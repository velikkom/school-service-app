export const stopChangeKeys = {
  all: () => ["stop-change-requests"],
  mine: () => ["stop-change-requests", "mine"],
  options: (studentId, direction) => ["stop-change-requests", "options", Number(studentId), direction],
};
