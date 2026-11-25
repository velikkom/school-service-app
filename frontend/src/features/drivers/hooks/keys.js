export const driverKeys = {
  all: ["drivers"],
  list: () => ["drivers", "list"],
  detail: () => ["drivers", "detail"],
  lookup: ["lookup", "drivers"],
};

export const DRIVER_INVALIDATE = [["drivers"], ["lookup", "drivers"], ["lookup", "users"], ["users"]];
