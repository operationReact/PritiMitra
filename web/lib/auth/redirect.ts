/** Safe landing destinations after the server confirms the authenticated account state. */
export function destinationForAccountStatus(status: unknown): "/home" | "/onboarding" | "/confirm-sign-up" | "/account" {
  switch (status) {
    case "ACTIVE": return "/home";
    case "ONBOARDING_INCOMPLETE": return "/onboarding";
    case "PENDING_EMAIL_VERIFICATION": return "/confirm-sign-up";
    default: return "/account";
  }
}
