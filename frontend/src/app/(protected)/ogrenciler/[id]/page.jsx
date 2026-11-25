import StudentDetailScreen from "@/features/students/components/StudentDetailScreen";

export default async function Page({ params }) {
  const { id } = await params;
  return <StudentDetailScreen id={id} />;
}
