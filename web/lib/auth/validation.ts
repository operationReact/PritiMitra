const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CODE_PATTERN = /^[0-9]{6}$/;
const MAX_EMAIL_LENGTH = 254;
const MAX_PASSWORD_LENGTH = 256;

export function parseEmail(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const email = value.trim();
  if (email.length === 0 || email.length > MAX_EMAIL_LENGTH || !EMAIL_PATTERN.test(email)) {
    return null;
  }
  return email;
}

/** Password policy is enforced by Cognito; this only bounds size and type. */
export function parsePassword(value: unknown): string | null {
  if (typeof value !== "string" || value.length === 0 || value.length > MAX_PASSWORD_LENGTH) {
    return null;
  }
  return value;
}

export function parseConfirmationCode(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const code = value.trim();
  return CODE_PATTERN.test(code) ? code : null;
}
