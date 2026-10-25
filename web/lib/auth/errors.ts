export type AuthErrorCode =
  | "invalid_request"
  | "invalid_input"
  | "invalid_password"
  | "invalid_code"
  | "code_expired"
  | "invalid_credentials"
  | "email_not_verified"
  | "password_reset_required"
  | "challenge_not_supported"
  | "session_expired"
  | "unauthenticated"
  | "not_found"
  | "csrf_rejected"
  | "unsupported_media_type"
  | "payload_too_large"
  | "rate_limited"
  | "auth_unavailable"
  | "backend_unavailable"
  | "backend_timeout";

export type AuthOperation =
  | "sign-up"
  | "confirm"
  | "resend"
  | "sign-in"
  | "refresh"
  | "forgot"
  | "reset"
  | "sign-out";

export type AuthFailure = { status: number; code: AuthErrorCode };

/** Error name used when Cognito returns a challenge this app does not implement. */
export const UNSUPPORTED_CHALLENGE_ERROR = "UnsupportedChallengeError";

export function errorName(error: unknown): string | undefined {
  return error instanceof Error ? error.name : undefined;
}

/**
 * Maps a Cognito error name to a generic, non-enumerating HTTP status and code.
 * Account-specific outcomes that must look identical to success are handled by isAccountSpecificFailure.
 */
export function mapCognitoError(name: string | undefined, operation: AuthOperation): AuthFailure {
  switch (name) {
    case "InvalidPasswordException":
      return { status: 400, code: "invalid_password" };
    case "InvalidParameterException":
      return { status: 400, code: "invalid_input" };
    case "CodeMismatchException":
      return { status: 400, code: "invalid_code" };
    case "ExpiredCodeException":
      return { status: 400, code: "code_expired" };
    case "UserNotConfirmedException":
      return operation === "sign-in"
        ? { status: 403, code: "email_not_verified" }
        : { status: 400, code: "invalid_request" };
    case "PasswordResetRequiredException":
      return { status: 403, code: "password_reset_required" };
    case UNSUPPORTED_CHALLENGE_ERROR:
      return { status: 403, code: "challenge_not_supported" };
    case "NotAuthorizedException":
      return operation === "refresh" || operation === "sign-out"
        ? { status: 401, code: "session_expired" }
        : { status: 401, code: "invalid_credentials" };
    case "UserNotFoundException":
      if (operation === "sign-in") {
        return { status: 401, code: "invalid_credentials" };
      }
      if (operation === "refresh") {
        return { status: 401, code: "session_expired" };
      }
      return { status: 400, code: "invalid_code" };
    case "LimitExceededException":
    case "TooManyRequestsException":
    case "TooManyFailedAttemptsException":
      return { status: 429, code: "rate_limited" };
    default:
      return { status: 503, code: "auth_unavailable" };
  }
}

/**
 * Failures that reveal whether an account exists or its state. Routes answer these with the same
 * response as success so that callers cannot enumerate accounts.
 */
export function isAccountSpecificFailure(name: string | undefined, operation: AuthOperation): boolean {
  switch (operation) {
    case "sign-up":
      return name === "UsernameExistsException";
    case "resend":
      return name === "UserNotFoundException" || name === "InvalidParameterException";
    case "forgot":
      return (
        name === "UserNotFoundException" ||
        name === "InvalidParameterException" ||
        name === "NotAuthorizedException" ||
        name === "UserNotConfirmedException"
      );
    default:
      return false;
  }
}
