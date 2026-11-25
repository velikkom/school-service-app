import { describe, it, expect, vi, beforeEach } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { renderWithClient, stubMatchMedia } from "./helpers";

vi.mock("@/features/notifications/service/notificationsService", () => ({
  notificationsService: { list: vi.fn(), markRead: vi.fn(), broadcast: vi.fn() },
}));
vi.mock("@/providers/AuthProvider", () => ({ useAuth: () => ({ role: "PARENT", user: { id: 1 } }) }));
vi.mock("@/components/feedback/feedback", () => ({ toastSuccess: vi.fn(), toastError: vi.fn(), toastInfo: vi.fn(), confirmAction: vi.fn() }));

import { notificationsService } from "@/features/notifications/service/notificationsService";
import NotificationsScreen from "@/features/notifications/components/NotificationsScreen";
import { onlyInApp } from "@/features/notifications/hooks/useNotificationList";

const page = (content) => ({ content, page: 0, size: 20, totalElements: content.length, totalPages: 1 });

describe("Bildirimler", () => {
  beforeEach(() => {
    stubMatchMedia();
    vi.clearAllMocks();
    notificationsService.list.mockResolvedValue(
      page([
        { id: 1, channel: "IN_APP", title: "Uygulama duyurusu", body: "Metin", read: false, createdAt: "2026-11-19T07:00:00Z" },
        { id: 2, channel: "EMAIL", title: "E-posta kopyası", body: "Metin", read: false, createdAt: "2026-11-19T07:00:00Z" },
      ])
    );
    notificationsService.markRead.mockResolvedValue({ id: 1, read: true });
  });

  it("onlyInApp EMAIL kanalını eler", () => {
    expect(onlyInApp([{ channel: "IN_APP" }, { channel: "EMAIL" }])).toEqual([{ channel: "IN_APP" }]);
  });

  it("listede EMAIL kanalı gösterilmez, IN_APP gösterilir", async () => {
    renderWithClient(<NotificationsScreen />);
    expect(await screen.findByText("Uygulama duyurusu")).toBeInTheDocument();
    expect(screen.queryByText("E-posta kopyası")).not.toBeInTheDocument();
  });

  it("okunmamış bildirime tıklanınca okundu işaretlenir", async () => {
    renderWithClient(<NotificationsScreen />);
    fireEvent.click(await screen.findByRole("button", { name: /Uygulama duyurusu — okundu işaretle/ }));
    await waitFor(() => expect(notificationsService.markRead).toHaveBeenCalledWith(1));
  });
});
