/** Aile (PARENT/STUDENT) ekranlarının sorgu anahtarları. Rol değişiminde önbellek AuthProvider'da temizlenir. */
export const familyKeys = {
  myStudents: () => ["students", "mine"],
  contracts: (studentId) => ["contracts", "by-student", Number(studentId)],
  allContracts: () => ["contracts", "mine"],
  pendingContracts: () => ["contracts", "mine", "PENDING_APPROVAL"],
  charges: (studentId) => ["billing", "charges", Number(studentId)],
  payments: (studentId) => ["payments", "by-student", Number(studentId)],
  statement: (studentId) => ["statement", Number(studentId)],
  assignments: (studentId) => ["assignments", "mine", studentId == null ? "all" : Number(studentId)],
  corrections: () => ["attendance-corrections", "mine", "all"],
};
