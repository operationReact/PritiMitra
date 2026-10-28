import { describe, expect, it } from "vitest";
import { isBinaryBackendPath, isMultipartBackendRoute, resolveBackendPath, resolveBackendQuery } from "./proxy-policy";

describe("resolveBackendPath", () => {
  it("allows only the exact moderator audit history routes", () => {
    expect(resolveBackendPath("GET", ["v1", "moderation", "audit"])).toBe("/api/v1/moderation/audit");
    expect(resolveBackendPath("GET", ["v1", "moderation", "audit", "export"])).toBe("/api/v1/moderation/audit/export");
    expect(resolveBackendPath("POST", ["v1", "moderation", "audit"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "moderation", "audit", "export", "raw"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "moderation", "audit", "export"])).toBeNull();
  });

  it("maps allow-listed method and path pairs", () => {
    expect(resolveBackendPath("GET", ["v1", "me"])).toBe("/api/v1/me");
    expect(resolveBackendPath("GET", ["v1", "onboarding"])).toBe("/api/v1/onboarding");
    expect(resolveBackendPath("PUT", ["v1", "onboarding", "profile"])).toBe("/api/v1/onboarding/profile");
    expect(resolveBackendPath("POST", ["v1", "onboarding", "complete"])).toBe("/api/v1/onboarding/complete");
  });

  it("maps only the intended self-service account lifecycle routes", () => {
    expect(resolveBackendPath("DELETE", ["v1", "account"])).toBe("/api/v1/account");
    expect(resolveBackendPath("GET", ["v1", "account", "consents"])).toBe("/api/v1/account/consents");
    expect(resolveBackendPath("PUT", ["v1", "account", "consents", "community-nickname"])).toBe(
      "/api/v1/account/consents/community-nickname",
    );
    expect(resolveBackendPath("POST", ["v1", "account"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "account"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "account", "consents"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "account", "consents", "terms-of-service"])).toBeNull();
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

  it("validates moderator audit filters and export query parameters", () => {
    const from = "2026-10-01T00:00:00.000Z";
    const to = "2026-10-28T00:00:00.000Z";
    const auditQuery = "eventType=ACCOUNT_SUSPENDED&from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to) + "&size=50&cursor=YWJjXzEyMw";
    expect(query("/api/v1/moderation/audit", auditQuery)).toBe(auditQuery);
    const exportQuery = "eventType=ALL&from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to);
    expect(query("/api/v1/moderation/audit/export", exportQuery)).toBe(exportQuery);
    expect(query("/api/v1/moderation/audit/export", "size=20")).toBeNull();
    expect(query("/api/v1/moderation/audit", "eventType=BOGUS")).toBeNull();
    expect(query("/api/v1/moderation/audit", "size=51")).toBeNull();
    expect(query("/api/v1/moderation/audit", "from=not-a-date")).toBeNull();
  });

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

  it("validates moderation queue filters and paging parameters", () => {
    const path = "/api/v1/moderation/reports";
    expect(query(path, "status=PENDING&size=20")).toBe("status=PENDING&size=20");
    expect(query(path, "status=ALL&cursor=../../admin")).toBeNull();
    expect(query(path, "status=ALL&size=51")).toBeNull();
    expect(query(path, "status=UNKNOWN")).toBeNull();
    expect(query(path, "status=PENDING&admin=true")).toBeNull();
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
    expect(resolveBackendPath("GET", ["v1", "moderation", "reports"])).toBe("/api/v1/moderation/reports");
    expect(resolveBackendPath("PUT", ["v1", "moderation", "reports", "3f2504e0-4f89-41d3-9a0c-0305e82c3301", "decision"]))
      .toBe("/api/v1/moderation/reports/3f2504e0-4f89-41d3-9a0c-0305e82c3301/decision");
    expect(resolveBackendPath("POST", ["v1", "moderation", "reports"])).toBeNull();
    expect(resolveBackendPath("PUT", ["v1", "moderation", "reports", "not-a-uuid", "decision"])).toBeNull();
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

  it("allow-lists conversation detail by id and catch-up for messages newer than a cursor", () => {
    const id = "conv-abc123";
    expect(resolveBackendPath("GET", ["v1", "conversations", id])).toBe(`/api/v1/conversations/${id}`);
    expect(resolveBackendPath("PUT", ["v1", "conversations", id])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "conversations", id, "messages", "newer"])).toBe(
      `/api/v1/conversations/${id}/messages/newer`,
    );
    expect(resolveBackendPath("POST", ["v1", "conversations", id, "messages", "newer"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "conversations", "bad.id", "messages", "newer"])).toBeNull();
    const query = (search: string) =>
      resolveBackendQuery(`/api/v1/conversations/${id}/messages/newer`, new URLSearchParams(search));
    expect(query("cursor=abc_-1&size=50")).toBe("cursor=abc_-1&size=50");
    expect(query("cursor=abc&size=0")).toBeNull();
    expect(query("cursor=abc&size=51")).toBeNull();
    expect(query("unread=true")).toBeNull();
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



describe("Phase 6 community and Blind Date proxy policy", () => {
  const group = "3f2504e0-4f89-41d3-9a0c-0305e82c3301";
  const post = "8f2504e0-4f89-41d3-9a0c-0305e82c3301";
  const optIn = "9f2504e0-4f89-41d3-9a0c-0305e82c3301";
  it("allows only explicit community routes and resource methods", () => {
    expect(resolveBackendPath("GET", ["v1", "community", "groups"])).toBe("/api/v1/community/groups");
    expect(resolveBackendPath("PUT", ["v1", "community", "groups", group, "membership"])).toBe("/api/v1/community/groups/" + group + "/membership");
    expect(resolveBackendPath("DELETE", ["v1", "community", "groups", group, "membership"])).toBe("/api/v1/community/groups/" + group + "/membership");
    expect(resolveBackendPath("GET", ["v1", "community", "groups", group, "posts"])).toBe("/api/v1/community/groups/" + group + "/posts");
    expect(resolveBackendPath("POST", ["v1", "community", "groups", group, "posts"])).toBe("/api/v1/community/groups/" + group + "/posts");
    expect(resolveBackendPath("PUT", ["v1", "community", "posts", post, "reaction"])).toBe("/api/v1/community/posts/" + post + "/reaction");
    expect(resolveBackendPath("DELETE", ["v1", "community", "posts", post])).toBe("/api/v1/community/posts/" + post);
    expect(resolveBackendPath("POST", ["v1", "community", "groups", "bad-id", "posts"])).toBeNull();
    expect(resolveBackendPath("PATCH", ["v1", "community", "groups", group, "posts"])).toBeNull();
    expect(resolveBackendPath("GET", ["v1", "community", "posts", post, "reaction"])).toBeNull();
  });
  it("allows only expected Blind Date opt-in and session methods", () => {
    expect(resolveBackendPath("GET", ["v1", "blind-dates", "my"])).toBe("/api/v1/blind-dates/my");
    expect(resolveBackendPath("POST", ["v1", "blind-dates", "opt-ins"])).toBe("/api/v1/blind-dates/opt-ins");
    expect(resolveBackendPath("DELETE", ["v1", "blind-dates", "opt-ins", optIn])).toBe("/api/v1/blind-dates/opt-ins/" + optIn);
    expect(resolveBackendPath("DELETE", ["v1", "blind-dates", "sessions", optIn])).toBe("/api/v1/blind-dates/sessions/" + optIn);
    expect(resolveBackendPath("GET", ["v1", "blind-dates", "opt-ins"])).toBeNull();
    expect(resolveBackendPath("DELETE", ["v1", "blind-dates", "sessions", "not-a-uuid"])).toBeNull();
  });
  it("allows only cursor and bounded page size for community feeds", () => {
    const path = "/api/v1/community/groups/" + group + "/posts";
    expect(resolveBackendQuery(path, new URLSearchParams("cursor=abc_-1&size=20"))).toBe("cursor=abc_-1&size=20");
    expect(resolveBackendQuery(path, new URLSearchParams("size=51"))).toBeNull();
    expect(resolveBackendQuery(path, new URLSearchParams("admin=true"))).toBeNull();
    expect(resolveBackendQuery(path, new URLSearchParams("cursor=a&cursor=b"))).toBeNull();
  });
});
