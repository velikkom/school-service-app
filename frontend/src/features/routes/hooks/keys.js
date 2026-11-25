export const routeKeys = {
  all: ["routes"],
  list: () => ["routes", "list"],
  detail: () => ["routes", "detail"],
  stops: (routeId) => ["routes", "stops", String(routeId)],
  lookup: ["lookup", "routes"],
};

export const ROUTE_INVALIDATE = [["routes"], ["lookup", "routes"]];
