/** "1250.00" | 1250 → "1.250,00 TL" (TRY, tr-TR) */
export function formatMoney(value) {
  if (value === null || value === undefined || value === "") return "—";
  const n = typeof value === "string" ? Number(value) : value;
  if (Number.isNaN(n)) return "—";
  return `${new Intl.NumberFormat("tr-TR", { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)} TL`;
}

/** Baş harfler (Türkçe büyük harf kuralıyla). */
export function initials(name) {
  if (!name) return "?";
  return String(name)
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0])
    .join("")
    .toLocaleUpperCase("tr");
}
