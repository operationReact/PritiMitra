import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { authErrorResponse, authResponse, failureResponse, rejectUnsafeRequest, withSessionCookies } from "@/lib/auth/http";
import { clearSessionCookies, resolveFreshSession, type CookieWriter } from "@/lib/auth/session";

export async function POST(request: Request): Promise<NextResponse> {
  const rejected = rejectUnsafeRequest(request);
  if (rejected) {
    return rejected;
  }

  let session;
  try {
    session = await resolveFreshSession(await cookies(), { force: true });
  } catch (error) {
    return failureResponse(error, "refresh");
  }
  if (!session?.refreshed) {
    const response = authErrorResponse(401, "session_expired");
    clearSessionCookies(response.cookies as CookieWriter);
    return response;
  }
  return withSessionCookies(authResponse({ status: "refreshed" }), session.refreshed);
}
