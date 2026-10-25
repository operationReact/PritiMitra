import { describe, expect, it } from "vitest";
import { resolveBackendPath } from "./proxy-policy";

describe("resolveBackendPath", () => {
  it("maps allow-listed method and path pairs", () => {
    expect(resolveBackendPath("GET", ["v1", "me"])).toBe("/api/v1/me");
    expect(resolveBackendPath("GET", ["v1", "onboarding"])).toBe("/api/v1/onboarding");
    expect(resolveBackendPath("PUT", ["v1", "onboarding", "profile"])).toBe("/api/v1/onboarding/profile");
    expect(resolveBackendPath("POST", ["v1", "onboarding", "complete"])).toBe("/api/v1/onboarding/complete");
  });

  it("denies a path that is not on the list, even under /api/v1", () => {
    expect(resolveBackendPath("GET", ["v1", "admin"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "onboarding", "profile"])).toBeNull();
  });

  it("denies an allowed path with the wrong method", () => {
    expect(resolveBackendPath("POST", ["v1", "me"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "onboarding", "complete"])).toBeNull();
  });

  it("denies unsupported methods", () => {
    expect(resolveBackendPath("DELETE", ["v1", "me"])).toBeNull();
    expect(resolveBackendPath("PATCH", ["v1", "onboarding", "profile"])).toBeNull();
    expect(resolveBackendPath("get", ["v1", "me"])).toBeNull();
  });

  it("maps profile and discovery routes", () => {
    expect(resolveBackendPath("GET", ["v1", "profile"])).toBe("/api/v1/profile");
    expect(resolveBackendPath("PUT", ["v1", "profile", "discoverability"])).toBe("/api/v1/profile/discoverability");
    expect(resolveBackendPath("GET", ["v1", "discovery"])).toBe("/api/v1/discovery");
  });

  it("maps like and unlike only for a single public id", () => {
    expect(resolveBackendPath("PUT", ["v1", "likes", "pub-abc123"])).toBe("/api/v1/likes/pub-abc123");
    expect(resolveBackendPath("DELETE", ["v1", "likes", "pub-abc123"])).toBe("/api/v1/likes/pub-abc123");
    expect(resolveBackendPath("GET", ["v1", "likes", "pub-abc123"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "likes"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "likes", "a", "b"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "likes", "bad.id"])).toBeNull();
  });

  it("does not allow profile writes beyond discoverability or DELETE on profile routes", () => {
    expect(resolveBackendPath("PUT", ["v1", "profile"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "profile", "discoverability"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "discovery"])).toBeNull();
  });

  it("denies traversal, encoded, and query-like segments", () => {
    expect(resolveBackendPath("GET", ["v1", "..", "me"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "me%2F..%2Fadmin"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "me?x=1"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "me.json"])).toBeNull();
  });

  it("denies empty and overly long segment lists", () => {
    expect(resolveBackendPath("GET", [])).toBeNull();
    expect(resolveBackendPath("GET", Array.from({ length: 9 }, () => "v1"))).toBeNull();
  });
});
