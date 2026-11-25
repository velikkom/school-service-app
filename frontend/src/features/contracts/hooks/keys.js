export const contractKeys = {
  all: ["contracts"],
  list: () => ["contracts", "list"],
  detail: () => ["contracts", "detail"],
};

/** Sözleşme değişimi ekstreyi ve bekleyen sayaçları etkileyebilir. */
export const CONTRACT_INVALIDATE = [["contracts"], ["students", "statement"], ["pending-counts"]];
