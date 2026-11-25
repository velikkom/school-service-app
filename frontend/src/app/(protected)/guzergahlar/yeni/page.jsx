import { redirect } from "next/navigation";

/** Eski adres — yeni karşılığına yönlenir. */
export default function Page() {
  redirect("/guzergahlar?yeni=1");
}
