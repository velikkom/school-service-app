import { describe, expect, it } from "vitest";
import {
  attendanceActions,
  buildTripPatch,
  canRequestCorrection,
  correctionTargets,
  groupRosterByStop,
  nextStopId,
  pickActiveTrip,
  rosterCounts,
  sortTrips,
  staffOptions,
  stopProgress,
} from "@/features/trips/lib/fieldUtils";

const row = (studentId, stopId, sequenceNo, status = "NONE", extra = {}) => ({
  studentId,
  studentFullName: `Öğrenci ${studentId}`,
  stopId,
  stopName: `Durak ${stopId}`,
  sequenceNo,
  stopPlannedTime: "07:00:00",
  status,
  absenceNotice: null,
  pendingCorrectionId: null,
  ...extra,
});

describe("sortTrips / pickActiveTrip", () => {
  it("API'nin AFTERNOON<MORNING sırasını sabah önce olacak şekilde düzeltir", () => {
    const trips = [
      { id: 2, businessDate: "2026-11-19", direction: "AFTERNOON", plannedStartTime: "16:00:00" },
      { id: 1, businessDate: "2026-11-19", direction: "MORNING", plannedStartTime: "07:00:00" },
      { id: 3, businessDate: "2026-11-18", direction: "AFTERNOON" },
    ];
    expect(sortTrips(trips).map((t) => t.id)).toEqual([3, 1, 2]);
  });

  it("önce yoldaki, yoksa ilk planlı seferi seçer; yoksa null", () => {
    const base = { businessDate: "2026-11-19" };
    expect(pickActiveTrip([{ ...base, id: 1, direction: "MORNING", status: "PLANNED" }, { ...base, id: 2, direction: "AFTERNOON", status: "IN_PROGRESS" }]).id).toBe(2);
    expect(pickActiveTrip([{ ...base, id: 5, direction: "AFTERNOON", status: "PLANNED" }, { ...base, id: 4, direction: "MORNING", status: "COMPLETED" }]).id).toBe(5);
    expect(pickActiveTrip([{ ...base, id: 4, direction: "MORNING", status: "CANCELLED" }])).toBeNull();
  });
});

describe("roster gruplama", () => {
  const rows = [row(1, 20, 2), row(2, 10, 1, "BOARDED"), row(3, 20, 2, "NONE", { absenceNotice: { id: 9 } }), row(4, 30, 3)];

  it("durak sırasına göre gruplar, grup içi sırayı korur", () => {
    const g = groupRosterByStop(rows);
    expect(g.map((x) => x.stopId)).toEqual([10, 20, 30]);
    expect(g[1].rows.map((r) => r.studentId)).toEqual([1, 3]);
  });

  it("sayaçlar ve sıradaki durak (veli bildirimli satır bekleyen sayılmaz)", () => {
    const c = rosterCounts(rows);
    expect(c).toMatchObject({ NONE: 3, BOARDED: 1, total: 4, notices: 1 });
    expect(nextStopId(groupRosterByStop(rows))).toBe(20);
    expect(nextStopId(groupRosterByStop([row(2, 10, 1, "BOARDED"), row(3, 20, 2, "NONE", { absenceNotice: { id: 9 } })]))).toBeNull();
  });

  it("durak ilerlemesi yoklamadan türetilir", () => {
    const stops = [{ id: 30, sequenceNo: 3 }, { id: 10, sequenceNo: 1 }, { id: 20, sequenceNo: 2 }];
    const p = stopProgress(stops, groupRosterByStop(rows), "IN_PROGRESS");
    expect(p.map((s) => [s.id, s.state, s.count])).toEqual([[10, "done", 1], [20, "current", 2], [30, "todo", 1]]);
    expect(stopProgress(stops, [], "COMPLETED").every((s) => s.state === "done")).toBe(true);
  });
});

describe("yoklama aksiyonları ve düzeltme", () => {
  it("butonlar yalnız yoldaki seferde; veli bildirimi Bindi'yi engellemez", () => {
    expect(attendanceActions(row(1, 1, 1), "PLANNED")).toEqual([]);
    expect(attendanceActions(row(1, 1, 1, "NONE", { absenceNotice: { id: 1 } }), "IN_PROGRESS")).toEqual(["BOARDING", "ABSENCE"]);
    expect(attendanceActions(row(1, 1, 1, "BOARDED"), "IN_PROGRESS")).toEqual(["DROPOFF"]);
    expect(attendanceActions(row(1, 1, 1, "ABSENT"), "IN_PROGRESS")).toEqual([]);
  });

  it("düzeltme talebi: kayıtlı ve bekleyen talebi olmayan satır; hedef mevcut durum hariç", () => {
    expect(canRequestCorrection(row(1, 1, 1, "NONE"))).toBe(false);
    expect(canRequestCorrection(row(1, 1, 1, "BOARDED"))).toBe(true);
    expect(canRequestCorrection(row(1, 1, 1, "BOARDED", { pendingCorrectionId: 7 }))).toBe(false);
    expect(correctionTargets("ABSENT")).toEqual(["NONE", "BOARDED", "DROPPED_OFF"]);
  });
});

describe("ADMIN form yardımcıları", () => {
  it("şoför profilini kullanıcı adıyla eşler", () => {
    expect(staffOptions([{ id: 5, userId: 50 }, { id: 6, userId: 60 }], [{ id: 60, fullName: "Ahmet" }, { id: 50, fullName: "Zeynep" }])).toEqual([
      { value: 6, label: "Ahmet" },
      { value: 5, label: "Zeynep" },
    ]);
  });

  it("PATCH gövdesi yalnız değişen alanları ve hostes kaldırmayı içerir", () => {
    const trip = { plannedStartTime: "07:30:00", vehicleId: 1, driverId: 2, hostessId: 3 };
    expect(buildTripPatch(trip, { plannedStartTime: "07:30", vehicleId: "1", driverId: "2", hostessId: "3" })).toEqual({});
    expect(buildTripPatch(trip, { plannedStartTime: "07:45", vehicleId: "4", driverId: "2", hostessId: "" })).toEqual({
      plannedStartTime: "07:45",
      vehicleId: 4,
      removeHostess: true,
    });
  });
});
