import { AuthFlow, type AuthMode } from "@/app/auth/auth-flow";

export const metadata = { title: "Sign in · PritiMitra" };

export default function Page() {
  return <AuthFlow mode={"sign-in" as AuthMode} />;
}
