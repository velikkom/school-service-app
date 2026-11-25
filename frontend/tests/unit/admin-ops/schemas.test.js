import { describe, it, expect } from "vitest";
import { registrationApproveSchema, toRegistrationApproveBody } from "@/features/registration-requests/schemas/registrationApproveSchema";
import { enrollmentApproveSchema, toApproveBody } from "@/features/requests/schemas/enrollmentApproveSchema";
import { broadcastSchema, toBroadcastBody } from "@/features/notifications/schemas/broadcastSchema";
import { buildSettingsPatch, makeSettingsSchema } from "@/features/settings/schemas/settingsSchema";
import { rosterCounts, sortTrips } from "@/features/operations/hooks/useDailyOperations";
import { methodBreakdown } from "@/features/reports/hooks/useReports";

const issues = (r) => (r.success ? [] : r.error.issues.map((i) => [i.path.join("."), i.message]));

describe("Hesap kayıt onayı şeması", () => {
  it("DRIVER rolünde licenseNo zorunlu", () => {
    const r = registrationApproveSchema.safeParse({ role: "DRIVER", phone: "", licenseNo: "  " });
    expect(issues(r)).toContainEqual(["licenseNo", "Şoför rolünde ehliyet no zorunludur."]);
    expect(registrationApproveSchema.safeParse({ role: "DRIVER", phone: "", licenseNo: "B-123" }).success).toBe(true);
  });
  it("diğer rollerde licenseNo gerekmez ve gövdeye eklenmez", () => {
    const r = registrationApproveSchema.parse({ role: "PARENT", phone: "0555", licenseNo: "X" });
    expect(toRegistrationApproveBody(r)).toEqual({ role: "PARENT", phone: "0555" });
  });
  it("rol boşsa hata", () => {
    expect(registrationApproveSchema.safeParse({ role: "", phone: "", licenseNo: "" }).success).toBe(false);
  });
});

describe("Öğrenci başvurusu onay şeması", () => {
  it("monthlyFee zorunlu ve > 0", () => {
    expect(issues(enrollmentApproveSchema.safeParse({ monthlyFee: "", discountAmount: "", startDate: "" }))[0][0]).toBe("monthlyFee");
    expect(enrollmentApproveSchema.safeParse({ monthlyFee: "0", discountAmount: "", startDate: "" }).success).toBe(false);
  });
  it("virgüllü tutar normalize edilir, boş alanlar gönderilmez", () => {
    const v = enrollmentApproveSchema.parse({ monthlyFee: "1250,50", discountAmount: "", startDate: "" });
    expect(toApproveBody(v)).toEqual({ monthlyFee: "1250.50" });
  });
});

describe("Toplu bildirim şeması", () => {
  const base = { target: "PARENTS", targetRole: "", parentIds: [], studentIds: [], title: "Duyuru", body: "Metin" };
  it("seçili veliler hedefinde en az bir veli gerekir", () => {
    expect(issues(broadcastSchema.safeParse(base))).toContainEqual(["parentIds", "En az bir veli seçin."]);
  });
  it("gövde yalnız seçili hedef türünü içerir", () => {
    const v = broadcastSchema.parse({ ...base, target: "ROLE", targetRole: "PARENT", parentIds: [3] });
    expect(toBroadcastBody(v)).toEqual({ title: "Duyuru", body: "Metin", targetRole: "PARENT" });
  });
});

describe("Kurum ayarları PATCH", () => {
  const original = { name: "Kurum", address: "Adres", phone: null, reportHeaderNote: "", defaultMonthlyFee: "1500.00" };
  it("yalnız değişen alanlar gönderilir; boş metin = temizle", () => {
    const patch = buildSettingsPatch({ name: "Kurum", address: "", phone: "", reportHeaderNote: "", defaultMonthlyFee: "1500" }, original);
    expect(patch).toEqual({ address: "" });
  });
  it("ücret değişince gönderilir; tanımlı ücret boşaltılamaz; ad boş olamaz", () => {
    expect(buildSettingsPatch({ ...original, phone: "", defaultMonthlyFee: "1600.00" }, original)).toEqual({ defaultMonthlyFee: "1600.00" });
    const r = makeSettingsSchema(original).safeParse({ name: " ", address: "", phone: "", reportHeaderNote: "", defaultMonthlyFee: "" });
    const paths = issues(r).map((i) => i[0]);
    expect(paths).toContain("name");
    expect(paths).toContain("defaultMonthlyFee");
  });
});

describe("Operasyon/rapor yardımcıları", () => {
  it("rosterCounts", () => {
    expect(rosterCounts([{ status: "NONE" }, { status: "BOARDED" }, { status: "ABSENT", absenceNotice: { id: 1 } }])).toEqual({
      total: 3,
      recorded: 2,
      absent: 1,
      notices: 1,
    });
  });
  it("sortTrips saate sonra yöne göre", () => {
    const s = sortTrips([
      { id: 1, plannedStartTime: "16:00:00", direction: "AFTERNOON" },
      { id: 2, plannedStartTime: "07:00:00", direction: "MORNING" },
      { id: 3, plannedStartTime: null, direction: "MORNING" },
    ]);
    expect(s.map((t) => t.id)).toEqual([2, 1, 3]);
  });
  it("methodBreakdown eksik yöntemi 0 sayar", () => {
    const r = methodBreakdown({ totalCollected: "200.00", byMethod: [{ method: "CASH", amount: "150.00" }] }, ["CASH", "CARD"]);
    expect(r).toEqual([
      { method: "CASH", amount: "150.00", pct: 75 },
      { method: "CARD", amount: "0.00", pct: 0 },
    ]);
  });
});
