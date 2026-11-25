/** Talep kuyrukları sorgu anahtarları (kapsam: filtre + sayfa). */
export const requestKeys = {
  stopChangesAll: () => ["stop-change-requests"],
  stopChanges: (params) => ["stop-change-requests", "list", params],
  contractsAll: () => ["contracts"],
  enrollments: (params) => ["contracts", "list", { status: "PENDING_APPROVAL", ...params }],
  settings: () => ["settings"],
  student: (id) => ["students", "detail", Number(id)],
  studentLinks: (studentId) => ["parent-student-links", { studentId: Number(studentId) }],
  parent: (id) => ["parents", "detail", Number(id)],
};
