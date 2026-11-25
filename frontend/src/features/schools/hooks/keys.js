export const schoolKeys = {
  all: ["schools"],
  list: () => ["schools", "list"],
  detail: () => ["schools", "detail"],
  lookup: ["lookup", "schools"],
};

export const SCHOOL_INVALIDATE = [["schools"], ["lookup", "schools"]];
