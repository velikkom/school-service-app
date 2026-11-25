import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";
import { screen, within } from "@testing-library/react";
import { installMatchMedia, page, renderWithClient } from "./testUtils";

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/yoklama-duzeltmeleri",
  useSearchParams: () => new URLSearchParams(""),
}));
vi.mock("@/features/attendance-corrections/service/attendanceCorrectionsService", () => ({
  attendanceCorrectionsService: { list: vi.fn(), approve: vi.fn(), reject: vi.fn() },
}));

import { attendanceCorrectionsService } from "@/features/attendance-corrections/service/attendanceCorrectionsService";
import CorrectionQueueScreen from "@/features/attendance-corrections/components/CorrectionQueueScreen";
import MyCorrectionRequestsScreen from "@/features/attendance-corrections/components/MyCorrectionRequestsScreen";

const corr = (id, extra = {}) => ({
  id,
  tripId: 7,
  tripBusinessDate: "2026-11-19",
  tripDirection: "MORNING",
  studentId: id,
  studentFullName: `Öğrenci ${id}`,
  kind: "CORRECTION",
  sourceStatus: "ABSENT",
  requestedStatus: "BOARDED",
  currentStatus: "ABSENT",
  stale: false,
  reason: "Yanlış girildi",
  status: "PENDING",
  requestedByFullName: "Ali Şoför",
  requestedByRole: "DRIVER",
  createdAt: "2026-11-19T05:00:00Z",
  ...extra,
});

beforeAll(() => installMatchMedia(false));
beforeEach(() => vi.clearAllMocks());

describe("CorrectionQueueScreen", () => {
  it("stale talepte 'Kayıt değişmiş' uyarısı ve onay pasif; normal talepte onay aktif", async () => {
    attendanceCorrectionsService.list.mockResolvedValue(page([corr(1, { stale: true, currentStatus: "BOARDED" }), corr(2)]));
    renderWithClient(<CorrectionQueueScreen />);
    const staleRow = await screen.findByTestId("correction-1");
    expect(within(staleRow).getByText("Kayıt değişmiş")).toBeInTheDocument();
    expect(within(staleRow).getByRole("button", { name: /onayla/i })).toBeDisabled();
    expect(within(staleRow).getByRole("button", { name: /reddet/i })).toBeEnabled();
    const okRow = screen.getByTestId("correction-2");
    expect(within(okRow).queryByText("Kayıt değişmiş")).toBeNull();
    expect(within(okRow).getByRole("button", { name: /onayla/i })).toBeEnabled();
    expect(attendanceCorrectionsService.list).toHaveBeenCalledWith(expect.objectContaining({ status: "PENDING" }));
  });

  it("kaynak → hedef durum etiketlerle gösterilir (ham enum yok)", async () => {
    attendanceCorrectionsService.list.mockResolvedValue(page([corr(3)]));
    renderWithClient(<CorrectionQueueScreen />);
    const row = await screen.findByTestId("correction-3");
    expect(within(row).getAllByText("Gelmedi").length).toBeGreaterThan(0);
    expect(within(row).getByText("Bindi")).toBeInTheDocument();
    expect(row.textContent).not.toMatch(/BOARDED|ABSENT/);
  });
});

describe("MyCorrectionRequestsScreen", () => {
  it("karar notu gösterilir, karar butonu yoktur", async () => {
    attendanceCorrectionsService.list.mockResolvedValue(
      page([corr(4, { status: "APPROVED", decisionNote: "Kamera kaydı kontrol edildi", appliedStatus: "BOARDED", decidedAt: "2026-11-19T06:00:00Z" })])
    );
    renderWithClient(<MyCorrectionRequestsScreen />);
    const row = await screen.findByTestId("correction-4");
    expect(within(row).getByText("Onaylandı")).toBeInTheDocument();
    expect(within(row).getByText(/Kamera kaydı kontrol edildi/)).toBeInTheDocument();
    expect(within(row).queryByRole("button")).toBeNull();
  });
});
