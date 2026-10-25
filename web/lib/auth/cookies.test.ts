import { describe, expect, it } from "vitest";
import {
  accessTokenMaxAge,
  buildClearedCookieOptions,
  buildSessionCookieOptions,
  REFRESH_COOKIE_MAX_AGE_SECONDS,
  SESSION_COOKIE_PATH,
} from "./cookies";

describe("buildSessionCookieOptions", () => {
  it("is HttpOnly, SameSite=Lax and scoped to /api", () => {
    const options = buildSessionCookieOptions({ isProduction: false, maxAge: 300 });
    expect(options).toEqual({ httpOnly: true, secure: false, sameSite: "lax", path: "/api", maxAge: 300 });
    expect(SESSION_COOKIE_PATH).toBe("/api");
  });

  it("sets Secure in production", () => {
    expect(buildSessionCookieOptions({ isProduction: true, maxAge: 300 }).secure).toBe(true);
  });
});

describe("buildClearedCookieOptions", () => {
  it("expires the cookie immediately with the same security attributes", () => {
    expect(buildClearedCookieOptions(true)).toEqual({
      httpOnly: true,
      secure: true,
      sameSite: "lax",
      path: "/api",
      maxAge: 0,
    });
  });
});

describe("accessTokenMaxAge", () => {
  it("uses the token lifetime within the bounded window", () => {
    expect(accessTokenMaxAge(900)).toBe(900);
  });

  it("clamps to 60 seconds minimum and one hour maximum", () => {
    expect(accessTokenMaxAge(5)).toBe(60);
    expect(accessTokenMaxAge(86_400)).toBe(3600);
  });

  it("falls back to one hour when the lifetime is unknown", () => {
    expect(accessTokenMaxAge(undefined)).toBe(3600);
    expect(accessTokenMaxAge(Number.NaN)).toBe(3600);
  });

  it("keeps the refresh cookie at 30 days", () => {
    expect(REFRESH_COOKIE_MAX_AGE_SECONDS).toBe(2_592_000);
  });
});
