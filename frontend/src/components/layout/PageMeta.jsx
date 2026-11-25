"use client";

import { createContext, useContext, useEffect, useState } from "react";

const PageMetaContext = createContext({ meta: {}, setMeta: () => {} });

export function PageMetaProvider({ children }) {
  const [meta, setMeta] = useState({});
  return <PageMetaContext.Provider value={{ meta, setMeta }}>{children}</PageMetaContext.Provider>;
}

export function usePageMetaValue() {
  return useContext(PageMetaContext).meta;
}

/**
 * Sayfanın kabuk başlığını ayarlar (masaüstü üst çubuk + mobil üst çubuk).
 * <PageMeta title="Seferler" subtitle="18.11.2026" back="/seferler" actions={<Button/>} />
 * `actions` masaüstünde üst çubukta, mobilde içerik başında gösterilir.
 */
export default function PageMeta({ title, subtitle, back, crumb }) {
  const { setMeta } = useContext(PageMetaContext);
  useEffect(() => {
    setMeta({ title, subtitle, back, crumb });
    if (title) document.title = `${title} · ServisFlow`;
  }, [title, subtitle, back, crumb, setMeta]);
  useEffect(() => () => setMeta({}), [setMeta]);
  return null;
}
