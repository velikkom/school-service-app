import ScreenState from "@/components/feedback/ScreenState";

export default function ProtectedNotFound() {
  return <ScreenState kind="notFound" title="Sayfa bulunamadı" message="Adres hatalı olabilir ya da sayfa taşınmış olabilir." homeHref="/dashboard" />;
}
