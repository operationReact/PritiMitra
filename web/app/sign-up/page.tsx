import { AuthFlow, type AuthMode } from "@/app/auth/auth-flow";

export const metadata = { title: "Create account · PritiMitra" };

export default function Page() {
  return <AuthFlow mode={"sign-up" as AuthMode} />;
}
