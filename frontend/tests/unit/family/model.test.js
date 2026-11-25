import { describe, expect, it } from "vitest";
import { deriveEnrollmentState } from "@/features/enrollment/hooks/enrollmentState";
import { makeStopChangeSchema } from "@/features/stop-change-requests/schemas/stopChangeSchema";
import { groupByTrip, reviewTargetOptions, startOfDayInstant, statusFromEvents } from "@/features/attendance/hooks/attendanceModel";
import { noticeActionsFor, pickHero } from "@/features/daily-service/hooks/dailyServiceModel";
import { enrollmentSchema, emptyEnrollment, stepOfField, toEnrollmentPayload } from "@/features/enrollment/schemas/enrollmentSchema";

describe("deriveEnrollmentState", () => {
  it("öğrenci yoksa sihirbaz", () => {
    expect(deriveEnrollmentState([], {}).state).toBe("WIZARD");
  });
  it("en az bir ACTIVE sözleşme varsa açık (bekleyen ikinci başvuru kilitlemez)", () => {
    const r = deriveEnrollmentState([{ id: 1 }, { id: 2 }], { 1: [{ status: "PENDING_APPROVAL" }], 2: [{ status: "ACTIVE" }] });
    expect(r.state).toBe("OPEN");
  });
  it("aktif yok, bekleyen var → PENDING ve bekleyen öğrenci döner", () => {
    const r = deriveEnrollmentState([{ id: 1, fullName: "Ece" }], { 1: [{ status: "PENDING_APPROVAL" }] });
    expect(r).toEqual({ state: "PENDING", pendingStudent: { id: 1, fullName: "Ece" } });
  });
  it("hepsi iptal (pasif öğrenci veya tüm sözleşmeler CANCELLED) → sihirbaz", () => {
    const r = deriveEnrollmentState([{ id: 1, isActive: false }, { id: 2 }], { 1: [], 2: [{ status: "CANCELLED" }] });
    expect(r.state).toBe("WIZARD");
  });
  it("sözleşmesi olmayan aktif öğrenci (yönetici ekledi) kilitlenmez", () => {
    expect(deriveEnrollmentState([{ id: 1 }], { 1: [] }).state).toBe("OPEN");
  });
});

describe("durak talebi şeması", () => {
  const schema = makeStopChangeSchema("2026-11-19");
  const base = { studentId: 1, direction: "MORNING", requestedStopId: 5, effectiveDate: "2026-11-19", reason: "" };
  it("bugünü kabul eder", () => {
    expect(schema.safeParse(base).success).toBe(true);
  });
  it("geçmiş tarihi reddeder", () => {
    const r = schema.safeParse({ ...base, effectiveDate: "2026-11-18" });
    expect(r.success).toBe(false);
    expect(r.error.issues[0].message).toBe("Tarih bugünden önce olamaz.");
  });
  it("öğrenci, yön ve durak zorunlu", () => {
    const r = schema.safeParse({ ...base, studentId: null, direction: null, requestedStopId: null });
    expect(r.success).toBe(false);
    expect(r.error.issues.map((i) => i.path[0]).sort()).toEqual(["direction", "requestedStopId", "studentId"]);
  });
});

describe("yoklama modeli", () => {
  const events = [
    { id: 1, tripId: 10, eventType: "BOARDING", recordedAt: "2026-11-18T04:30:00Z" },
    { id: 2, tripId: 10, eventType: "DROPOFF", recordedAt: "2026-11-18T05:10:00Z" },
    { id: 3, tripId: 11, eventType: "ABSENCE", recordedAt: "2026-11-18T13:00:00Z" },
  ];
  it("sefer bazında gruplar, en yeni önce, resmi durumu türetir", () => {
    const rows = groupByTrip(events);
    expect(rows.map((r) => [r.tripId, r.status])).toEqual([
      [11, "ABSENT"],
      [10, "DROPPED_OFF"],
    ]);
    expect(statusFromEvents([])).toBe("NONE");
  });
  it("hedef durum seçenekleri mevcut durumu içermez (aynısı 400)", () => {
    expect(reviewTargetOptions("BOARDED")).not.toContain("BOARDED");
  });
  it("tarih Istanbul gün başlangıcına çevrilir", () => {
    expect(startOfDayInstant("2026-11-19")).toBe("2026-11-18T21:00:00.000Z");
  });
});

describe("günlük servis modeli", () => {
  it("hero: yoldaki > planlı > son", () => {
    const e = [{ status: "COMPLETED" }, { status: "PLANNED" }];
    expect(pickHero(e)).toBe(e[1]);
    expect(pickHero([{ status: "COMPLETED" }, { status: "IN_PROGRESS" }]).status).toBe("IN_PROGRESS");
  });
  it("STUDENT için bayraklar true gelse bile aksiyon yok", () => {
    const entry = { status: "PLANNED", attendanceStatus: "NONE", canSubmitAbsenceNotice: true };
    expect(noticeActionsFor(entry, "STUDENT").canSubmit).toBe(false);
    expect(noticeActionsFor(entry, "PARENT").canSubmit).toBe(true);
  });
  it("yoklama girilmiş açık seferde kilit notu", () => {
    const entry = { status: "IN_PROGRESS", attendanceStatus: "BOARDED", canSubmitAbsenceNotice: false, canWithdrawAbsenceNotice: false };
    expect(noticeActionsFor(entry, "PARENT").lockedByAttendance).toBe(true);
  });
});

describe("öz-kayıt şeması", () => {
  const valid = {
    ...emptyEnrollment(),
    schoolId: "3",
    relationType: "Anne",
    parentPhone: "0532 400 00 31",
    student: { fullName: "Ece Korkmaz", birthDate: "2019-03-02", tcNo: "12345678901", grade: "1-A", gender: "FEMALE", healthNote: "" },
    address: { city: "Denizli", district: "Merkezefendi", neighborhood: "Bağlarbaşı", addressLine: "Örnek Sk. No:1" },
    contractAccepted: true,
  };
  it("geçerli formu kabul eder ve gövdeyi üretir", () => {
    expect(enrollmentSchema.safeParse(valid).success).toBe(true);
    const p = toEnrollmentPayload(valid);
    expect(p.schoolId).toBe(3);
    expect(p.student.healthNote).toBeUndefined();
  });
  it("10 haneli T.C. ve onaysız sözleşmeyi reddeder", () => {
    const r = enrollmentSchema.safeParse({ ...valid, contractAccepted: false, student: { ...valid.student, tcNo: "123" } });
    const paths = r.error.issues.map((i) => i.path.join("."));
    expect(paths).toEqual(expect.arrayContaining(["student.tcNo", "contractAccepted"]));
  });
  it("sunucu alanı doğru adıma eşlenir", () => {
    expect(stepOfField("student.tcNo")).toBe(1);
    expect(stepOfField("address.city")).toBe(2);
  });
});
