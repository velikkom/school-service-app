import ProtectedShell from "@/components/layout/ProtectedShell";

/** Oturum gerektiren tüm sayfalar: rol kabuğu + doğrudan URL erişim kontrolü. */
export default function ProtectedLayout({ children }) {
  return <ProtectedShell>{children}</ProtectedShell>;
}
