// İzole E2E test verisi: gerçek backend API'si (koşuya özel ADMIN) ile önekli kayıtlar oluşturur. Yalnız
// scripts/e2e.ps1'in kurduğu tek kullanımlık ssp_e2e veritabanında çalışır (bkz. support/e2eEnv.js); çıktı
// (kimlikler + test şifreleri) e2e/.seed.json'a yazılır (git'e eklenmez). STUDENT kullanıcısı öğrenci kaydına
// Sprint 3'te eklenen PUT /api/students/{id}/account ucuyla bağlanır (veritabanına elle yazılmaz).
//
// Kullanım: scripts\e2e.ps1 global setup'ta otomatik çağırır.
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import { createRequire } from "node:module";
import { fileURLToPath } from "node:url";

const require = createRequire(import.meta.url);
const { e2eEnv, adminApi } = require("./e2eEnv.js");

const here = path.dirname(fileURLToPath(import.meta.url));
e2eEnv(); // izole ortam değilse burada durur
const today = new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
const tag = `${today.replaceAll("-", "").slice(2)}${crypto.randomBytes(2).toString("hex")}`.toUpperCase();
const pw = () => `S3e2e-${crypto.randomBytes(6).toString("hex")}`;

let api;

async function main() {
  api = await adminApi();

  const school = await api("POST", "/api/schools", { name: `S3 Test Okulu ${tag}`, address: "Test Mah. 1" });
  const n = Number.parseInt(tag.slice(-3), 16) % 900 + 100;
  const vehicle = await api("POST", "/api/vehicles", { plate: `34 SE ${n}`, capacity: 16, model: "Test Minibüs" });

  const mkUser = async (role, fullName) => {
    const password = pw();
    const email = `s3e2e.${role.toLowerCase()}.${tag.toLowerCase()}@example.test`;
    const { user } = await api("POST", "/api/users", { fullName, email, role, password, phone: "05550000000" });
    return { id: user.id, email, password, fullName, role };
  };
  const driverUser = await mkUser("DRIVER", `Test Şoför ${tag}`);
  const hostessUser = await mkUser("HOSTESS", `Test Hostes ${tag}`);
  const parentUser = await mkUser("PARENT", `Test Veli ${tag}`);
  const studentUser = await mkUser("STUDENT", `Test Öğrenci Ayşe ${tag}`);
  const adminUser = await mkUser("ADMIN", `Test Yönetici ${tag}`);

  const driver = await api("POST", "/api/users/" + driverUser.id + "/role", { role: "DRIVER", licenseNo: `E2E${tag}` }).then(() => api("GET", "/api/drivers?size=100&sort=id,desc")).then((p) => p.content.find((d) => d.userId === driverUser.id));
  const hostess = await api("POST", "/api/users/" + hostessUser.id + "/role", { role: "HOSTESS" }).then(() => api("GET", "/api/hostesses?size=100&sort=id,desc")).then((p) => p.content.find((h) => h.userId === hostessUser.id));
  const parent = await api("POST", "/api/users/" + parentUser.id + "/role", { role: "PARENT" }).then(() => api("GET", `/api/parents?size=100&sort=id,desc`)).then((p) => p.content.find((x) => x.userId === parentUser.id));

  const studentA = await api("POST", "/api/students", { fullName: `Test Öğrenci Ayşe ${tag}`, birthDate: "2016-05-10", schoolId: school.id, grade: "4" });
  const studentB = await api("POST", "/api/students", { fullName: `Test Öğrenci Mert ${tag}`, birthDate: "2018-09-02", schoolId: school.id, grade: "2" });
  for (const s of [studentA, studentB]) {
    await api("POST", "/api/parent-student-links", { parentId: parent.id, studentId: s.id, relationType: "MOTHER" }).catch(async () =>
      api("POST", "/api/parent-student-links", { parentId: parent.id, studentId: s.id, relationType: "PARENT" })
    );
    await api("POST", "/api/contracts", { studentId: s.id, monthlyFee: "1500.00", startDate: today });
  }

  const morning = await api("POST", "/api/routes", { name: `S3 Sabah ${tag}`, direction: "MORNING" });
  const afternoon = await api("POST", "/api/routes", { name: `S3 Akşam ${tag}`, direction: "AFTERNOON" });
  const mStops = [
    await api("POST", `/api/routes/${morning.id}/stops`, { name: "Çınar Durağı", sequenceNo: 1, plannedTime: "07:20" }),
    await api("POST", `/api/routes/${morning.id}/stops`, { name: "Park Durağı", sequenceNo: 2, plannedTime: "07:35" }),
  ];
  const aStops = [
    await api("POST", `/api/routes/${afternoon.id}/stops`, { name: "Okul Önü", sequenceNo: 1, plannedTime: "16:10" }),
    await api("POST", `/api/routes/${afternoon.id}/stops`, { name: "Çınar Durağı", sequenceNo: 2, plannedTime: "16:30" }),
  ];
  await api("POST", "/api/assignments", { studentId: studentA.id, routeId: morning.id, stopId: mStops[0].id, direction: "MORNING", validFrom: today });
  await api("POST", "/api/assignments", { studentId: studentB.id, routeId: morning.id, stopId: mStops[1].id, direction: "MORNING", validFrom: today });
  await api("POST", "/api/assignments", { studentId: studentA.id, routeId: afternoon.id, stopId: aStops[1].id, direction: "AFTERNOON", validFrom: today });
  await api("POST", "/api/assignments", { studentId: studentB.id, routeId: afternoon.id, stopId: aStops[1].id, direction: "AFTERNOON", validFrom: today });

  const tripMorning = await api("POST", "/api/trips", { routeId: morning.id, vehicleId: vehicle.id, driverId: driver.id, hostessId: hostess.id, businessDate: today, direction: "MORNING", plannedStartTime: "07:10" });
  const tripAfternoon = await api("POST", "/api/trips", { routeId: afternoon.id, vehicleId: vehicle.id, driverId: driver.id, hostessId: hostess.id, businessDate: today, direction: "AFTERNOON", plannedStartTime: "16:00" });

  // STUDENT kullanıcısı ↔ öğrenci kaydı (Sprint 3 API'si).
  await api("PUT", `/api/students/${studentA.id}/account`, { userId: studentUser.id });

  const out = {
    tag,
    today,
    users: { admin: adminUser, driver: driverUser, hostess: hostessUser, parent: parentUser, student: studentUser },
    ids: { schoolId: school.id, vehicleId: vehicle.id, driverId: driver.id, hostessId: hostess.id, parentId: parent.id, studentA: studentA.id, studentB: studentB.id, morningRoute: morning.id, afternoonRoute: afternoon.id, tripMorning: tripMorning.id, tripAfternoon: tripAfternoon.id },
    names: { studentA: studentA.fullName, studentB: studentB.fullName, plate: vehicle.plate },
  };
  fs.writeFileSync(process.env.SEED_OUT || path.join(here, "..", ".seed.json"), JSON.stringify(out, null, 2));
  console.log(`Seed hazır (etiket ${tag}): seferler ${tripMorning.id} (sabah), ${tripAfternoon.id} (akşam). Kimlikler e2e/.seed.json içinde.`);
}

main().catch((e) => {
  console.error("Seed başarısız:", e.message);
  process.exit(1);
});
