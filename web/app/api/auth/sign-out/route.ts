import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { globalSignOut } from "@/lib/auth/cognito";
import { errorName } from "@/lib/auth/errors";
import { authResponse, rejectUnsafeRequest } from "@/lib/auth/http";
import { clearSessionCookies, resolveFreshSession, type CookieWriter } from "@/lib/auth/session";

export async function POST(request: Request): Promise<NextResponse> {
  const rejected = rejectUnsafeRequest(request);
  if (rejected) {
    return rejected;
  }

  try {
    // Refreshing first makes sure GlobalSignOut is not rejected because the access token has expired.
    const session = await resolveFreshSession(await cookies());
    if (session) {
      await globalSignOut(session.accessToken);
    }
  } catch (error) {
    // Cookies are cleared regardless, so the browser session always ends even if revocation fails.
    console.warn(`[auth] sign-out: revocation failed (${errorName(error) ?? "unknown"})`);
  }

  const response = authResponse({ status: "signed_out" });
  clearSessionCookies(response.cookies as CookieWriter);
  return response;
}
