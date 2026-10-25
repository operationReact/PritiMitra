import { describe, expect, it } from "vitest";
import { isAccountSpecificFailure, mapCognitoError, UNSUPPORTED_CHALLENGE_ERROR } from "./errors";

describe("mapCognitoError", () => {
  it("maps credential failures to a generic invalid_credentials code", () => {
    expect(mapCognitoError("NotAuthorizedException", "sign-in")).toEqual({ status: 401, code: "invalid_credentials" });
    expect(mapCognitoError("UserNotFoundException", "sign-in")).toEqual({ status: 401, code: "invalid_credentials" });
  });

  it("maps refresh and sign-out rejections to session_expired", () => {
    expect(mapCognitoError("NotAuthorizedException", "refresh")).toEqual({ status: 401, code: "session_expired" });
    expect(mapCognitoError("NotAuthorizedException", "sign-out")).toEqual({ status: 401, code: "session_expired" });
  });

  it("reports unconfirmed accounts only for sign-in", () => {
    expect(mapCognitoError("UserNotConfirmedException", "sign-in")).toEqual({
      status: 403,
      code: "email_not_verified",
    });
    expect(mapCognitoError("UserNotConfirmedException", "reset")).toEqual({ status: 400, code: "invalid_request" });
  });

  it("maps code and password errors", () => {
    expect(mapCognitoError("CodeMismatchException", "confirm")).toEqual({ status: 400, code: "invalid_code" });
    expect(mapCognitoError("ExpiredCodeException", "reset")).toEqual({ status: 400, code: "code_expired" });
    expect(mapCognitoError("InvalidPasswordException", "sign-up")).toEqual({ status: 400, code: "invalid_password" });
  });

  it("maps rate limit errors to 429", () => {
    expect(mapCognitoError("TooManyRequestsException", "sign-in")).toEqual({ status: 429, code: "rate_limited" });
    expect(mapCognitoError("LimitExceededException", "forgot")).toEqual({ status: 429, code: "rate_limited" });
  });

  it("maps unsupported challenges and unknown errors without leaking details", () => {
    expect(mapCognitoError(UNSUPPORTED_CHALLENGE_ERROR, "sign-in")).toEqual({
      status: 403,
      code: "challenge_not_supported",
    });
    expect(mapCognitoError("SomeInternalException", "sign-in")).toEqual({ status: 503, code: "auth_unavailable" });
    expect(mapCognitoError(undefined, "sign-in")).toEqual({ status: 503, code: "auth_unavailable" });
  });
});

describe("isAccountSpecificFailure", () => {
  it("treats existing-account sign-up as a success-equivalent outcome", () => {
    expect(isAccountSpecificFailure("UsernameExistsException", "sign-up")).toBe(true);
    expect(isAccountSpecificFailure("InvalidPasswordException", "sign-up")).toBe(false);
  });

  it("hides account state for resend and forgot-password", () => {
    expect(isAccountSpecificFailure("UserNotFoundException", "resend")).toBe(true);
    expect(isAccountSpecificFailure("UserNotFoundException", "forgot")).toBe(true);
    expect(isAccountSpecificFailure("UserNotConfirmedException", "forgot")).toBe(true);
    expect(isAccountSpecificFailure("LimitExceededException", "forgot")).toBe(false);
  });
});
