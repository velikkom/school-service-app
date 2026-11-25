export const enrollmentKeys = {
  all: () => ["enrollment"],
  state: (userId) => ["enrollment", "state", userId ?? "me"],
  schools: () => ["schools", "active-options"],
  provinces: () => ["address-lookup", "provinces"],
  districts: (provinceId) => ["address-lookup", "districts", provinceId],
  neighborhoods: (districtId) => ["address-lookup", "neighborhoods", districtId],
};
