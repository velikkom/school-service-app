// @ts-check
// Sprint 3 — İzole E2E ortamı yardımcıları. Tüm değerler scripts/e2e.ps1'in ürettiği ortam değişkenlerinden
// okunur (her koşuda rastgele sırlar; hiçbiri loglanmaz). Günlük geliştirme ortamına (3000/8080/ssp-postgres)
// bağlanmayı reddeder.
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");

const REQUIRED = [
  "E2E_MODE",
  "E2E_BASE_URL",
  "E2E_BACKEND_URL",
  "E2E_MAILPIT_URL",
  "E2E_ADMIN_EMAIL",
  "E2E_ADMIN_PASSWORD",
  "E2E_JWT_SECRET",
  "E2E_DB_CONTAINER",
  "E2E_DB_NAME",
  "E2E_DB_USER",
];

function e2eEnv() {
  const missing = REQUIRED.filter((k) => !process.env[k]);
  if (missing.length || process.env.E2E_MODE !== "isolated") {
    throw new Error(
      `E2E yalnız izole ortamda çalışır. Depo kökünden çalıştırın: powershell -ExecutionPolicy Bypass -File scripts\\e2e.ps1 (eksik: ${missing.join(", ") || "E2E_MODE=isolated"})`
    );
  }
  const env = {
    baseUrl: String(process.env.E2E_BASE_URL),
    backendUrl: String(process.env.E2E_BACKEND_URL),
    mailpitUrl: String(process.env.E2E_MAILPIT_URL),
    admin: { email: String(process.env.E2E_ADMIN_EMAIL), password: String(process.env.E2E_ADMIN_PASSWORD) },
    jwtSecret: String(process.env.E2E_JWT_SECRET),
    db: { container: String(process.env.E2E_DB_CONTAINER), name: String(process.env.E2E_DB_NAME), user: String(process.env.E2E_DB_USER) },
    runId: process.env.E2E_RUN_ID || "manual",
  };
  const port = (u) => new URL(u).port;
  if (port(env.baseUrl) === "3000" || port(env.backendUrl) === "8080" || env.db.container === "ssp-postgres" || env.db.name === "school_service") {
    throw new Error("E2E hedefi günlük geliştirme ortamını gösteriyor; reddedildi.");
  }
  return env;
}

/** E2E veritabanında salt-okunur sorgu (yalnız doğrulama/assert amaçlı; kurulum için kullanılmaz). */
function e2eQuery(sql) {
  const { db } = e2eEnv();
  return execFileSync("docker", ["exec", db.container, "psql", "-U", db.user, "-d", db.name, "-At", "-F", "|", "-c", sql], { encoding: "utf8" }).trim();
}

/**
 * Yazma testlerinden önce: backend'in gerçekten E2E veritabanına bağlı olduğunu kanıtlar. Koşuya özel ADMIN
 * yalnız E2E veritabanında vardır ve backend o hesapla giriş yapabiliyorsa backend o veritabanına bağlıdır.
 */
async function assertIsolatedTarget() {
  const env = e2eEnv();
  const dbName = e2eQuery("select current_database()");
  const adminRows = e2eQuery(`select count(*) from app_user where lower(email) = lower('${env.admin.email.replace(/'/g, "''")}')`);
  const res = await fetch(`${env.backendUrl}/api/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(env.admin),
  });
  if (dbName !== env.db.name || adminRows !== "1" || !res.ok) {
    throw new Error(`İzole hedef doğrulanamadı (veritabanı=${dbName}, koşu ADMIN satırı=${adminRows}, backend girişi=${res.status}). Testler durduruldu.`);
  }
  return { database: dbName, backend: env.backendUrl, frontend: env.baseUrl };
}

/** Backend'e doğrudan (BFF'siz) ADMIN çağrıları — yalnız test verisi hazırlığı için. */
async function adminApi() {
  const env = e2eEnv();
  const login = await fetch(`${env.backendUrl}/api/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(env.admin) });
  if (!login.ok) throw new Error(`ADMIN girişi başarısız: ${login.status}`);
  const { accessToken } = await login.json();
  return async function api(method, p, body) {
    const res = await fetch(`${env.backendUrl}${p}`, {
      method,
      headers: { "Content-Type": "application/json", Authorization: `Bearer ${accessToken}` },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new Error(`${method} ${p} → ${res.status} ${data?.message ?? ""}`);
    return data;
  };
}

/** Koşunun JWT sırrıyla, süresi GEÇMİŞ ama imzası geçerli bir access token üretir (süre dolumu simülasyonu). */
function mintExpiredAccessToken({ userId, email, role }) {
  const { jwtSecret } = e2eEnv();
  const b64url = (o) => Buffer.from(JSON.stringify(o)).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const head = b64url({ alg: "HS256" });
  const body = b64url({ sub: String(userId), email, role, iat: now - 3600, exp: now - 60 });
  const sig = crypto.createHmac("sha256", Buffer.from(jwtSecret, "base64")).update(`${head}.${body}`).digest("base64url");
  return `${head}.${body}.${sig}`;
}

/** Mailpit (yalnız E2E örneği): alıcıya giden en yeni mesajı bekler ve metnini döner. */
async function waitForMail(to, { subject, timeoutMs = 30_000, after = 0 } = {}) {
  const { mailpitUrl } = e2eEnv();
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const res = await fetch(`${mailpitUrl}/api/v1/search?query=${encodeURIComponent(`to:"${to}"`)}&limit=50`);
    if (res.ok) {
      const data = await res.json();
      const msg = (data.messages || [])
        .filter((m) => new Date(m.Created).getTime() >= after)
        .filter((m) => !subject || String(m.Subject).includes(subject))
        .sort((a, b) => new Date(b.Created).getTime() - new Date(a.Created).getTime())[0];
      if (msg) {
        const full = await (await fetch(`${mailpitUrl}/api/v1/message/${msg.ID}`)).json();
        return { id: msg.ID, subject: full.Subject, text: full.Text || "", html: full.HTML || "", to: (full.To || []).map((t) => t.Address), created: msg.Created };
      }
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Mailpit: ${to} için posta gelmedi${subject ? ` (${subject})` : ""}.`);
}

/** Mesaj gövdesinden verilen yolu içeren ilk bağlantıyı çıkarır. */
function extractLink(mail, pathPart) {
  const all = `${mail.text}\n${mail.html}`;
  const m = all.match(new RegExp(`https?://[^\\s"'<>]*${pathPart.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}[^\\s"'<>]*`));
  if (!m) throw new Error(`Postada ${pathPart} bağlantısı yok.`);
  return m[0].replace(/&amp;/g, "&");
}

/** Mailpit'teki tüm mesajların alıcıları (dış adrese posta çıkmadığını göstermek için). */
async function allMailRecipients() {
  const { mailpitUrl } = e2eEnv();
  const data = await (await fetch(`${mailpitUrl}/api/v1/messages?limit=2000`)).json();
  return (data.messages || []).flatMap((m) => (m.To || []).map((t) => t.Address));
}

/**
 * Süre dolumu simülasyonu (yalnız izole ssp_e2e): verilen tek kullanımlık bağlantı token'ının son kullanma
 * zamanını geçmişe çeker. Bağlantı süresi (saatler) beklenmeden "süresi dolmuş bağlantı" davranışını sınamak
 * için tek yazma istisnasıdır; yalnız iki token tablosuna ve yalnız o token'ın hash'ine dokunur.
 */
function expireTokenInE2eDb(table, rawToken) {
  if (!["password_reset_token", "registration_verification_token"].includes(table)) throw new Error("İzin verilmeyen tablo");
  const hash = crypto.createHash("sha256").update(rawToken, "utf8").digest("base64");
  const out = e2eQuery(`update ${table} set expires_at = now() - interval '1 minute' where token_hash = '${hash}' and used_at is null returning id`);
  if (!out) throw new Error(`${table}: token bulunamadı`);
}

module.exports = { e2eEnv, e2eQuery, expireTokenInE2eDb, assertIsolatedTarget, adminApi, mintExpiredAccessToken, waitForMail, extractLink, allMailRecipients };
