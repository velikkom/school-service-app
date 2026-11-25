// @ts-check
const { execFileSync } = require("node:child_process");
const path = require("node:path");
const { assertIsolatedTarget } = require("./support/e2eEnv");

/**
 * Sprint 3: önce hedefin izole E2E veritabanı olduğu doğrulanır (doğrulanamazsa koşu hata ile biter), sonra
 * bu koşuya ait önekli test verisi API ile oluşturulur. Seferleri iptal eden cancellation.spec.js kendi
 * seed'ini ayrıca üretir.
 */
module.exports = async function globalSetup() {
  const target = await assertIsolatedTarget();
  console.log(`[e2e] Hedef doğrulandı: veritabanı ${target.database}, backend ${target.backend}, frontend ${target.frontend}`);
  execFileSync(process.execPath, [path.join(__dirname, "support", "seed.mjs")], { stdio: "inherit" });
};
