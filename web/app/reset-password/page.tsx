import { AuthFlow, type AuthMode } from "@/app/auth/auth-flow";

export const metadata = { title: "Reset password · PritiMitra" };

export default function Page() {
  return <AuthFlow mode={"reset" as AuthMode} />;
}
