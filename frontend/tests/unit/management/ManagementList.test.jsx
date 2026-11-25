import { describe, it, expect, vi, beforeAll } from "vitest";
import { render, screen, fireEvent } from "@testing-library/react";
import ManagementList from "@/components/data/ManagementList";
import { installMatchMedia, page } from "./testUtils";

beforeAll(() => installMatchMedia(false));

const columns = [
  { key: "name", header: "Ad", main: true, body: (r) => r.name },
  { key: "phone", header: "Telefon", body: (r) => r.phone },
];

const q = (over) => ({ data: undefined, isPending: false, isError: false, isFetching: false, isPlaceholderData: false, error: null, refetch: vi.fn(), ...over });

describe("ManagementList", () => {
  it("yüklenirken iskelet gösterir (aria-busy)", () => {
    render(<ManagementList columns={columns} query={q({ isPending: true })} />);
    expect(screen.getByRole("status")).toHaveAttribute("aria-busy", "true");
  });

  it("boş listede boş durum ve aksiyon gösterir", () => {
    render(
      <ManagementList
        columns={columns}
        query={q({ data: page([]) })}
        emptyTitle="Henüz öğrenci yok"
        emptyAction={<button type="button">Yeni öğrenci</button>}
      />
    );
    expect(screen.getByText("Henüz öğrenci yok")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Yeni öğrenci" })).toBeInTheDocument();
  });

  it("filtre varken boş listede filtre mesajı gösterir", () => {
    render(<ManagementList columns={columns} query={q({ data: page([]) })} isFiltered />);
    expect(screen.getByText("Filtreye uyan kayıt yok")).toBeInTheDocument();
  });

  it("hata durumunda hata kodu ve Tekrar dene gösterir", () => {
    const refetch = vi.fn();
    render(<ManagementList columns={columns} query={q({ isError: true, error: { status: 500, error: "INTERNAL_ERROR" }, refetch })} />);
    expect(screen.getByText("Veriler yüklenemedi")).toBeInTheDocument();
    expect(screen.getByText(/Hata kodu: 500/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /Tekrar dene/ }));
    expect(refetch).toHaveBeenCalled();
  });

  it("403'te yetki yok durumu gösterir", () => {
    render(<ManagementList columns={columns} query={q({ isError: true, error: { status: 403 } })} />);
    expect(screen.getByText("Bu sayfaya erişim yetkiniz yok")).toBeInTheDocument();
  });

  it("satırları, satır aksiyonlarını ve sayfalayıcıyı gösterir", () => {
    const onPage = vi.fn();
    const rows = [
      { id: 1, name: "Ada Yılmaz", phone: "555" },
      { id: 2, name: "Can Demir", phone: "556" },
    ];
    render(
      <ManagementList
        columns={columns}
        query={q({ data: page(rows, 45) })}
        page={0}
        size={20}
        onPage={onPage}
        rowActions={(r) => <button type="button">Düzenle {r.name}</button>}
      />
    );
    expect(screen.getByText("Ada Yılmaz")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Düzenle Can Demir" })).toBeInTheDocument();
    expect(screen.getByText("1–20 / 45")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button").find((b) => b.textContent === "2"));
    expect(onPage).toHaveBeenCalledWith({ page: 1, size: 20 });
  });

  it("yenilemede eski veriyi korur (isFetching + placeholder)", () => {
    render(<ManagementList columns={columns} query={q({ data: page([{ id: 1, name: "Ada", phone: "1" }]), isFetching: true, isPlaceholderData: true })} />);
    expect(screen.getByText("Ada")).toBeInTheDocument();
  });
});
