export const hostessKeys = {
  all: ["hostesses"],
  list: () => ["hostesses", "list"],
  detail: () => ["hostesses", "detail"],
  lookup: ["lookup", "hostesses"],
};

export const HOSTESS_INVALIDATE = [["hostesses"], ["lookup", "hostesses"], ["lookup", "users"], ["users"]];
