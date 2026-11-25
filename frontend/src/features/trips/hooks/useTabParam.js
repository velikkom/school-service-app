"use client";

import { usePathname, useRouter, useSearchParams } from "next/navigation";

/** `?tab=` ile adreslenen sekme. Bilinmeyen değer varsayılana düşer. */
export function useTabParam(allowed, fallback) {
  const router = useRouter();
  const pathname = usePathname();
  const search = useSearchParams();
  const raw = search.get("tab");
  const tab = allowed.includes(raw) ? raw : fallback;
  const setTab = (next) => {
    const sp = new URLSearchParams(search.toString());
    sp.set("tab", next);
    router.replace(`${pathname}?${sp.toString()}`, { scroll: false });
  };
  return [tab, setTab];
}
