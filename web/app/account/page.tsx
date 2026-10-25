import { cookies } from "next/headers";
import { Suspense } from "react";
import { callBackend } from "@/lib/auth/backend-proxy";
import { accountGuardDecision, parseMeResponse } from "@/lib/auth/account";

export const metadata = {
  title: "Account",
};

export default function AccountPage() {
  return (
    <main>
      <h1>Account</h1>
      <Suspense fallback={<p>Checking your account…</p>}>
        <AccountStatus />
      </Suspense>
    </main>
  );
}

async function AccountStatus() {
  let result;
  try {
    result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      read: await cookies(),
      // Server Components cannot write cookies, so a rotation here would be lost.
      canRefresh: false,
    });
  } catch {
    return <p role="alert">Your account is temporarily unavailable.</p>;
  }

  if (result.kind === "unavailable") {
    return <p role="alert">We could not reach your account service. Please try again.</p>;
  }
  if (result.kind !== "response" || result.status !== 200) {
    return <p>Please sign in to continue.</p>;
  }

  let parsed;
  try {
    parsed = parseMeResponse(JSON.parse(result.body));
  } catch {
    parsed = null;
  }
  if (!parsed) {
    return <p role="alert">Your account is temporarily unavailable.</p>;
  }

  switch (accountGuardDecision(parsed.status)) {
    case "continue":
      return <p>Welcome back{parsed.displayName ? `, ${parsed.displayName}` : ""}.</p>;
    case "verify-email":
      return <p>Please verify your email address to continue.</p>;
    case "onboarding":
      return <p>Please finish setting up your profile to continue.</p>;
    case "restricted":
      return <p>This account cannot access PritiMitra right now.</p>;
  }
}
