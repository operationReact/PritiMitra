import { describe, expect, it } from "vitest";
import { isBinaryBackendPath, isMultipartBackendRoute, resolveBackendPath, resolveBackendQuery } from "./proxy-policy";

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

  it("maps photo list and upload only on the exact collection path", () => {
    expect(resolveBackendPath("GET", ["v1", "profile", "photos"])).toBe("/api/v1/profile/photos");
    expect(resolveBackendPath("POST", ["v1", "profile", "photos"])).toBe("/api/v1/profile/photos");
    expect(resolveBackendPath("PUT", ["v1", "profile", "photos"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "profile", "photos"])).toBeNull();
  });

  it("maps photo content and delete only for a canonical UUID", () => {
    const id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
    expect(resolveBackendPath("GET", ["v1", "profile", "photos", id, "content"])).toBe(
      `/api/v1/profile/photos/${id}/content`,
    );
    expect(resolveBackendPath("DELETE", ["v1", "profile", "photos", id])).toBe(`/api/v1/profile/photos/${id}`);
    expect(resolveBackendPath("GET", ["v1", "profile", "photos", "not-a-uuid", "content"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "profile", "photos", "../" + id])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "profile", "photos", id + "x"])).toBeNull();
  });

  it("denies photo metadata writes and unknown photo subpaths", () => {
    const id = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
    expect(resolveBackendPath("GET", ["v1", "profile", "photos", id])).toBeNull();
    expect(resolveBackendPath("POST", ["v1", "profile", "photos", id, "content"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "profile", "photos", id, "content"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "profile", "photos", id, "storage"])).toBeNull();
  });

  it("flags only the upload route as multipart and only content as binary", () => {
    expect(isMultipartBackendRoute("POST", "/api/v1/profile/photos")).toBe(true);
    expect(isMultipartBackendRoute("GET", "/api/v1/profile/photos")).toBe(false);
    expect(isMultipartBackendRoute("POST", "/api/v1/likes/pub-abc123")).toBe(false);
    expect(isBinaryBackendPath(`/api/v1/profile/photos/3f2504e0-4f89-41d3-9a0c-0305e82c3301/content`)).toBe(true);
    expect(isBinaryBackendPath("/api/v1/profile/photos")).toBe(false);
  });
});

describe("resolveBackendQuery", () => {
  const DISCOVERY = "/api/v1/discovery";

  function query(path: string, raw: string): string | null {
    return resolveBackendQuery(path, new URLSearchParams(raw));
  }

  it("accepts an empty query on every path", () => {
    expect(query(DISCOVERY, "")).toBe("");
    expect(query("/api/v1/me", "")).toBe("");
  });

  it("forwards allow-listed discovery parameters in normalized form", () => {
    expect(query(DISCOVERY, "size=20&cursor=AbC_-9")).toBe("size=20&cursor=AbC_-9");
    expect(query(DISCOVERY, "goal=DATING")).toBe("goal=DATING");
  });

  it("accepts size boundaries 1 and 50 and rejects 0 and 51", () => {
    expect(query(DISCOVERY, "size=1")).toBe("size=1");
    expect(query(DISCOVERY, "size=50")).toBe("size=50");
    expect(query(DISCOVERY, "size=0")).toBeNull();
    expect(query(DISCOVERY, "size=51")).toBeNull();
  });

  it("rejects malformed numbers and cursors", () => {
    expect(query(DISCOVERY, "size=2.5")).toBeNull();
    expect(query(DISCOVERY, "size=-1")).toBeNull();
    expect(query(DISCOVERY, "size=20abc")).toBeNull();
    expect(query(DISCOVERY, "cursor=../../admin")).toBeNull();
    expect(query(DISCOVERY, "cursor=a%2Fb")).toBeNull();
    expect(query(DISCOVERY, `cursor=${"a".repeat(129)}`)).toBeNull();
  });

  it("rejects unknown goal values", () => {
    expect(query(DISCOVERY, "goal=dating")).toBeNull();
    expect(query(DISCOVERY, "goal=ADMIN")).toBeNull();
  });

  it("rejects unknown and repeated parameters", () => {
    expect(query(DISCOVERY, "admin=1")).toBeNull();
    expect(query(DISCOVERY, "size=10&size=20")).toBeNull();
    expect(query(DISCOVERY, "size=10&cursor=abc&unexpected=x")).toBeNull();
  });

  it("does not treat inherited object keys as allowed parameters", () => {
    expect(query(DISCOVERY, "constructor=x")).toBeNull();
    expect(query(DISCOVERY, "__proto__=x")).toBeNull();
  });

  it("rejects any query on paths without rules", () => {
    expect(query("/api/v1/me", "size=20")).toBeNull();
    expect(query("/api/v1/likes/pub-abc123", "cursor=abc")).toBeNull();
  });
});

describe("messaging and safety routes", () => {
  it("allow-lists exact list, read-all and report routes", () => {
    expect(resolveBackendPath("GET", ["v1", "matches"])).toBe("/api/v1/matches");
    expect(resolveBackendPath("GET", ["v1", "conversations"])).toBe("/api/v1/conversations");
    expect(resolveBackendPath("GET", ["v1", "notifications"])).toBe("/api/v1/notifications");
    expect(resolveBackendPath("PUT", ["v1", "notifications", "read-all"])).toBe("/api/v1/notifications/read-all");
    expect(resolveBackendPath("POST", ["v1", "reports"])).toBe("/api/v1/reports");
    expect(resolveBackendPath("POST", ["v1", "matches"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "conversations"])).toBeNull();
  });

  it("allow-lists message history, send and read cursor for a conversation id only", () => {
    const id = "conv-abc123";
    expect(resolveBackendPath("GET", ["v1", "conversations", id, "messages"])).toBe(`/api/v1/conversations/${id}/messages`);
    expect(resolveBackendPath("POST", ["v1", "conversations", id, "messages"])).toBe(`/api/v1/conversations/${id}/messages`);
    expect(resolveBackendPath("PUT", ["v1", "conversations", id, "read"])).toBe(`/api/v1/conversations/${id}/read`);
    expect(resolveBackendPath("DELETE", ["v1", "conversations", id, "messages"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "conversations", "bad.id", "messages"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "conversations", id, "messages", "extra"])).toBeNull();
  });

  it("allow-lists match unmatch, notification read and block by public id", () => {
    expect(resolveBackendPath("DELETE", ["v1", "matches", "m-123"])).toBe("/api/v1/matches/m-123");
    expect(resolveBackendPath("GET", ["v1", "matches", "m-123"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "notifications", "n-123", "read"])).toBe("/api/v1/notifications/n-123/read");
    expect(resolveBackendPath("GET", ["v1", "notifications", "n-123", "read"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "blocks", "p-123"])).toBe("/api/v1/blocks/p-123");
    expect(resolveBackendPath("DELETE", ["v1", "blocks", "p-123"])).toBe("/api/v1/blocks/p-123");
    expect(resolveBackendPath("GET", ["v1", "blocks", "p-123"])).toBeNull();
  });

  it("accepts only cursor and size on paged lists and rejects unknown or repeated parameters", () => {
    const paged = (path: string, search: string) => resolveBackendQuery(path, new URLSearchParams(search));
    expect(paged("/api/v1/matches", "cursor=abc_-1&size=20")).toBe("cursor=abc_-1&size=20");
    expect(paged("/api/v1/conversations", "size=51")).toBeNull();
    expect(paged("/api/v1/notifications", "unread=true")).toBeNull();
    expect(paged("/api/v1/conversations/conv-1/messages", "cursor=abc&size=30")).toBe("cursor=abc&size=30");
    expect(paged("/api/v1/conversations/conv-1/messages", "cursor=a&cursor=b")).toBeNull();
    expect(paged("/api/v1/conversations/conv-1/messages", "cursor=a;drop")).toBeNull();
  });
});

