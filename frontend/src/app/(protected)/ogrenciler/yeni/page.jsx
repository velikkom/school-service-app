import { redirect } from "next/navigation";

/** Eski adres — yeni karşılığına yönlenir. */
export default function Page() {
  redirect("/ogrenciler?yeni=1");
}
