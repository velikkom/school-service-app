import TripDetailScreen from "@/features/trips/components/TripDetailScreen";

export default async function Page({ params }) {
  const { id } = await params;
  return <TripDetailScreen id={id} />;
}
