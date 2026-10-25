import { NextResponse } from "next/server";
import { signUp } from "@/lib/auth/cognito";
import { isAccountSpecificFailure, errorName } from "@/lib/auth/errors";
import { authErrorResponse, authResponse, failureResponse, readJsonObject, rejectUnsafeRequest } from "@/lib/auth/http";
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
  if (!email) {
    return authErrorResponse(400, "invalid_input");
  }
  const password = parsePassword(body.value.password);
  if (!password) {
    return authErrorResponse(400, "invalid_password");
  }

  try {
    await signUp(email, password);
  } catch (error) {
    // Existing accounts get the same response as new ones so sign-up cannot be used to probe for accounts.
    if (!isAccountSpecificFailure(errorName(error), "sign-up")) {
      return failureResponse(error, "sign-up");
    }
  }
  return authResponse({ status: "confirmation_required" }, 202);
}
