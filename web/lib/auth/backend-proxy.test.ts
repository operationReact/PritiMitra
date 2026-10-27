import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const cognito = vi.hoisted(() => ({ refreshTokens: vi.fn() }));

vi.mock("./cognito", () => ({ refreshTokens: cognito.refreshTokens }));

import { callBackend } from "./backend-proxy";
import { ACCESS_COOKIE_NAME, REFRESH_COOKIE_NAME } from "./cookies";
import type { CookieReader } from "./session";

function reader(values: Record<string, string>): CookieReader {
  return {
    get: (name) => (values[name] === undefined ? undefined : { value: values[name] }),
  };
}

function namedError(name: string): Error {
  const error = new Error(name);
  error.name = name;
  return error;
}

describe("callBackend", () => {
  beforeEach(() => {
    process.env.BACKEND_API_BASE_URL = "http://backend.test";
    process.env.COGNITO_REGION = "ap-south-1";
    process.env.COGNITO_CLIENT_ID = "web-client-id";
    cognito.refreshTokens.mockReset();
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    delete process.env.BACKEND_API_BASE_URL;
  });

  it("does not refresh from a Server Component path and returns unauthenticated", async () => {
    const result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: false,
    });

    expect(result).toEqual({ kind: "unauthenticated" });
    expect(cognito.refreshTokens).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("keeps the rotated refresh token when the backend times out", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    vi.mocked(fetch).mockRejectedValueOnce(namedError("TimeoutError"));

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    expect(result).toEqual({
      kind: "unavailable",
      reason: "timeout",
      refreshed: { accessToken: "access-2", refreshToken: "refresh-2", expiresIn: undefined },
    });
    expect(cognito.refreshTokens).toHaveBeenCalledWith("refresh-1");
  });

  it("keeps the rotated refresh token when the backend is unreachable", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    vi.mocked(fetch).mockRejectedValueOnce(new TypeError("fetch failed"));

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    expect(result).toMatchObject({
      kind: "unavailable",
      reason: "unreachable",
      refreshed: { refreshToken: "refresh-2" },
    });
  });

  it("forwards the fresh access token and returns the rotated refresh token with the response", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response('{"ok":true}', { status: 200, headers: { "content-type": "application/json" } }),
    );

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    const [, init] = vi.mocked(fetch).mock.calls[0];
    expect((init?.headers as Record<string, string>).Authorization).toBe("Bearer access-2");
    expect(result).toMatchObject({
      kind: "response",
      status: 200,
      refreshed: { refreshToken: "refresh-2" },
    });
  });

  it("forwards only validated discovery query parameters upstream", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response('{"items":[],"nextCursor":null}', { status: 200, headers: { "content-type": "application/json" } }),
    );

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "discovery"],
      search: new URLSearchParams("size=20&cursor=AbC-123"),
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    const [url] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toBe("http://backend.test/api/v1/discovery?size=20&cursor=AbC-123");
    expect(result).toMatchObject({ kind: "response", status: 200 });
  });

  it("rejects disallowed query parameters before any session lookup or upstream call", async () => {
    const result = await callBackend({
      method: "GET",
      segments: ["v1", "discovery"],
      search: new URLSearchParams("size=500"),
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    expect(result).toEqual({ kind: "invalid_query" });
    expect(cognito.refreshTokens).not.toHaveBeenCalled();
    expect(fetch).not.toHaveBeenCalled();
  });

  it("does not forward query parameters on paths that accept none", async () => {
    const result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      search: new URLSearchParams("redirect=https://evil.test"),
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    expect(result).toEqual({ kind: "invalid_query" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("returns unauthenticated when Cognito rejects the refresh token", async () => {
    cognito.refreshTokens.mockRejectedValueOnce(namedError("NotAuthorizedException"));

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "me"],
      read: reader({ [ACCESS_COOKIE_NAME]: "", [REFRESH_COOKIE_NAME]: "revoked" }),
      canRefresh: true,
    });

    expect(result).toEqual({ kind: "unauthenticated" });
    expect(fetch).not.toHaveBeenCalled();
  });

  it("forwards a buffered multipart upload with the browser-supplied boundary", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response('{"id":"p1"}', { status: 201, headers: { "content-type": "application/json" } }),
    );
    const bytes = new Uint8Array([0xff, 0xd8, 0xff, 0xe0]);
    const contentType = "multipart/form-data; boundary=----WebKitFormBoundaryabc123";

    const result = await callBackend({
      method: "POST",
      segments: ["v1", "profile", "photos"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      upload: { bytes, contentType },
      canRefresh: true,
    });

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(String(url)).toBe("http://backend.test/api/v1/profile/photos");
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe(contentType);
    expect(init?.body).toBe(bytes);
    expect(result).toMatchObject({ kind: "response", status: 201 });
  });

  it("relays a photo's bytes unchanged and asks the backend for an image", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    const photo = new Uint8Array([1, 2, 3, 4]);
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(photo, { status: 200, headers: { "content-type": "image/jpeg" } }),
    );

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "profile", "photos", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", "content"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    const [, init] = vi.mocked(fetch).mock.calls[0];
    expect((init?.headers as Record<string, string>).Accept).toBe("image/jpeg, image/png");
    expect(result).toMatchObject({ kind: "response", status: 200, contentType: "image/jpeg" });
    expect(result.kind === "response" && result.bytes && Array.from(result.bytes)).toEqual([1, 2, 3, 4]);
  });

  it("reports the backend as unreachable when a photo exceeds the relay cap", async () => {
    cognito.refreshTokens.mockResolvedValueOnce({ accessToken: "access-2", refreshToken: "refresh-2" });
    const oversized = new Uint8Array(8 * 1024 * 1024 + 1);
    vi.mocked(fetch).mockResolvedValueOnce(
      new Response(oversized, { status: 200, headers: { "content-type": "image/png" } }),
    );

    const result = await callBackend({
      method: "GET",
      segments: ["v1", "profile", "photos", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", "content"],
      read: reader({ [REFRESH_COOKIE_NAME]: "refresh-1" }),
      canRefresh: true,
    });

    expect(result).toMatchObject({ kind: "unavailable", reason: "unreachable" });
  });
});
