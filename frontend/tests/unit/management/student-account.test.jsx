import { describe, it, expect, vi, beforeAll, beforeEach } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { installMatchMedia, renderWithClient, page, apiError } from "./testUtils";

vi.mock("@/features/students/service/studentsService", () => ({
  studentsService: { linkAccount: vi.fn(), unlinkAccount: vi.fn() },
}));
vi.mock("@/features/users/service/usersService", () => ({
  usersService: { list: vi.fn(), get: vi.fn(), create: vi.fn() },
}));

import { studentsService } from "@/features/students/service/studentsService";
import { usersService } from "@/features/users/service/usersService";
import StudentAccountCard from "@/features/students/components/StudentAccountCard";
import { studentAccountSchema } from "@/features/students/schemas/studentSchema";

const student = { id: 7, fullName: "Ada Yılmaz", isActive: true, userId: null };

beforeAll(() => installMatchMedia(false));
beforeEach(() => {
  vi.clearAllMocks();
  usersService.list.mockResolvedValue(page([{ id: 31, fullName: "Ada Hesap", email: "ada@example.test" }]));
});

const submit = () => fireEvent.submit(document.getElementById("student-account-form"));

describe("Öğrenci hesabı bağı (Sprint 3)", () => {
  it("şema: mevcut modda hesap, yeni modda ad ve geçerli e-posta zorunlu", () => {
    const base = { accountMode: "existing", userId: "", fullName: "", email: "", phone: "" };
    expect(studentAccountSchema.safeParse(base).error.issues[0].message).toBe("Öğrenci hesabı seçin.");
    const msgs = studentAccountSchema.safeParse({ ...base, accountMode: "new", email: "x" }).error.issues.map((i) => i.message);
    expect(msgs).toEqual(expect.arrayContaining(["Ad soyad zorunludur.", "Geçerli bir e-posta adresi girin."]));
    expect(studentAccountSchema.safeParse({ ...base, userId: "31" }).success).toBe(true);
  });

  it("bağ yoksa yalnız bağlanabilir STUDENT hesaplarını listeler", async () => {
    renderWithClient(<StudentAccountCard student={student} />);
    expect(screen.getByText("Bağlı hesap yok; öğrenci giriş yapamaz.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Hesap bağla" }));
    await waitFor(() =>
      expect(usersService.list).toHaveBeenCalledWith({ role: "STUDENT", isActive: true, studentLinked: false, size: 100, sort: "fullName,asc" })
    );
  });

  it("pasif öğrencide bağlama butonu kapalıdır", () => {
    renderWithClient(<StudentAccountCard student={{ ...student, isActive: false }} />);
    expect(screen.getByRole("button", { name: "Hesap bağla" })).toBeDisabled();
  });

  it("yeni hesap: önce STUDENT hesabı oluşturur, sonra bağlar ve geçici şifreyi bir kez gösterir", async () => {
    usersService.create.mockResolvedValue({ user: { id: 44 }, temporaryPassword: "Gecici123abc" });
    studentsService.linkAccount.mockResolvedValue({ ...student, userId: 44 });
    renderWithClient(<StudentAccountCard student={student} />);
    fireEvent.click(screen.getByRole("button", { name: "Hesap bağla" }));
    fireEvent.click(await screen.findByText("Yeni hesap oluştur"));
    expect(screen.getByLabelText("Ad soyad", { exact: false })).toHaveValue("Ada Yılmaz");
    fireEvent.change(screen.getByLabelText("E-posta", { exact: false }), { target: { value: "ada.yeni@example.test" } });
    submit();
    expect(await screen.findByText("Gecici123abc")).toBeInTheDocument();
    expect(usersService.create).toHaveBeenCalledWith({ fullName: "Ada Yılmaz", email: "ada.yeni@example.test", phone: undefined, role: "STUDENT" });
    expect(studentsService.linkAccount).toHaveBeenCalledWith(7, 44);
  });

  it("bağ 409 ile reddedilirse sunucu mesajını gösterir; tekrar denemede ikinci hesap açmaz", async () => {
    usersService.create.mockResolvedValue({ user: { id: 45 }, temporaryPassword: null });
    studentsService.linkAccount.mockRejectedValueOnce(apiError(409, "Bu öğrenci zaten başka bir hesaba bağlı. Önce mevcut bağı kaldırın."));
    studentsService.linkAccount.mockResolvedValueOnce({ ...student, userId: 45 });
    const { client } = renderWithClient(<StudentAccountCard student={student} />);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    fireEvent.click(screen.getByRole("button", { name: "Hesap bağla" }));
    fireEvent.click(await screen.findByText("Yeni hesap oluştur"));
    fireEvent.change(screen.getByLabelText("E-posta", { exact: false }), { target: { value: "ada.iki@example.test" } });
    submit();
    expect(await screen.findByText("Bu öğrenci zaten başka bir hesaba bağlı. Önce mevcut bağı kaldırın.")).toBeInTheDocument();
    submit();
    await waitFor(() => expect(studentsService.linkAccount).toHaveBeenCalledTimes(2));
    expect(usersService.create).toHaveBeenCalledTimes(1);
    expect(studentsService.linkAccount).toHaveBeenLastCalledWith(7, 45);
    expect(invalidate).toHaveBeenCalled();
  });

  it("bağ varsa hesabı ve durumunu gösterir, bağ kaldırma sunar", async () => {
    usersService.get.mockResolvedValue({ id: 31, fullName: "Ada Hesap", email: "ada@example.test", isActive: false });
    renderWithClient(<StudentAccountCard student={{ ...student, userId: 31 }} />);
    expect(await screen.findByText("ada@example.test")).toBeInTheDocument();
    expect(screen.getByText("Bağlı · hesap pasif (giriş yapamaz)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bağı kaldır" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Hesap bağla" })).toBeNull();
  });
});
