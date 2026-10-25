import { beforeEach, describe, expect, it, vi } from "vitest";

const cognito = vi.hoisted(() => ({ refreshTokens: vi.fn() }));

vi.mock("./cognito", () => ({ refreshTokens: cognito.refreshTokens }));

import { ACCESS_COOKIE_NAME, REFRESH_COOKIE_NAME } from "./cookies";
import {
  clearSessionCookies,
  resolveFreshSession,
  setSessionCookies,
  type CookieReader,
  type CookieWriter,
} from "./session";

function reader(values: Record<string, string>): CookieReader {
  return {
    get: (name) => (values[name] === undefined ? undefined : { value: values[name] }),
  };
}

function recordingWriter() {
  const calls: Array<{ name: string; value: string; options: Record<string, unknown> }> = [];
  const target: CookieWriter = {
    set: (name, value, options) => {
      calls.push({ name, value, options: options as unknown as Record<string, unknown> });
      return undefined;
    },
  };
  return { target, calls };
}

function rejection(name: string): Error {
  const error = new Error(name);
  error.name = name;
  return error;
}

describe("resolveFreshSession", () => {
  beforeEach(() => {
    cognito.refreshTokens.mockReset();
  });

  it("returns null without calling Cognito when refresh is not allowed", async () => {
    const result = await resolveFreshSession(reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }), {
      force: true,
      allowRefresh: false,
    });

    expect(result).toBeNull();
    expect(cognito.refreshTokens).not.toHaveBeenCalled();
  });

  it("returns the rotated refresh token so the caller can persist it", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({
      accessToken: "access-2",
      refreshToken: "refresh-2",
      expiresIn: 3600,
    });

    const result = await resolveFreshSession(reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }), { force: true });

    expect(cognito.refreshTokens).toHaveBeenCalledWith("refresh-1");
    expect(result).toEqual({
      accessToken: "access-2",
      refreshed: { accessToken: "access-2", refreshToken: "refresh-2", expiresIn: 3600 },
    });
  });

  it("returns null when Cognito rejects the refresh token", async () => {
    cognito.refreshTokens.mockRejectedValueOnce(rejection("NotAuthorizedException"));

    const result = await resolveFreshSession(reader({ [REFRESH_COOKIE_NAME]: "revoked" }), { force: true });

    expect(result).toBeNull();
  });

  it("rethrows transient failures so existing cookies are kept", async () => {
    cognito.refreshTokens.mockRejectedValueOnce(rejection("TimeoutError"));

    await expect(resolveFreshSession(reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }), { force: true })).rejects.toThrow(
      "TimeoutError",
    );
  });

  it("returns null without a refresh token", async () => {
    const result = await resolveFreshSession(reader({}), { force: true });

    expect(result).toBeNull();
    expect(cognito.refreshTokens).not.toHaveBeenCalled();
  });
});

describe("session cookies", () => {
  it("writes HttpOnly access and refresh cookies, with the rotated refresh token", () => {
    const { target, calls } = recordingWriter();

    setSessionCookies(target, { accessToken: "access-2", refreshToken: "refresh-2", expiresIn: 3600 }, true);

    const access = calls.find((call) => call.name === ACCESS_COOKIE_NAME);
    const refresh = calls.find((call) => call.name === REFRESH_COOKIE_NAME);
    expect(access?.value).toBe("access-2");
    expect(refresh?.value).toBe("refresh-2");
    expect(access?.options).toMatchObject({ httpOnly: true, secure: true });
    expect(refresh?.options).toMatchObject({ httpOnly: true, secure: true });
  });

  it("does not mark cookies Secure outside production", () => {
    const { target, calls } = recordingWriter();

    setSessionCookies(target, { accessToken: "a", refreshToken: "r" }, false);

    for (const call of calls) {
      expect(call.options.secure).toBe(false);
      expect(call.options.httpOnly).toBe(true);
    }
  });

  it("clears both cookies", () => {
    const { target, calls } = recordingWriter();

    clearSessionCookies(target, true);

    expect(calls.map((call) => [call.name, call.value])).toEqual([
      [ACCESS_COOKIE_NAME, ""],
      [REFRESH_COOKIE_NAME, ""],
    ]);
  });
});
