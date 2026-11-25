/** Sefer sorgu anahtarları — kapsamı (id/tarih/filtre) içerir. */
export const tripKeys = {
  all: () => ["trips"],
  lists: () => ["trips", "list"],
  list: (params) => ["trips", "list", params],
  mine: (date) => ["trips", "mine", date],
  detail: (id) => ["trips", "detail", Number(id)],
  roster: (id) => ["trips", "roster", Number(id)],
  incidents: (id) => ["trips", "incidents", Number(id)],
  notices: (id) => ["trips", "absence-notices", Number(id)],
  routeStops: (routeId) => ["routes", "stops", Number(routeId)],
  lookups: (kind) => ["trips", "lookups", kind],
  myVehicle: () => ["vehicles", "mine"],
};

/** Başka feature'ların anahtarları (yalnız invalidation için, değerleri sabit). */
export const externalKeys = {
  latestLocation: (tripId) => ["tracking", "latest", Number(tripId)],
  corrections: () => ["attendance-corrections"],
  pendingCounts: () => ["pending-counts"],
  dailyService: () => ["daily-service"],
  attendance: () => ["attendance"],
};
