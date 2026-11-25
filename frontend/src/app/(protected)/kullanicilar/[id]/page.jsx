import UserDetailScreen from "@/features/users/components/UserDetailScreen";

export default async function Page({ params }) {
  const { id } = await params;
  return <UserDetailScreen id={id} />;
}
