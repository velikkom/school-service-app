"use client";

import { useCallback, useMemo } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { useMyStudents } from "./useMyStudents";

/**
 * Veli ekranlarında seçili çocuk. Seçim `?child=<id>` ile adreslenir (Çocuklarım → Bugünkü servis
 * bağlantısı bu parametreyi kullanır); geçersiz/eksikse ilk aktif çocuk seçilir.
 * Bu hook `useSearchParams` kullandığı için çağıran ekran bir <Suspense> içinde olmalıdır.
 */
export function useSelectedChild() {
  const query = useMyStudents();
  const params = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const children = useMemo(() => (query.data ?? []).filter((s) => s.isActive !== false), [query.data]);
  const requested = Number(params.get("child"));
  const selected = children.find((s) => s.id === requested) ?? children[0] ?? null;

  const select = useCallback(
    (id) => {
      const next = new URLSearchParams(params.toString());
      next.set("child", String(id));
      router.replace(`${pathname}?${next.toString()}`, { scroll: false });
    },
    [params, pathname, router]
  );

  return { query, children, selected, select };
}
