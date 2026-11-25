export const dailyServiceKeys = {
  all: () => ["daily-service"],
  byStudent: (studentId) => ["daily-service", Number(studentId)],
  entry: (studentId, date) => ["daily-service", Number(studentId), date],
};
