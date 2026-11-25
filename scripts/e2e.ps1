# Sprint 3 — Izole E2E paketi (tek komut). Depo kokunden:
#   powershell -ExecutionPolicy Bypass -File scripts\e2e.ps1            # 1 kosu
#   powershell -ExecutionPolicy Bypass -File scripts\e2e.ps1 -Runs 2    # arka arkaya 2 temiz kosu
#   ... -Grep "student-account"                                        # yalniz eslesen testler
#
# Her kosu: ssp-e2e compose projesi sifirdan (tmpfs PostgreSQL + Mailpit) -> backend jar'i 18080'de, yalniz bu
# veritabanina bagli -> hedef dogrulamasi (dogrulanamazsa cikis kodu 2) -> Playwright (Next.js 3100, .next-e2e)
# -> backend durdurulur, ssp-e2e kaynaklari kaldirilir. Gunluk gelistirme ortamina (ssp-postgres 5434,
# backend 8080, frontend 3000) baglanilmaz; gelistirme veritabanindan yalniz SALT-OKUNUR sayim alinir ve
# kosu oncesi/sonrasi karsilastirilir. Uretilen sirlar (DB/JWT/ADMIN) her kosuda rastgeledir ve yazdirilmaz.
param(
  [int]$Runs = 1,
  [string]$Grep = "",
  [switch]$SkipBuild
)
# Yerel komutlarin (docker, psql, npx) stderr ciktisi Windows PowerShell 5.1'de hata kaydina donusmesin diye
# "Continue"; hatalar cikis kodu ($LASTEXITCODE) ve acik throw ile ele alinir.
$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root "frontend"
$backend = Join-Path $root "backend"
$work = Join-Path $root ".e2e"
New-Item -ItemType Directory -Force $work | Out-Null

$DbPort = 55434; $MailSmtp = 11025; $MailUi = 18025; $BackendPort = 18080; $FrontPort = 3100
$DbName = "ssp_e2e"; $DbUser = "ssp_e2e"; $DbContainer = "ssp-e2e-postgres"
$composeFile = Join-Path $root "compose.e2e.yaml"

function Write-Step($m) { Write-Host "[e2e] $m" -ForegroundColor Cyan }
function New-Secret([int]$bytes) { $b = New-Object byte[] $bytes; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b) }
function Test-PortFree([int]$p) { -not (Get-NetTCPConnection -State Listen -LocalPort $p -ErrorAction SilentlyContinue) }
function Invoke-Psql([string]$container, [string]$user, [string]$db, [string]$sql) {
  $out = & docker exec $container psql -U $user -d $db -At -F "|" -c $sql 2>&1
  if ($LASTEXITCODE -ne 0) { throw "psql ($container/$db) basarisiz: $out" }
  return $out
}

# --- Gunluk gelistirme veritabani: yalniz okunur parmak izi (tablo bazinda satir sayisi) ---
$devEnv = @{}
$rootEnv = Join-Path $root ".env"
if (Test-Path $rootEnv) {
  Get-Content $rootEnv | ForEach-Object {
    $l = $_.Trim(); if ($l -and -not $l.StartsWith("#") -and $l.Contains("=")) { $i = $l.IndexOf("="); $devEnv[$l.Substring(0, $i).Trim()] = $l.Substring($i + 1).Trim().Trim('"') }
  }
}
$devUser = if ($devEnv["POSTGRES_USER"]) { $devEnv["POSTGRES_USER"] } else { "school_service_app" }
$devDb = if ($devEnv["POSTGRES_DB"]) { $devEnv["POSTGRES_DB"] } else { "school_service" }
$devRunning = (& docker ps --filter "name=^ssp-postgres$" --format "{{.Names}}") -eq "ssp-postgres"
$countSql = "select table_name || '=' || (xpath('/row/c/text()', query_to_xml(format('select count(*) as c from public.%I', table_name), false, true, '')))[1]::text from information_schema.tables where table_schema='public' and table_type='BASE TABLE' order by table_name"
function Get-DevFingerprint {
  if (-not $devRunning) { return $null }
  $h = [ordered]@{}
  foreach ($line in (Invoke-Psql "ssp-postgres" $devUser $devDb $countSql)) { $kv = $line -split "=", 2; $h[$kv[0]] = [long]$kv[1] }
  $h["__e2e_marked_users"] = [long](Invoke-Psql "ssp-postgres" $devUser $devDb "select count(*) from app_user where email like 's3e2e.%' or email like 'e2e-admin-%'")
  return $h
}

if (-not (Test-PortFree $BackendPort)) { throw "Port $BackendPort kullanimda; E2E backend'i baslatilamaz (yanlis sunucuya baglanmamak icin durduruldu)." }
if (-not (Test-PortFree $FrontPort)) { throw "Port $FrontPort kullanimda; E2E frontend'i baslatilamaz (yanlis sunucuya baglanmamak icin durduruldu)." }

# --- Backend jar (kopyadan calistirilir; calisan gelistirme sunucusuna dokunulmaz) ---
$jar = Join-Path $work "backend.jar"
if (-not $SkipBuild -or -not (Test-Path $jar)) {
  Write-Step "Backend paketleniyor (testler atlanir; backend testleri ayri calistirilir)..."
  Push-Location $backend
  try { & .\mvnw.cmd -o -q -DskipTests package; if ($LASTEXITCODE -ne 0) { throw "Backend paketlenemedi." } } finally { Pop-Location }
  Copy-Item (Join-Path $backend "target\school-service-backend.jar") $jar -Force -ErrorAction Stop
}

$devBefore = Get-DevFingerprint
$results = @()
$overallExit = 0

for ($run = 1; $run -le $Runs; $run++) {
  $runId = (Get-Date -Format "yyMMddHHmmss") + "-" + $run
  Write-Step "=== Kosu $run/$Runs (id $runId) ==="
  $env:E2E_DB_PASSWORD = New-Secret 24
  $env:E2E_DB_PORT = "$DbPort"; $env:E2E_MAIL_SMTP_PORT = "$MailSmtp"; $env:E2E_MAIL_UI_PORT = "$MailUi"
  $jwtSecret = New-Secret 48
  $adminEmail = "e2e-admin-$($runId.ToLower())@example.test"
  $adminPassword = "E2e-" + ((New-Secret 18) -replace "[^A-Za-z0-9]", "x")
  $backendProc = $null
  $runExit = 1
  $verification = $null
  try {
    Write-Step "ssp-e2e ortami sifirdan kuruluyor (yalniz bu projenin container'lari)..."
    & docker compose -p ssp-e2e -f $composeFile down -v --remove-orphans 2>&1 | Out-Null
    & docker compose -p ssp-e2e -f $composeFile up -d --wait 2>&1 | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "ssp-e2e compose ortami baslatilamadi." }

    # Backend: yalniz E2E degerleri (kok .env YUKLENMEZ). Sirlar sadece bu surecin ortaminda.
    $benv = @{
      SPRING_PROFILES_ACTIVE = "dev"; SERVER_PORT = "$BackendPort"
      DB_URL = "jdbc:postgresql://127.0.0.1:$DbPort/$DbName"; DB_USERNAME = $DbUser; DB_PASSWORD = $env:E2E_DB_PASSWORD
      JWT_SECRET = $jwtSecret; JWT_ACCESS_TOKEN_TTL_MINUTES = "15"; JWT_REFRESH_TOKEN_TTL_DAYS = "1"; JWT_REFRESH_TOKEN_TTL_DAYS_REMEMBER_ME = "30"
      BOOTSTRAP_ADMIN_EMAIL = $adminEmail; BOOTSTRAP_ADMIN_PASSWORD = $adminPassword; BOOTSTRAP_ADMIN_FULL_NAME = "E2E Yonetici"
      CORS_ALLOWED_ORIGINS = "http://localhost:$FrontPort"; SWAGGER_ENABLED = "false"
      MAIL_HOST = "127.0.0.1"; MAIL_PORT = "$MailSmtp"; MAIL_FROM = "no-reply@e2e.example.test"
      RESET_PASSWORD_BASE_URL = "http://localhost:$FrontPort/sifre-sifirla"
      REGISTRATION_VERIFICATION_BASE_URL = "http://localhost:$FrontPort/e-posta-dogrula"
      OAUTH_REDIRECT_BASE_URL = "http://localhost:$FrontPort"; INTERNAL_SERVICE_KEY = (New-Secret 32)
      GOOGLE_OAUTH_CLIENT_ID = ""; FACEBOOK_OAUTH_CLIENT_ID = ""; X_OAUTH_CLIENT_ID = ""
    }
    $saved = @{}
    foreach ($k in $benv.Keys) { $saved[$k] = [Environment]::GetEnvironmentVariable($k, "Process"); [Environment]::SetEnvironmentVariable($k, $benv[$k], "Process") }
    New-Item -ItemType Directory -Force "C:\tmp\jdk" | Out-Null
    $log = Join-Path $work "backend-run$run.log"
    Write-Step "E2E backend baslatiliyor (port $BackendPort, log .e2e\backend-run$run.log)..."
    $backendProc = Start-Process -FilePath "java" -ArgumentList @("-Djdk.net.unixdomain.tmpdir=C:/tmp/jdk", "-jar", $jar) -RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru -WindowStyle Hidden -ErrorAction Stop
    foreach ($k in $benv.Keys) { [Environment]::SetEnvironmentVariable($k, $saved[$k], "Process") }

    $deadline = (Get-Date).AddSeconds(240); $up = $false
    while ((Get-Date) -lt $deadline) {
      if ($backendProc.HasExited) { throw "E2E backend erken kapandi (bkz. $log)." }
      try { $h = Invoke-RestMethod "http://127.0.0.1:$BackendPort/actuator/health" -TimeoutSec 3; if ($h.status -eq "UP") { $up = $true; break } } catch { }
      Start-Sleep -Seconds 2
    }
    if (-not $up) { throw "E2E backend $BackendPort uzerinde ayaga kalkmadi." }

    # --- Hedef dogrulamasi: yazma testlerinden ONCE. Basarisizsa kosu calistirilmaz, cikis kodu 2. ---
    Write-Step "Hedef dogrulamasi: backend gercekten $DbName veritabanina mi bagli?"
    $e2eDbName = (Invoke-Psql $DbContainer $DbUser $DbName "select current_database()") | Select-Object -First 1
    $adminInE2e = [int]((Invoke-Psql $DbContainer $DbUser $DbName "select count(*) from app_user where lower(email)=lower('$adminEmail')") | Select-Object -First 1)
    $adminInDev = if ($devRunning) { [int]((Invoke-Psql "ssp-postgres" $devUser $devDb "select count(*) from app_user where lower(email)=lower('$adminEmail')") | Select-Object -First 1) } else { 0 }
    $loginOk = $false
    try {
      $lr = Invoke-RestMethod -Method Post "http://127.0.0.1:$BackendPort/api/auth/login" -ContentType "application/json" -Body (@{ email = $adminEmail; password = $adminPassword } | ConvertTo-Json)
      $loginOk = [bool]$lr.accessToken
    } catch { $loginOk = $false }
    $flyway = (Invoke-Psql $DbContainer $DbUser $DbName "select max(version::int) from flyway_schema_history where success") | Select-Object -First 1
    $verification = [ordered]@{ database = $e2eDbName; target = "127.0.0.1:$DbPort (container $DbContainer)"; flywayVersion = $flyway; runAdminInE2eDb = $adminInE2e; runAdminInDevDb = $adminInDev; backendLoginWithRunAdmin = $loginOk }
    Write-Step ("Dogrulama: " + ($verification | ConvertTo-Json -Compress))
    if ($e2eDbName -ne $DbName -or $adminInE2e -ne 1 -or $adminInDev -ne 0 -or -not $loginOk) {
      $overallExit = 2
      throw "HEDEF DOGRULANAMADI: backend'in $DbName veritabanina bagli oldugu kanitlanamadi. Testler calistirilmadi."
    }

    # --- Playwright (Next.js dev sunucusu 3100, ayri .next-e2e klasoru, BFF -> 18080) ---
    $penv = @{
      E2E_MODE = "isolated"; E2E_RUN_ID = $runId
      E2E_BASE_URL = "http://localhost:$FrontPort"; E2E_FRONTEND_PORT = "$FrontPort"
      E2E_BACKEND_URL = "http://127.0.0.1:$BackendPort"; E2E_MAILPIT_URL = "http://127.0.0.1:$MailUi"
      E2E_ADMIN_EMAIL = $adminEmail; E2E_ADMIN_PASSWORD = $adminPassword; E2E_JWT_SECRET = $jwtSecret
      E2E_DB_CONTAINER = $DbContainer; E2E_DB_NAME = $DbName; E2E_DB_USER = $DbUser
      BACKEND_INTERNAL_URL = "http://127.0.0.1:$BackendPort"; BFF_COOKIE_SECRET = (New-Secret 32)
      JWT_ACCESS_TOKEN_TTL_MINUTES = "15"; JWT_REFRESH_TOKEN_TTL_DAYS = "1"; NEXT_DIST_DIR = ".next-e2e"
      OAUTH_REDIRECT_BASE_URL = "http://localhost:$FrontPort"; INTERNAL_SERVICE_KEY = ""
      GOOGLE_OAUTH_CLIENT_ID = ""; FACEBOOK_OAUTH_CLIENT_ID = ""; X_OAUTH_CLIENT_ID = ""
    }
    $savedP = @{}
    foreach ($k in $penv.Keys) { $savedP[$k] = [Environment]::GetEnvironmentVariable($k, "Process"); [Environment]::SetEnvironmentVariable($k, $penv[$k], "Process") }
    Push-Location $frontend
    try {
      $pwArgs = @("playwright", "test", "--trace", "off", "--reporter=list,json")
      if ($Grep) { $pwArgs += @("--grep", $Grep) }
      $env:PLAYWRIGHT_JSON_OUTPUT_NAME = (Join-Path $work "playwright-run$run.json")
      Write-Step "Playwright calisiyor..."
      & npx @pwArgs
      $runExit = $LASTEXITCODE
    } finally {
      Pop-Location
      foreach ($k in $penv.Keys) { [Environment]::SetEnvironmentVariable($k, $savedP[$k], "Process") }
      Remove-Item Env:PLAYWRIGHT_JSON_OUTPUT_NAME -ErrorAction SilentlyContinue
    }
  } catch {
    Write-Host "[e2e] HATA: $($_.Exception.Message)" -ForegroundColor Red
    if ($overallExit -eq 0) { $overallExit = 1 }
  } finally {
    if ($backendProc -and -not $backendProc.HasExited) { & taskkill /PID $backendProc.Id /T /F 2>&1 | Out-Null }
    Write-Step "ssp-e2e kaynaklari kaldiriliyor (yalniz bu kosunun container'lari ve tmpfs verisi)..."
    & docker compose -p ssp-e2e -f $composeFile down -v --remove-orphans 2>&1 | Out-Null
    Remove-Item Env:E2E_DB_PASSWORD -ErrorAction SilentlyContinue
  }
  $summary = $null
  $jsonFile = Join-Path $work "playwright-run$run.json"
  if (Test-Path $jsonFile) {
    $j = Get-Content $jsonFile -Raw | ConvertFrom-Json
    $summary = [ordered]@{ expected = $j.stats.expected; unexpected = $j.stats.unexpected; flaky = $j.stats.flaky; skipped = $j.stats.skipped; durationMs = [int]$j.stats.duration }
  }
  $results += [ordered]@{ run = $run; runId = $runId; exitCode = $runExit; verification = $verification; playwright = $summary }
  if ($runExit -ne 0 -and $overallExit -eq 0) { $overallExit = 1 }
  if ($overallExit -eq 2) { break }
}

$devAfter = Get-DevFingerprint
$devDiff = @()
if ($devBefore -and $devAfter) {
  foreach ($k in $devBefore.Keys) { if ($devBefore[$k] -ne $devAfter[$k]) { $devDiff += "$k $($devBefore[$k])->$($devAfter[$k])" } }
}
$report = [ordered]@{
  finishedAt = (Get-Date).ToString("s"); runs = $results
  devDatabase = [ordered]@{ checked = [bool]$devBefore; container = "ssp-postgres"; database = $devDb; tables = if ($devBefore) { $devBefore.Count - 1 } else { 0 }; e2eMarkedUsers = if ($devAfter) { $devAfter["__e2e_marked_users"] } else { $null }; changedTables = $devDiff }
}
$report | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $work "last-report.json") -Encoding UTF8
Write-Step "Rapor: .e2e\last-report.json"
Write-Host ($report | ConvertTo-Json -Depth 6)
if ($devDiff.Count -gt 0) { Write-Host "[e2e] UYARI: gelistirme veritabaninda satir sayisi degisen tablolar var (calisan gunluk backend kaynakli olabilir): $($devDiff -join ', ')" -ForegroundColor Yellow }
exit $overallExit


