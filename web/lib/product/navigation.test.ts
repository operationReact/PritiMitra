import { describe, expect, it } from "vitest";
import { isProductNavActive, isProductRoute, productPageTitle } from "./navigation";

describe("responsive product route shell", () => {
  it("keeps the public landing page outside the authenticated app shell", () => {
    expect(isProductRoute("/")).toBe(false);
    expect(isProductRoute("/home")).toBe(true);
    expect(isProductRoute("/messages/3f2504e0-4f89-41d3-9a0c-0305e82c3301")).toBe(true);
  });

  it("does not match unrelated routes by a shared prefix", () => {
    expect(isProductRoute("/discover-more")).toBe(false);
    expect(isProductRoute("/community-guidelines")).toBe(false);
  });

  it("marks nested conversations active and keeps account settings under profile", () => {
    expect(isProductNavActive("/messages/3f2504e0-4f89-41d3-9a0c-0305e82c3301", "/messages")).toBe(true);
    expect(isProductNavActive("/account", "/profile")).toBe(true);
    expect(isProductNavActive("/discover", "/messages")).toBe(false);
  });

  it("uses a clear title for every primary product area", () => {
    expect(productPageTitle("/home")).toBe("Your space");
    expect(productPageTitle("/blind-date")).toBe("Blind Date");
    expect(productPageTitle("/messages/abc")).toBe("Your conversation");
    expect(productPageTitle("/moderation")).toBe("Trust & Safety");
  });
});
