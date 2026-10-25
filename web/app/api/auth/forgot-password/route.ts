import { NextResponse } from "next/server";
import { forgotPassword } from "@/lib/auth/cognito";
import { errorName, isAccountSpecificFailure } from "@/lib/auth/errors";
import { authErrorResponse, authResponse, failureResponse, readJsonObject, rejectUnsafeRequest } from "@/lib/auth/http";
import { parseEmail } from "@/lib/auth/validation";

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
  if (!email) {
    return authErrorResponse(400, "invalid_input");
  }

  try {
    await forgotPassword(email);
  } catch (error) {
    // Same response whether or not the account exists.
    if (!isAccountSpecificFailure(errorName(error), "forgot")) {
      return failureResponse(error, "forgot");
    }
  }
  return authResponse({ status: "sent" }, 202);
}
