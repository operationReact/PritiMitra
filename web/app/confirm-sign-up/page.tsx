import { AuthFlow, type AuthMode } from "@/app/auth/auth-flow";

export const metadata = { title: "Verify email · PritiMitra" };

export default function Page() {
  return <AuthFlow mode={"confirm" as AuthMode} />;
}
