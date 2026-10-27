import { AuthFlow, type AuthMode } from "@/app/auth/auth-flow";

export const metadata = { title: "Recover your account · PritiMitra" };

export default function Page() {
  return <AuthFlow mode={"forgot" as AuthMode} />;
}
