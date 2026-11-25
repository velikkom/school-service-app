import { describe, expect, it, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
  usePathname: () => "/servisim",
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock("@/features/tracking/hooks/useLatestLocation", () => ({
  useLatestLocation: () => ({ isPending: false, isError: false, data: null, refetch: vi.fn() }),
}));
const logout = vi.fn();
vi.mock("@/providers/AuthProvider", () => ({ useAuth: () => ({ logout, role: "PARENT" }) }));
vi.mock("@/features/students/service/studentsService", () => ({ studentsService: { list: vi.fn() } }));
vi.mock("@/features/contracts/service/contractsService", () => ({ contractsService: { list: vi.fn() } }));
vi.mock("@/features/schools/service/schoolsService", () => ({
  schoolsService: { list: vi.fn(async () => ({ content: [{ id: 3, name: "Yeşilvadi İlkokulu" }] })) },
}));

import ServiceCard from "@/features/daily-service/components/ServiceCard";
import EnrollmentGate from "@/features/enrollment/components/EnrollmentGate";
import { studentsService } from "@/features/students/service/studentsService";
import { contractsService } from "@/features/contracts/service/contractsService";

const wrap = (ui) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
};

const entry = (over = {}) => ({
  tripId: 7,
  direction: "MORNING",
  status: "PLANNED",
  plannedStartTime: "07:00:00",
  routeName: "Sabah 1",
  vehiclePlate: "20 ABC 123",
  stopId: 4,
  stopName: "Çamlık",
  stopPlannedTime: "07:25:00",
  attendanceStatus: "NONE",
  absenceNotice: null,
  canSubmitAbsenceNotice: false,
  canWithdrawAbsenceNotice: false,
  latestIncident: null,
  cancellationReason: null,
  ...over,
});

describe("ServiceCard bildirim butonları", () => {
  it("canSubmitAbsenceNotice=true → 'Servise binmeyecek' görünür", () => {
    wrap(<ServiceCard hero entry={entry({ canSubmitAbsenceNotice: true })} role="PARENT" />);
    expect(screen.getByRole("button", { name: /Servise binmeyecek/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Bildirimi geri al/ })).not.toBeInTheDocument();
    expect(screen.getByText("07:25")).toBeInTheDocument();
  });

  it("canWithdrawAbsenceNotice=true → 'Bildirimi geri al' + mor bildirim kutusu", () => {
    const notice = { id: 9, note: "Doktor", createdAt: "2026-11-19T04:00:00Z", parentFullName: "Ayşe" };
    wrap(<ServiceCard hero entry={entry({ canWithdrawAbsenceNotice: true, absenceNotice: notice })} role="PARENT" />);
    expect(screen.getByRole("button", { name: /Bildirimi geri al/ })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Servise binmeyecek/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Resmi yoklama değildir/)).toBeInTheDocument();
  });

  it("STUDENT rolünde bayraklar true olsa da buton yok", () => {
    wrap(<ServiceCard hero entry={entry({ canSubmitAbsenceNotice: true, canWithdrawAbsenceNotice: true, absenceNotice: { id: 1 } })} role="STUDENT" />);
    expect(screen.queryByRole("button", { name: /Servise binmeyecek/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Bildirimi geri al/ })).not.toBeInTheDocument();
    expect(screen.getByText(/Veliniz/)).toBeInTheDocument();
  });

  it("iptal ve gecikme kutuları", () => {
    wrap(<ServiceCard hero entry={entry({ status: "CANCELLED", cancellationReason: "Kar yağışı" })} role="PARENT" />);
    expect(screen.getByText("Bu sefer iptal edildi")).toBeInTheDocument();
    expect(screen.getByText("Kar yağışı")).toBeInTheDocument();
  });

  it("gecikme rozeti (DELAY, açık sefer)", () => {
    wrap(<ServiceCard entry={entry({ latestIncident: { type: "DELAY", delayMinutes: 10, note: "Trafik", createdAt: "2026-11-19T04:10:00Z" } })} role="PARENT" />);
    expect(screen.getByText("Gecikme +10 dk")).toBeInTheDocument();
  });
});

describe("EnrollmentGate", () => {
  beforeEach(() => {
    studentsService.list.mockReset();
    contractsService.list.mockReset();
  });

  it("PARENT değilse doğrudan children", () => {
    wrap(<EnrollmentGate user={{ id: 1, role: "ADMIN" }}><p>uygulama</p></EnrollmentGate>);
    expect(screen.getByText("uygulama")).toBeInTheDocument();
    expect(studentsService.list).not.toHaveBeenCalled();
  });

  it("aktif sözleşme varsa children", async () => {
    studentsService.list.mockResolvedValue({ content: [{ id: 1, fullName: "Ece" }] });
    contractsService.list.mockResolvedValue({ content: [{ status: "ACTIVE" }] });
    wrap(<EnrollmentGate user={{ id: 2, role: "PARENT" }}><p>uygulama</p></EnrollmentGate>);
    expect(await screen.findByText("uygulama")).toBeInTheDocument();
  });

  it("yalnız bekleyen başvuru → Onay bekliyor + çıkış", async () => {
    studentsService.list.mockResolvedValue({ content: [{ id: 1, fullName: "Ece Korkmaz" }] });
    contractsService.list.mockResolvedValue({ content: [{ status: "PENDING_APPROVAL" }] });
    wrap(<EnrollmentGate user={{ id: 3, role: "PARENT" }}><p>uygulama</p></EnrollmentGate>);
    expect(await screen.findByText("Başvurunuz yönetici onayında")).toBeInTheDocument();
    expect(screen.getByText(/Ece Korkmaz/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Çıkış yap/ })).toBeInTheDocument();
    expect(screen.queryByText("uygulama")).not.toBeInTheDocument();
  });

  it("öğrenci yoksa tam ekran sihirbaz (okul adımı)", async () => {
    studentsService.list.mockResolvedValue({ content: [] });
    wrap(<EnrollmentGate user={{ id: 4, role: "PARENT" }}><p>uygulama</p></EnrollmentGate>);
    expect(await screen.findByText("Öğrenci kaydı")).toBeInTheDocument();
    expect(screen.getByText("Okul seçimi")).toBeInTheDocument();
    await waitFor(() => expect(document.getElementById("enr-school")).not.toBeNull());
    expect(screen.queryByText("uygulama")).not.toBeInTheDocument();
  });
});
