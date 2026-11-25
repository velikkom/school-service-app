"use client";

import { useState } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { PrimeReactProvider, addLocale } from "primereact/api";
import AuthProvider from "@/providers/AuthProvider";
import { FeedbackHost } from "@/components/feedback/feedback";

addLocale("tr", {
  firstDayOfWeek: 1,
  dayNames: ["Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"],
  dayNamesShort: ["Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"],
  dayNamesMin: ["Pz", "Pt", "Sa", "Ça", "Pe", "Cu", "Ct"],
  monthNames: ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"],
  monthNamesShort: ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"],
  today: "Bugün",
  clear: "Temizle",
  accept: "Evet",
  reject: "Hayır",
  choose: "Seç",
  upload: "Yükle",
  cancel: "Vazgeç",
  emptyMessage: "Sonuç yok",
  emptyFilterMessage: "Sonuç bulunamadı",
  weekHeader: "Hf",
  dateFormat: "dd.mm.yy",
  aria: {
    close: "Kapat",
    previous: "Önceki",
    next: "Sonraki",
    navigation: "Gezinme",
    firstPageLabel: "İlk sayfa",
    lastPageLabel: "Son sayfa",
    nextPageLabel: "Sonraki sayfa",
    previousPageLabel: "Önceki sayfa",
    rowsPerPageLabel: "Sayfa başına satır",
    pageLabel: "Sayfa {page}",
    selectAll: "Tümünü seç",
    unselectAll: "Seçimi kaldır",
    showMask: "Şifreyi göster",
    hideMask: "Şifreyi gizle",
    prevYear: "Önceki yıl",
    nextYear: "Sonraki yıl",
    prevMonth: "Önceki ay",
    nextMonth: "Sonraki ay",
    chooseDate: "Tarih seç",
    chooseYear: "Yıl seç",
    chooseMonth: "Ay seç",
  },
});

const primeValue = { locale: "tr", ripple: false, inputStyle: "outlined" };

/** İstemci sağlayıcıları: TanStack Query → PrimeReact → oturum. */
export default function AppProviders({ children }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            retry: (count, err) => (err?.status && err.status < 500 ? false : count < 1),
            refetchOnWindowFocus: true,
            staleTime: 20_000,
          },
          mutations: { retry: 0 },
        },
      })
  );

  return (
    <QueryClientProvider client={client}>
      <PrimeReactProvider value={primeValue}>
        <AuthProvider>{children}</AuthProvider>
        <FeedbackHost />
      </PrimeReactProvider>
    </QueryClientProvider>
  );
}
