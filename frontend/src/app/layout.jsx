import { Figtree, IBM_Plex_Mono } from "next/font/google";
import "primereact/resources/themes/lara-light-teal/theme.css";
import "primeicons/primeicons.css";
import "@/styles/globals.scss";
import AppProviders from "@/providers/AppProviders";

const figtree = Figtree({ subsets: ["latin", "latin-ext"], weight: ["400", "500", "600", "700", "800"], variable: "--font-figtree", display: "swap" });
const plexMono = IBM_Plex_Mono({ subsets: ["latin", "latin-ext"], weight: ["500", "600", "700"], variable: "--font-plex-mono", display: "swap" });

export const metadata = {
  title: { default: "ServisFlow", template: "%s · ServisFlow" },
  description: "Okul servisi operasyon, yoklama, veli ve finans yönetimi.",
};

export const viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#15202b",
};

export default function RootLayout({ children }) {
  return (
    <html lang="tr" className={`${figtree.variable} ${plexMono.variable}`}>
      <body>
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
