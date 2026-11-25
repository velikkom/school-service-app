"use client";

import { useSyncExternalStore } from "react";

const QUERY = "(max-width: 899.98px)";

function subscribe(cb) {
  const mq = window.matchMedia(QUERY);
  mq.addEventListener("change", cb);
  return () => mq.removeEventListener("change", cb);
}

/** < 900 px kırılımı (DESIGN_SYSTEM.md §8). Sunucuda false. */
export function useIsMobile() {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(QUERY).matches,
    () => false
  );
}
