import { NextResponse } from "next/server";
import { confirmSignUp } from "@/lib/auth/cognito";
import { authErrorResponse, authResponse, failureResponse, readJsonObject, rejectUnsafeRequest } from "@/lib/auth/http";
import { parseConfirmationCode, parseEmail } from "@/lib/auth/validation";

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
  if (!email || !code) {
    return authErrorResponse(400, "invalid_input");
  }

  try {
    await confirmSignUp(email, code);
  } catch (error) {
    return failureResponse(error, "confirm");
  }
  return authResponse({ status: "confirmed" });
}
