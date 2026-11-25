import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { renderWithClient, stubMatchMedia } from "./helpers";

vi.mock("@/features/stop-change-requests/service/stopChangeRequestsService", () => ({
  stopChangeRequestsService: { list: vi.fn(), approve: vi.fn(), reject: vi.fn() },
}));
vi.mock("@/components/feedback/feedback", () => ({ toastSuccess: vi.fn(), toastError: vi.fn(), toastInfo: vi.fn(), confirmAction: vi.fn() }));

import { stopChangeRequestsService } from "@/features/stop-change-requests/service/stopChangeRequestsService";
import StopChangeQueueScreen from "@/features/requests/components/StopChangeQueueScreen";

const request = {
  id: 7,
  studentFullName: "Ada Yılmaz",
  requestedByFullName: "Ayşe Yılmaz",
  direction: "MORNING",
  currentStopName: "Çarşı",
  requestedStopName: "Park",
  requestedRouteName: "Hat 1",
  effectiveDate: "2026-11-25",
  reason: "Taşındık",
  status: "PENDING",
  createdAt: "2026-11-19T06:00:00Z",
};

describe("Durak değişiklikleri kuyruğu", () => {
  beforeEach(() => {
    stubMatchMedia();
    vi.clearAllMocks();
    stopChangeRequestsService.list.mockResolvedValue({ content: [request], page: 0, size: 20, totalElements: 1, totalPages: 1 });
    stopChangeRequestsService.reject.mockResolvedValue({ ...request, status: "REJECTED" });
  });

  it("mevcut → istenen durak ve yürürlük tarihi gösterilir", async () => {
    renderWithClient(<StopChangeQueueScreen />);
    expect(await screen.findByText("Ada Yılmaz")).toBeInTheDocument();
    expect(screen.getByText("Çarşı")).toBeInTheDocument();
    expect(screen.getByText("Park")).toBeInTheDocument();
    expect(screen.getByText("25.11.2026")).toBeInTheDocument();
  });

  it("ret dialogu boş gerekçeyle gönderilemez", async () => {
    renderWithClient(<StopChangeQueueScreen />);
    fireEvent.click(await screen.findByRole("button", { name: "Reddet" }));
    await screen.findByText("Durak değişikliğini reddet");
    const submit = document.querySelector("button[type=submit]");
    fireEvent.click(submit);
    expect(await screen.findByText("Ret gerekçesi zorunludur.")).toBeInTheDocument();
    expect(stopChangeRequestsService.reject).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText(/Ret gerekçesi/), { target: { value: "Durak dolu" } });
    fireEvent.click(submit);
    await waitFor(() => expect(stopChangeRequestsService.reject).toHaveBeenCalledWith(7, "Durak dolu"));
  });
});
