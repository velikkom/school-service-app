/** Veli sorgu anahtarları. Bağ listeleri ["parent-links", "student"|"parent", id]. */
export const parentKeys = {
  all: ["parents"],
  list: () => ["parents", "list"],
  detail: () => ["parents", "detail"],
  links: (parentId) => ["parent-links", "parent", String(parentId)],
  lookup: ["lookup", "parents"],
};

export const PARENT_INVALIDATE = [["parents"], ["lookup", "parents"], ["lookup", "users"]];
