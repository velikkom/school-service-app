import RouteDetailScreen from "@/features/routes/components/RouteDetailScreen";

export default async function Page({ params }) {
  const { id } = await params;
  return <RouteDetailScreen id={id} />;
}
