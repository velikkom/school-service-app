/** Öğrenci sorgu anahtarları. Seçim listesi önbelleği ["lookup","students"] (diğer ekranlarla ortak). */
export const studentKeys = {
  all: ["students"],
  list: () => ["students", "list"],
  detail: () => ["students", "detail"],
  statement: (id) => ["students", "statement", String(id)],
  attendance: (id, from, to) => ["attendance", String(id), from ?? "", to ?? ""],
  links: (studentId) => ["parent-links", "student", String(studentId)],
  assignments: (studentId) => ["assignments", "student", String(studentId)],
  payments: (studentId) => ["payments", "student", String(studentId)],
  contracts: (studentId) => ["contracts", "student", String(studentId)],
  lookup: ["lookup", "students"],
};

export const STUDENT_INVALIDATE = [["students"], ["lookup", "students"]];
