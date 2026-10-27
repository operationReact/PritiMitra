import { describe, expect, it } from "vitest";
import { destinationForAccountStatus } from "./redirect";

describe("destinationForAccountStatus", () => {
  it("sends active users to the product workspace", () => {
    expect(destinationForAccountStatus("ACTIVE")).toBe("/home");
  });

  it("sends incomplete accounts to onboarding", () => {
    expect(destinationForAccountStatus("ONBOARDING_INCOMPLETE")).toBe("/onboarding");
  });

  it("routes pending verification to confirmation", () => {
    expect(destinationForAccountStatus("PENDING_EMAIL_VERIFICATION")).toBe("/confirm-sign-up");
  });

  it("fails closed for unknown, suspended, and ineligible statuses", () => {
    expect(destinationForAccountStatus("SUSPENDED")).toBe("/account");
    expect(destinationForAccountStatus("INELIGIBLE")).toBe("/account");
    expect(destinationForAccountStatus(undefined)).toBe("/account");
  });
});
