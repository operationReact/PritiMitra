import { describe, expect, it } from "vitest";
import { accountGuardDecision, parseMeResponse } from "./account";
import { parseConfirmationCode, parseEmail, parsePassword } from "./validation";
import { needsRefresh, readUnverifiedExpiry } from "./tokens";
import { readBackendOrigin, readCognitoEnv, AuthConfigError } from "./config";

function fakeJwt(payload: Record<string, unknown>): string {
  const encode = (value: object) => Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none" })}.${encode(payload)}.signature`;
}

describe("tokens", () => {
  it("reads exp without verifying the signature", () => {
    expect(readUnverifiedExpiry(fakeJwt({ exp: 1_000 }))).toBe(1_000);
    expect(readUnverifiedExpiry("not-a-jwt")).toBeUndefined();
  });

  it("requests a refresh when missing, expired, or inside the skew window", () => {
    const now = 1_000;
    expect(needsRefresh(undefined, now)).toBe(true);
    expect(needsRefresh(fakeJwt({ exp: now + 30 }), now)).toBe(true);
    expect(needsRefresh(fakeJwt({ exp: now + 600 }), now)).toBe(false);
  });
});

describe("validation", () => {
  it("normalises and checks email", () => {
    expect(parseEmail("  user@example.com ")).toBe("user@example.com");
    expect(parseEmail("no-at-sign")).toBeNull();
    expect(parseEmail(42)).toBeNull();
  });

  it("bounds password length without applying extra policy", () => {
    expect(parsePassword("correct horse")).toBe("correct horse");
    expect(parsePassword("")).toBeNull();
    expect(parsePassword("x".repeat(257))).toBeNull();
  });

  it("requires a six digit confirmation code", () => {
    expect(parseConfirmationCode("123456")).toBe("123456");
    expect(parseConfirmationCode("12345")).toBeNull();
    expect(parseConfirmationCode("12a456")).toBeNull();
  });
});

describe("account", () => {
  it("parses a valid /me response", () => {
    expect(parseMeResponse({ id: "u1", status: "ACTIVE", displayName: null })).toEqual({
      id: "u1",
      status: "ACTIVE",
      displayName: null,
    });
  });

  it("rejects unknown statuses and malformed bodies", () => {
    expect(parseMeResponse({ id: "u1", status: "ADMIN", displayName: null })).toBeNull();
    expect(parseMeResponse({ id: "u1", status: "ACTIVE", displayName: 5 })).toBeNull();
    expect(parseMeResponse(null)).toBeNull();
  });

  it("routes each status to the matching step", () => {
    expect(accountGuardDecision("ACTIVE")).toBe("continue");
    expect(accountGuardDecision("PENDING_EMAIL_VERIFICATION")).toBe("verify-email");
    expect(accountGuardDecision("ONBOARDING_INCOMPLETE")).toBe("onboarding");
    expect(accountGuardDecision("SUSPENDED")).toBe("restricted");
    expect(accountGuardDecision("INELIGIBLE")).toBe("restricted");
  });
});

describe("config", () => {
  it("requires region and client id", () => {
    expect(() => readCognitoEnv({})).toThrow(AuthConfigError);
    expect(readCognitoEnv({ COGNITO_REGION: "eu-west-1", COGNITO_CLIENT_ID: "abc" })).toEqual({
      region: "eu-west-1",
      clientId: "abc",
    });
  });

  it("requires https for the backend origin in production only", () => {
    expect(() => readBackendOrigin({ NODE_ENV: "production", BACKEND_API_BASE_URL: "http://api.internal" })).toThrow(
      AuthConfigError,
    );
    expect(readBackendOrigin({ NODE_ENV: "production", BACKEND_API_BASE_URL: "https://api.example/" })).toBe(
      "https://api.example",
    );
    expect(readBackendOrigin({ NODE_ENV: "development", BACKEND_API_BASE_URL: "http://localhost:8080" })).toBe(
      "http://localhost:8080",
    );
  });

  it("rejects missing or non-URL backend configuration", () => {
    expect(() => readBackendOrigin({})).toThrow(AuthConfigError);
    expect(() => readBackendOrigin({ BACKEND_API_BASE_URL: "ftp://api" })).toThrow(AuthConfigError);
  });
});
