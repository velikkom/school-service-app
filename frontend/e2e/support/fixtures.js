// @ts-check
// Sprint 3 — Spec'e özel, API ile hazırlanan izole veri (yalnız koşuya ait ADMIN ile, yalnız ssp_e2e).
const crypto = require("node:crypto");
const { adminApi } = require("./e2eEnv");

const todayIstanbul = () =>
  new Intl.DateTimeFormat("en-CA", { timeZone: "Europe/Istanbul", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());

function plate() {
  const letters = "ABCDEFGHJKLMNPRSTUVYZ";
  const pick = () => letters[crypto.randomInt(letters.length)];
  return `${String(crypto.randomInt(10, 81)).padStart(2, "0")} ${pick()}${pick()}${pick()} ${crypto.randomInt(100, 999)}`;
}

/** Öğrenci (bugünkü sabah servisine atanmış) + başlatılmış sefer + biniş kaydı. */
async function buildServiceFixture(label) {
  const api = await adminApi();
  const today = todayIstanbul();
  const tag = `${label}${crypto.randomBytes(2).toString("hex")}`.toUpperCase();
  const vehicle = await api("POST", "/api/vehicles", { plate: plate(), capacity: 14, model: "Fixture Minibüs" });
  const { user: driverUser } = await api("POST", "/api/users", {
    fullName: `Fixture Şoför ${tag}`,
    email: `s3e2e.driver.${tag.toLowerCase()}@example.test`,
    role: "DRIVER",
    password: `S3e2e-${crypto.randomBytes(6).toString("hex")}`,
  });
  await api("POST", `/api/users/${driverUser.id}/role`, { role: "DRIVER", licenseNo: `FX${tag}` });
  const driver = (await api("GET", "/api/drivers?size=100&sort=id,desc")).content.find((d) => d.userId === driverUser.id);
  const route = await api("POST", "/api/routes", { name: `S3 Fixture Güzergâh ${tag}`, direction: "MORNING" });
  const stop = await api("POST", `/api/routes/${route.id}/stops`, { name: `Fixture Durağı ${tag}`, sequenceNo: 1, plannedTime: "07:40" });
  const student = await api("POST", "/api/students", { fullName: `S3 Öğrenci ${tag}`, birthDate: "2014-02-03", grade: "6" });
  await api("POST", "/api/assignments", { studentId: student.id, routeId: route.id, stopId: stop.id, direction: "MORNING", validFrom: today });
  const trip = await api("POST", "/api/trips", { routeId: route.id, vehicleId: vehicle.id, driverId: driver.id, businessDate: today, direction: "MORNING", plannedStartTime: "07:30" });
  await api("POST", `/api/trips/${trip.id}/start`);
  await api("POST", `/api/trips/${trip.id}/attendance-events/boardings`, { studentId: student.id });
  return { api, tag, today, student, route, stop, trip, vehicle };
}

/** Koşuya özel, bağlı olmayan aktif STUDENT hesabı (şifresi bilinir). */
async function createStudentAccount(api, label) {
  const password = `S3e2e-${crypto.randomBytes(6).toString("hex")}`;
  const email = `s3e2e.student.${label.toLowerCase()}.${crypto.randomBytes(2).toString("hex")}@example.test`;
  const { user } = await api("POST", "/api/users", { fullName: `S3 Öğrenci Hesabı ${label}`, email, role: "STUDENT", password });
  return { id: user.id, email, password, fullName: user.fullName, role: "STUDENT" };
}

module.exports = { buildServiceFixture, createStudentAccount, todayIstanbul, plate };
