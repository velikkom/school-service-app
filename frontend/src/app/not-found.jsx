import Link from "next/link";

export const metadata = { title: "Sayfa bulunamadı" };

export default function NotFound() {
  return (
    <main
      style={{
        minHeight: "100vh",
        display: "grid",
        placeItems: "center",
        padding: 16,
        textAlign: "center",
      }}
    >
      <div
        style={{
          display: "flex",
          flexDirection: "column",
          gap: 12,
          alignItems: "center",
        }}
      >
        <i
          className="pi pi-search"
          style={{ fontSize: 32, color: "var(--ink-3)" }}
          aria-hidden="true"
        />
        <h1 style={{ fontSize: 24 }}>Sayfa bulunamadı</h1>
        <p style={{ color: "var(--ink-3)" }}>
          Adres hatalı olabilir ya da sayfa taşınmış olabilir.
        </p>
        <Link href="/dashboard" className="p-button p-button-secondary">
          Ana sayfaya dön
        </Link>
      </div>
    </main>
  );
}
