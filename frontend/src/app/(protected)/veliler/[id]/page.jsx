import ParentDetailScreen from "@/features/parents/components/ParentDetailScreen";

export default async function Page({ params }) {
  const { id } = await params;
  return <ParentDetailScreen id={id} />;
}
