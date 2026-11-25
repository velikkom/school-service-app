# Yerel backend'i kök .env değerleriyle başlatır (değerleri ekrana yazmaz).
# Kullanım (depo kökünden):  powershell -ExecutionPolicy Bypass -File scripts\start-backend.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root ".env"
if (-not (Test-Path $envFile)) { throw ".env bulunamadı: $envFile (önce .env.example'dan kopyalayın)" }

Get-Content $envFile | ForEach-Object {
  $line = $_.Trim()
  if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
    $i = $line.IndexOf("=")
    $name = $line.Substring(0, $i).Trim()
    $value = $line.Substring($i + 1).Trim().Trim('"')
    [Environment]::SetEnvironmentVariable($name, $value, "Process")
  }
}

Set-Location (Join-Path $root "backend")
# Windows'ta uzun TEMP yolu NIO loopback (unix domain socket) bağlantısını bozabiliyor; kısa dizin kullanılır.
$udsDir = "C:\tmp\jdk"
New-Item -ItemType Directory -Force $udsDir | Out-Null
& .\mvnw.cmd -q spring-boot:run "-Dspring-boot.run.jvmArguments=-Djdk.net.unixdomain.tmpdir=C:/tmp/jdk"
exit $LASTEXITCODE
