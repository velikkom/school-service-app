import { describe, it, expect, vi, beforeAll, beforeEach } from "vitest";
import { screen, fireEvent, waitFor } from "@testing-library/react";
import { installMatchMedia, renderWithClient, page, apiError } from "./testUtils";

vi.mock("@/features/students/service/studentsService", () => ({
  studentsService: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    deactivate: vi.fn(),
    statement: vi.fn(),
    statementDocumentPath: (id) => `/students/${id}/statement/document`,
  },
}));
vi.mock("@/features/schools/service/schoolsService", () => ({ schoolsService: { list: vi.fn() } }));
vi.mock("@/features/routes/service/routesService", () => ({ routesService: { list: vi.fn() } }));
vi.mock("@/features/vehicles/service/vehiclesService", () => ({ vehiclesService: { list: vi.fn(), get: vi.fn(), create: vi.fn(), update: vi.fn() } }));
vi.mock("@/features/payments/service/paymentsService", () => ({
  paymentsService: { list: vi.fn(), create: vi.fn(), cancel: vi.fn(), receiptPath: (id) => `/payments/${id}/receipt` },
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn(), push: vi.fn() }),
  usePathname: () => "/test",
  useSearchParams: () => new URLSearchParams(),
}));

import { studentsService } from "@/features/students/service/studentsService";
import { schoolsService } from "@/features/schools/service/schoolsService";
import { vehiclesService } from "@/features/vehicles/service/vehiclesService";
import { paymentsService } from "@/features/payments/service/paymentsService";
import { StudentForm } from "@/features/students/components/StudentManagementScreen";
import { VehicleForm } from "@/features/vehicles/components/VehicleManagementScreen";
import { PaymentFormDialog } from "@/features/payments/components/PaymentsScreen";
import { vehicleSchema } from "@/features/vehicles/schemas/vehicleSchema";
import { studentSchema } from "@/features/students/schemas/studentSchema";

beforeAll(() => installMatchMedia(false));
beforeEach(() => {
  vi.clearAllMocks();
  schoolsService.list.mockResolvedValue(page([{ id: 1, name: "Atatürk İlkokulu", isActive: true }]));
  studentsService.statement.mockResolvedValue({ studentId: 5, entries: [], currentBalance: "1000.00" });
});

const submit = (formId) => fireEvent.submit(document.getElementById(formId));
const input = (label) => screen.getByLabelText(label, { exact: false });

describe("Öğrenci formu", () => {
  it("şema: zorunlu alanlar ve 11 haneli T.C.", () => {
    const r = studentSchema.safeParse({ fullName: "", birthDate: "", tcNo: "123", schoolId: "", grade: "" });
    const msgs = r.error.issues.map((i) => i.message);
    expect(msgs).toContain("Ad soyad zorunludur.");
    expect(msgs).toContain("Doğum tarihi zorunludur.");
    expect(msgs).toContain("T.C. kimlik numarası 11 haneli olmalıdır.");
    expect(studentSchema.safeParse({ fullName: "Ada", birthDate: "2015-03-02", tcNo: "", schoolId: "", grade: "" }).success).toBe(true);
  });

  it("boş gönderimde satır içi hata gösterir, istek atmaz", async () => {
    renderWithClient(<StudentForm record={null} editId={null} onHide={vi.fn()} />);
    submit("student-form");
    expect(await screen.findByText("Ad soyad zorunludur.")).toBeInTheDocument();
    expect(screen.getByText("Doğum tarihi zorunludur.")).toBeInTheDocument();
    expect(studentsService.create).not.toHaveBeenCalled();
  });

  it("T.C. çakışmasında (409) sunucu mesajını dialogda gösterir", async () => {
    studentsService.create.mockRejectedValue(apiError(409, "Bu T.C. kimlik numarası ile kayıtlı öğrenci var."));
    const onHide = vi.fn();
    renderWithClient(<StudentForm record={null} editId={null} onHide={onHide} />);
    fireEvent.change(input("Ad soyad"), { target: { value: "Ada Yılmaz" } });
    fireEvent.change(input("Doğum tarihi"), { target: { value: "2015-03-02" } });
    fireEvent.change(input("T.C. kimlik"), { target: { value: "12345678901" } });
    submit("student-form");
    expect(await screen.findByText("Bu T.C. kimlik numarası ile kayıtlı öğrenci var.")).toBeInTheDocument();
    expect(studentsService.create).toHaveBeenCalledWith({ fullName: "Ada Yılmaz", birthDate: "2015-03-02", tcNo: "12345678901", schoolId: undefined, grade: undefined });
    expect(onHide).not.toHaveBeenCalled();
  });

  it("400 fieldErrors alanlara uygulanır", async () => {
    studentsService.create.mockRejectedValue(apiError(400, "Girilen bilgiler geçersiz.", { fieldErrors: [{ field: "grade", message: "Sınıf geçersiz." }] }));
    renderWithClient(<StudentForm record={null} editId={null} onHide={vi.fn()} />);
    fireEvent.change(input("Ad soyad"), { target: { value: "Ada" } });
    fireEvent.change(input("Doğum tarihi"), { target: { value: "2015-03-02" } });
    submit("student-form");
    expect(await screen.findByText("Sınıf geçersiz.")).toBeInTheDocument();
  });
});

describe("Araç formu", () => {
  it("şema: kapasite pozitif tam sayı, plaka zorunlu", () => {
    const bad = vehicleSchema.safeParse({ plate: "", capacity: "0", model: "", isActive: true });
    const msgs = bad.error.issues.map((i) => i.message);
    expect(msgs).toContain("Plaka zorunludur.");
    expect(msgs).toContain("Kapasite pozitif bir tam sayı olmalıdır.");
    expect(vehicleSchema.safeParse({ plate: "34 ABC 1", capacity: "2.5", model: "", isActive: true }).success).toBe(false);
    expect(vehicleSchema.safeParse({ plate: "34 abc 123", capacity: "16", model: "", isActive: true }).success).toBe(true);
  });

  it("plakayı normalize ederek gönderir; plaka çakışması 409 mesajını gösterir", async () => {
    vehiclesService.create.mockRejectedValue(apiError(409, "Bu plaka zaten kayıtlı."));
    renderWithClient(<VehicleForm record={null} editId={null} onHide={vi.fn()} />);
    fireEvent.change(input("Plaka"), { target: { value: " 34  abc 123 " } });
    fireEvent.change(input("Kapasite"), { target: { value: "16" } });
    submit("vehicle-form");
    expect(await screen.findByText("Bu plaka zaten kayıtlı.")).toBeInTheDocument();
    expect(vehiclesService.create).toHaveBeenCalledWith({ plate: "34 ABC 123", capacity: 16, model: undefined });
  });

  it("kapasite 0 iken hata verir", async () => {
    renderWithClient(<VehicleForm record={null} editId={null} onHide={vi.fn()} />);
    fireEvent.change(input("Plaka"), { target: { value: "34 ABC 123" } });
    fireEvent.change(input("Kapasite"), { target: { value: "0" } });
    submit("vehicle-form");
    expect(await screen.findByText("Kapasite pozitif bir tam sayı olmalıdır.")).toBeInTheDocument();
    expect(vehiclesService.create).not.toHaveBeenCalled();
  });
});

describe("Ödeme kaydet", () => {
  const students = [{ id: 5, fullName: "Ada Yılmaz", isActive: true }];

  it("tekrar gönderimde aynı Idempotency-Key kullanılır; borçtan fazla 409 mesajı gösterilir", async () => {
    paymentsService.create
      .mockRejectedValueOnce(apiError(0, "Sunucuya ulaşılamadı. Bağlantınızı kontrol edin.", { error: "NETWORK_ERROR" }))
      .mockRejectedValueOnce(apiError(409, "Ödeme tutarı kalan bakiyeden fazla olamaz."));
    renderWithClient(<PaymentFormDialog presetStudentId="5" studentOptions={students} onHide={vi.fn()} />);
    fireEvent.change(input("Tutar"), { target: { value: "1250,50" } });
    submit("payment-form");
    expect(await screen.findByText("Sunucuya ulaşılamadı. Bağlantınızı kontrol edin.")).toBeInTheDocument();
    submit("payment-form");
    expect(await screen.findByText("Ödeme tutarı kalan bakiyeden fazla olamaz.")).toBeInTheDocument();
    expect(screen.getByText("Tutar kalan borçtan fazla olabilir")).toBeInTheDocument();

    expect(paymentsService.create).toHaveBeenCalledTimes(2);
    const [body1, key1] = paymentsService.create.mock.calls[0];
    const [, key2] = paymentsService.create.mock.calls[1];
    expect(body1).toEqual({ studentId: 5, amount: "1250.50", method: "CASH", note: undefined });
    expect(key1).toBeTruthy();
    expect(key2).toBe(key1);
  });

  it("yeni dialog yeni anahtar üretir", async () => {
    paymentsService.create.mockResolvedValue({ id: 1 });
    const first = renderWithClient(<PaymentFormDialog presetStudentId="5" studentOptions={students} onHide={vi.fn()} />);
    fireEvent.change(input("Tutar"), { target: { value: "10" } });
    submit("payment-form");
    await waitFor(() => expect(paymentsService.create).toHaveBeenCalledTimes(1));
    first.unmount();
    renderWithClient(<PaymentFormDialog presetStudentId="5" studentOptions={students} onHide={vi.fn()} />);
    fireEvent.change(input("Tutar"), { target: { value: "10" } });
    submit("payment-form");
    await waitFor(() => expect(paymentsService.create).toHaveBeenCalledTimes(2));
    expect(paymentsService.create.mock.calls[1][1]).not.toBe(paymentsService.create.mock.calls[0][1]);
  });

  it("geçersiz tutarda istek atmaz", async () => {
    renderWithClient(<PaymentFormDialog presetStudentId="5" studentOptions={students} onHide={vi.fn()} />);
    fireEvent.change(input("Tutar"), { target: { value: "-5" } });
    submit("payment-form");
    expect(await screen.findByText("Geçerli bir tutar girin (örn. 1250,00).")).toBeInTheDocument();
    expect(paymentsService.create).not.toHaveBeenCalled();
  });
});
