export const attendanceKeys = {
  history: (studentId, from, to) => ["attendance", Number(studentId), from, to],
  pendingCorrections: () => ["attendance-corrections", "mine", "PENDING"],
};
