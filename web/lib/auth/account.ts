export const ACCOUNT_STATUSES = [
  "PENDING_EMAIL_VERIFICATION",
  "ONBOARDING_INCOMPLETE",
  "ACTIVE",
  "SUSPENDED",
  "INELIGIBLE",
] as const;

export type AccountStatus = (typeof ACCOUNT_STATUSES)[number];

export type MeResponse = {
  id: string;
  status: AccountStatus;
  displayName: string | null;
};

export type AccountGuardDecision = "continue" | "verify-email" | "onboarding" | "restricted";

export function parseMeResponse(value: unknown): MeResponse | null {
  if (typeof value !== "object" || value === null) {
    return null;
  }
  const candidate = value as Record<string, unknown>;
  if (
    typeof candidate.id !== "string" ||
    typeof candidate.status !== "string" ||
    !(ACCOUNT_STATUSES as readonly string[]).includes(candidate.status) ||
    (candidate.displayName !== null && typeof candidate.displayName !== "string")
  ) {
    return null;
  }
  return {
    id: candidate.id,
    status: candidate.status as AccountStatus,
    displayName: candidate.displayName,
  };
}

/** Decides which step the web app shows for an account, based only on the backend status. */
export function accountGuardDecision(status: AccountStatus): AccountGuardDecision {
  switch (status) {
    case "ACTIVE":
      return "continue";
    case "PENDING_EMAIL_VERIFICATION":
      return "verify-email";
    case "ONBOARDING_INCOMPLETE":
      return "onboarding";
    case "SUSPENDED":
    case "INELIGIBLE":
      return "restricted";
  }
}
