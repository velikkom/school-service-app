import { redirect } from "next/navigation";

/** Eski tekil düzenleme sayfası — liste + düzenleme dialoguna yönlenir. */
export default async function Page({ params }) {
  const { id } = await params;
  redirect(`/hostesler?duzenle=${encodeURIComponent(id)}`);
}
