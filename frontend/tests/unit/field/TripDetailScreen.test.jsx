import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { installMatchMedia, renderWithClient } from "./testUtils";

const auth = vi.hoisted(() => ({ role: "DRIVER" }));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/seferler/7",
  useSearchParams: () => new URLSearchParams(""),
}));
vi.mock("@/providers/AuthProvider", () => ({
  useAuth: () => ({ role: auth.role, user: { id: 1, fullName: "Test Kullanıcı" } }),
}));
vi.mock("@/features/trips/service/tripsService", () => ({
  tripsService: { get: vi.fn(), roster: vi.fn(), incidents: vi.fn(), absenceNotices: vi.fn() },
}));
vi.mock("@/features/routes/service/routesService", () => ({ routesService: { stops: vi.fn().mockResolvedValue([]) } }));
vi.mock("@/features/tracking/service/trackingService", () => ({
  trackingService: { latest: vi.fn().mockResolvedValue(null), send: vi.fn() },
}));

import { tripsService } from "@/features/trips/service/tripsService";
import TripDetailScreen from "@/features/trips/components/TripDetailScreen";

const trip = (extra = {}) => ({
  id: 7,
  routeId: 3,
  routeName: "Kuzey Hattı",
  vehiclePlate: "34 ABC 12",
  driverFullName: "Ali Şoför",
  hostessFullName: "Ayşe Hostes",
  businessDate: "2026-11-19",
  direction: "MORNING",
  status: "IN_PROGRESS",
  plannedStartTime: "07:00:00",
  openCancellationRequestId: null,
  ...extra,
});

const base = { stopPlannedTime: "07:10:00", absenceNotice: null, pendingCorrectionId: null };
const rosterRows = [
  { ...base, studentId: 1, studentFullName: "Can Yılmaz", stopId: 10, stopName: "Okul Kapısı", sequenceNo: 1, status: "BOARDED", pendingCorrectionId: 55 },
  { ...base, studentId: 2, studentFullName: "Ece Demir", stopId: 10, stopName: "Okul Kapısı", sequenceNo: 1, status: "ABSENT" },
  {
    ...base,
    studentId: 3,
    studentFullName: "Mert Kaya",
    stopId: 20,
    stopName: "Park",
    sequenceNo: 2,
    status: "NONE",
    absenceNotice: { id: 9, note: "Hasta", parentFullName: "Veli Kaya", createdAt: "2026-11-19T04:00:00Z" },
  },
];

beforeAll(() => installMatchMedia(false));
beforeEach(() => {
  vi.clearAllMocks();
  tripsService.roster.mockResolvedValue(rosterRows);
  tripsService.incidents.mockResolvedValue([]);
  tripsService.absenceNotices.mockResolvedValue([]);
});

describe("TripDetailScreen — saha görünümü", () => {
  it.each(["DRIVER", "HOSTESS"])("%s için doğrudan 'Seferi iptal et' yok, 'İptal talebi' var", async (role) => {
    auth.role = role;
    tripsService.get.mockResolvedValue(trip());
    renderWithClient(<TripDetailScreen id="7" />);
    expect(await screen.findByText("Can Yılmaz")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /seferi iptal et/i })).toBeNull();
    expect(screen.getByRole("button", { name: "İptal talebi" })).toBeEnabled();
    expect(screen.getByRole("button", { name: /seferi tamamla/i })).toBeInTheDocument();
  });

  it("açık iptal talebi varken 'İptal talebi' pasif", async () => {
    auth.role = "DRIVER";
    tripsService.get.mockResolvedValue(trip({ openCancellationRequestId: 99 }));
    renderWithClient(<TripDetailScreen id="7" />);
    expect(await screen.findByText(/yönetici kararını bekliyor/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "İptal talebi" })).toBeDisabled();
  });

  it("pendingCorrectionId olan satırda rozet var, 'Düzeltme iste' yok", async () => {
    auth.role = "HOSTESS";
    tripsService.get.mockResolvedValue(trip());
    renderWithClient(<TripDetailScreen id="7" />);
    const pending = await screen.findByTestId("roster-row-1");
    expect(within(pending).getByText("Düzeltme bekliyor")).toBeInTheDocument();
    expect(within(pending).queryByRole("button", { name: /düzeltme iste/i })).toBeNull();
    expect(within(screen.getByTestId("roster-row-2")).getByRole("button", { name: /düzeltme iste/i })).toBeInTheDocument();
  });

  it("veli bildirimi satırı: mor rozet + not, resmi durum 'İşlem yok', 'Bindi' aktif", async () => {
    auth.role = "DRIVER";
    tripsService.get.mockResolvedValue(trip());
    renderWithClient(<TripDetailScreen id="7" />);
    const r = await screen.findByTestId("roster-row-3");
    expect(within(r).getByText("Veli: binmeyecek")).toBeInTheDocument();
    expect(within(r).getByText("İşlem yok")).toBeInTheDocument();
    expect(within(r).getByText(/Hasta/)).toBeInTheDocument();
    expect(within(r).getByRole("button", { name: /bindi/i })).toBeEnabled();
  });

  it("planlı seferde yoklama butonu yok, 'Seferi başlat' var", async () => {
    auth.role = "DRIVER";
    tripsService.get.mockResolvedValue(trip({ status: "PLANNED" }));
    renderWithClient(<TripDetailScreen id="7" />);
    expect(await screen.findByText("Mert Kaya")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /^bindi$/i })).toBeNull();
    expect(screen.getByRole("button", { name: /seferi başlat/i })).toBeInTheDocument();
  });

  it("404 → bulunamadı / kapsam dışı ekranı", async () => {
    auth.role = "DRIVER";
    tripsService.get.mockRejectedValue(Object.assign(new Error("Sefer bulunamadı: 7"), { status: 404 }));
    renderWithClient(<TripDetailScreen id="7" />);
    expect(await screen.findByText("Sefer bulunamadı")).toBeInTheDocument();
  });
});

describe("TripDetailScreen — ADMIN görünümü", () => {
  it("'Seferi iptal et' ayrı kontrol; başlamış seferde 'Düzenle' yok", async () => {
    auth.role = "ADMIN";
    tripsService.get.mockResolvedValue(trip());
    renderWithClient(<TripDetailScreen id="7" />);
    expect(await screen.findByRole("button", { name: /seferi iptal et/i })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /düzenle/i })).toBeNull();
    expect(screen.queryByRole("button", { name: "İptal talebi" })).toBeNull();
  });

  it("planlı seferde 'Düzenle' görünür", async () => {
    auth.role = "ADMIN";
    tripsService.get.mockResolvedValue(trip({ status: "PLANNED" }));
    renderWithClient(<TripDetailScreen id="7" />);
    expect(await screen.findByRole("button", { name: /düzenle/i })).toBeInTheDocument();
  });
});
