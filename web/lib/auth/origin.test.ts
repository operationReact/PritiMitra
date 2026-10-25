import { describe, expect, it } from "vitest";
import { checkStateChange, hasJsonContentType, isSameOrigin } from "./origin";

const ORIGIN = "https://pritimitra.example";

function headers(init: Record<string, string>): Headers {
  return new Headers(init);
}

describe("isSameOrigin", () => {
  it("accepts an Origin header that matches the request origin", () => {
    expect(isSameOrigin(headers({ origin: ORIGIN }), ORIGIN)).toBe(true);
  });

  it("rejects a missing Origin header", () => {
    expect(isSameOrigin(headers({}), ORIGIN)).toBe(false);
  });

  it("rejects a different origin, including a different port or scheme", () => {
    expect(isSameOrigin(headers({ origin: "https://evil.example" }), ORIGIN)).toBe(false);
    expect(isSameOrigin(headers({ origin: "https://pritimitra.example:8443" }), ORIGIN)).toBe(false);
    expect(isSameOrigin(headers({ origin: "http://pritimitra.example" }), ORIGIN)).toBe(false);
  });

  it("rejects the literal null origin and malformed values", () => {
    expect(isSameOrigin(headers({ origin: "null" }), ORIGIN)).toBe(false);
    expect(isSameOrigin(headers({ origin: "not a url" }), ORIGIN)).toBe(false);
  });

  it("does not treat a prefix match as the same origin", () => {
    expect(isSameOrigin(headers({ origin: "https://pritimitra.example.evil.test" }), ORIGIN)).toBe(false);
  });
});

describe("hasJsonContentType", () => {
  it("accepts application/json with or without parameters", () => {
    expect(hasJsonContentType(headers({ "content-type": "application/json" }))).toBe(true);
    expect(hasJsonContentType(headers({ "content-type": "Application/JSON; charset=utf-8" }))).toBe(true);
  });

  it("rejects other media types and missing headers", () => {
    expect(hasJsonContentType(headers({ "content-type": "text/plain" }))).toBe(false);
    expect(hasJsonContentType(headers({ "content-type": "application/x-www-form-urlencoded" }))).toBe(false);
    expect(hasJsonContentType(headers({}))).toBe(false);
  });
});

describe("checkStateChange", () => {
  it("passes for a same-origin JSON request", () => {
    expect(
      checkStateChange(headers({ origin: ORIGIN, "content-type": "application/json" }), ORIGIN),
    ).toEqual({ ok: true });
  });

  it("reports origin failure first", () => {
    expect(checkStateChange(headers({ "content-type": "text/plain" }), ORIGIN)).toEqual({
      ok: false,
      reason: "origin",
    });
  });

  it("reports content type failure for a same-origin non-JSON request", () => {
    expect(checkStateChange(headers({ origin: ORIGIN, "content-type": "text/plain" }), ORIGIN)).toEqual({
      ok: false,
      reason: "content_type",
    });
  });
});
