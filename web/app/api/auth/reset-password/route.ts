import { NextResponse } from "next/server";
import { confirmForgotPassword } from "@/lib/auth/cognito";
import { authErrorResponse, authResponse, failureResponse, readJsonObject, rejectUnsafeRequest } from "@/lib/auth/http";
import { parseConfirmationCode, parseEmail, parsePassword } from "@/lib/auth/validation";

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
  const code = parseConfirmationCode(body.value.code);
  const password = parsePassword(body.value.password);
  if (!email || !code) {
    return authErrorResponse(400, "invalid_input");
  }
  if (!password) {
    return authErrorResponse(400, "invalid_password");
  }

  try {
    await confirmForgotPassword(email, code, password);
  } catch (error) {
    return failureResponse(error, "reset");
  }
  return authResponse({ status: "reset" });
}
