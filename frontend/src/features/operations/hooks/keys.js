/**
 * Günlük Operasyon sorgu anahtarları. Sefer/yoklama/olay anahtarları trips alanının biçimini kullanır
 * (["trips", ...]) — böylece sefer detayındaki mutasyonların invalidation'ı bu ekranı da tazeler.
 */
export const operationKeys = {
  summary: (date) => ["reports", "daily-summary", date],
  trips: (date) => ["trips", "list", { businessDate: date, size: 100 }],
  roster: (tripId) => ["trips", "roster", Number(tripId)],
  incidents: (tripId) => ["trips", "incidents", Number(tripId)],
};
