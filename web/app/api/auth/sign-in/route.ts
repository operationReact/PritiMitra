import { NextResponse } from "next/server";
import { signIn } from "@/lib/auth/cognito";
import { authErrorResponse, authResponse, failureResponse, readJsonObject, rejectUnsafeRequest, withSessionCookies } from "@/lib/auth/http";
import { parseEmail, parsePassword } from "@/lib/auth/validation";

export async function POST(request: Request): Promise<NextResponse> {
  const rejected = rejectUnsafeRequest(request);
  if (rejected) {
    return rejected;
  }
  const body = await readJsonObject(request);
  if (!body.ok) {
    return body.response;
  }
  const email = parseEmail(body.value.email);
  const password = parsePassword(body.value.password);
  if (!email || !password) {
    return authErrorResponse(400, "invalid_input");
  }

  let tokens;
  try {
    tokens = await signIn(email, password);
  } catch (error) {
    return failureResponse(error, "sign-in");
  }
  return withSessionCookies(authResponse({ status: "signed_in" }), tokens);
}
