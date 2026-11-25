export const userKeys = {
  all: ["users"],
  list: () => ["users", "list"],
  detail: () => ["users", "detail"],
  lookup: (role) => ["lookup", "users", role ?? "all"],
};

/** Kullanıcı değişince profil listeleri de etkilenebilir (rol ensure). */
export const USER_INVALIDATE = [["users"], ["lookup", "users"], ["drivers"], ["hostesses"], ["parents"], ["lookup", "drivers"], ["lookup", "hostesses"], ["lookup", "parents"]];
