import { describe, expect, it } from "vitest";
import { parseDiscoveryPage, parseLikeStatus, parseOwnProfile } from "./models";

const profile = {
  publicId: "pub-1",
  displayName: "Asha",
  bio: null,
  profilePrompt: "Ask me about trains",
  goals: ["FRIENDSHIP"],
  interests: ["music"],
  discoverable: false,
};

describe("parseOwnProfile", () => {
  it("accepts the expected profile contract", () => {
    expect(parseOwnProfile(profile)).toEqual(profile);
  });

  it("rejects missing required fields and wrong types", () => {
    expect(parseOwnProfile({ ...profile, displayName: 7 })).toBeNull();
    expect(parseOwnProfile({ ...profile, discoverable: "yes" })).toBeNull();
    expect(parseOwnProfile({ ...profile, goals: "FRIENDSHIP" })).toBeNull();
    expect(parseOwnProfile(null)).toBeNull();
    expect(parseOwnProfile([])).toBeNull();
  });

  it("drops unknown fields rather than passing them through", () => {
    const parsed = parseOwnProfile({ ...profile, email: "x@example.com", cognitoSub: "sub" });
    expect(parsed).not.toHaveProperty("email");
    expect(parsed).not.toHaveProperty("cognitoSub");
  });
});

describe("parseDiscoveryPage", () => {
  it("accepts a page with items and a null cursor", () => {
    const page = { items: [{ ...profile, discoverable: undefined }], nextCursor: null };
    const parsed = parseDiscoveryPage(page);
    expect(parsed?.items).toHaveLength(1);
    expect(parsed?.nextCursor).toBeNull();
  });

  it("rejects a page whose item is malformed", () => {
    expect(parseDiscoveryPage({ items: [{ publicId: 1 }], nextCursor: null })).toBeNull();
    expect(parseDiscoveryPage({ items: "none", nextCursor: null })).toBeNull();
  });

  it("does not carry fields outside the discovery card", () => {
    const parsed = parseDiscoveryPage({
      items: [{ publicId: "p", displayName: "A", bio: null, profilePrompt: null, goals: [], interests: [], email: "e" }],
      nextCursor: null,
    });
    expect(parsed?.items[0]).not.toHaveProperty("email");
  });
});

describe("parseLikeStatus", () => {
  it("reads the liked flag and rejects malformed bodies", () => {
    expect(parseLikeStatus({ liked: true })).toBe(true);
    expect(parseLikeStatus({ liked: false })).toBe(false);
    expect(parseLikeStatus({ liked: "true" })).toBeNull();
    expect(parseLikeStatus({})).toBeNull();
  });
});
