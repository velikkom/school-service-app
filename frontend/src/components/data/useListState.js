"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { toList } from "@/lib/http/page";

/** Yazarken her tuşta istek atmamak için gecikmeli değer. */
export function useDebouncedValue(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(t);
  }, [value, delay]);
  return debounced;
}

/**
 * Liste filtre + sayfa durumu. Filtre değişince sayfa 0'a döner.
 * `params` doğrudan service.list(params)'e verilir (boş değerleri toQueryString atar).
 */
export function useListState(initialFilters = {}, initialSize = 20) {
  const [filters, setFilters] = useState(initialFilters);
  const [paging, setPaging] = useState({ page: 0, size: initialSize });

  const setFilter = useCallback((name, value) => {
    setFilters((f) => ({ ...f, [name]: value }));
    setPaging((p) => ({ ...p, page: 0 }));
  }, []);

  const params = useMemo(() => ({ ...filters, page: paging.page, size: paging.size }), [filters, paging]);

  return {
    filters,
    setFilter,
    page: paging.page,
    size: paging.size,
    onPage: setPaging,
    params,
    isFiltered: Object.values(filters).some((v) => v !== "" && v !== null && v !== undefined),
  };
}

/** Sayfalı liste sorgusu; yenilemede/sayfa geçişinde eski veri korunur (keepPreviousData). */
export function useListQuery(queryKey, fetcher, params, options = {}) {
  return useQuery({
    queryKey: [...queryKey, params],
    queryFn: () => fetcher(params),
    placeholderData: keepPreviousData,
    ...options,
  });
}

/**
 * Seçim listeleri için hafif sorgu (size=100). Aynı anahtar farklı ekranlarda önbelleği paylaşır.
 * Dönen: { items, byId: Map, options: [{value,label}], query }
 */
export function useLookup(queryKey, fetcher, { label, filter, enabled = true } = {}) {
  const query = useQuery({
    queryKey: ["lookup", ...queryKey],
    queryFn: fetcher,
    staleTime: 60_000,
    enabled,
  });
  const items = useMemo(() => toList(query.data), [query.data]);
  const byId = useMemo(() => new Map(items.map((x) => [String(x.id), x])), [items]);
  const options = useMemo(
    () => (label ? items.filter((x) => (filter ? filter(x) : true)).map((x) => ({ value: String(x.id), label: label(x) })) : []),
    // label/filter fonksiyonları her render'da yeni olabilir; içerik items'a bağlı.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [items]
  );
  return { items, byId, options, query };
}

/**
 * `?yeni=1` → yeni kayıt dialogu, `?duzenle={id}` → düzenleme dialogu (`&ogrenci={id}` ön seçim). Dialog açık/kapalı durumu
 * URL'de tutulur; kapatınca parametreler silinir (geçmişe kayıt eklenmez).
 */
export function useDialogParams() {
  const sp = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();

  const update = useCallback(
    (mutate) => {
      const next = new URLSearchParams(sp?.toString() ?? "");
      mutate(next);
      const s = next.toString();
      router.replace(s ? `${pathname}?${s}` : pathname, { scroll: false });
    },
    [sp, router, pathname]
  );

  return {
    isNew: sp?.get("yeni") === "1",
    editId: sp?.get("duzenle") || null,
    openNew: () =>
      update((p) => {
        p.set("yeni", "1");
        p.delete("duzenle");
      }),
    openEdit: (id) =>
      update((p) => {
        p.set("duzenle", String(id));
        p.delete("yeni");
      }),
    close: () =>
      update((p) => {
        p.delete("yeni");
        p.delete("duzenle");
        p.delete("ogrenci"); // ön seçim parametresi (ör. /atamalar?yeni=1&ogrenci=5)
      }),
    get: (name) => sp?.get(name) ?? null,
    setParam: (name, value) =>
      update((p) => {
        if (value === null || value === undefined || value === "") p.delete(name);
        else p.set(name, String(value));
      }),
  };
}

/** Form değerinden API gövdesine: boş string → undefined (PATCH'te "değişmez"), trim. */
export function emptyToUndefined(value) {
  if (value === null || value === undefined) return undefined;
  if (typeof value === "string") {
    const t = value.trim();
    return t === "" ? undefined : t;
  }
  return value;
}
