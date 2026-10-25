export const ACCESS_COOKIE_NAME = "pm_access";
export const REFRESH_COOKIE_NAME = "pm_refresh";

/** Both session cookies are scoped to /api so they are never sent to page or asset requests. */
export const SESSION_COOKIE_PATH = "/api";

/** Cognito access tokens default to one hour; the cookie lifetime is clamped to this window. */
const ACCESS_MAX_AGE_SECONDS = 60 * 60;
const ACCESS_MIN_AGE_SECONDS = 60;

/** Cognito's default refresh-token validity is 30 days. */
export const REFRESH_COOKIE_MAX_AGE_SECONDS = 30 * 24 * 60 * 60;

export type SessionCookieOptions = {
  httpOnly: true;
  secure: boolean;
  sameSite: "lax";
  path: string;
  maxAge: number;
};

export function accessTokenMaxAge(expiresInSeconds: number | undefined): number {
  const requested =
    typeof expiresInSeconds === "number" && Number.isFinite(expiresInSeconds)
      ? Math.floor(expiresInSeconds)
      : ACCESS_MAX_AGE_SECONDS;
  return Math.min(ACCESS_MAX_AGE_SECONDS, Math.max(ACCESS_MIN_AGE_SECONDS, requested));
}

export function buildSessionCookieOptions(input: { isProduction: boolean; maxAge: number }): SessionCookieOptions {
  return {
    httpOnly: true,
    secure: input.isProduction,
    sameSite: "lax",
    path: SESSION_COOKIE_PATH,
    maxAge: input.maxAge,
  };
}

export function buildClearedCookieOptions(isProduction: boolean): SessionCookieOptions {
  return buildSessionCookieOptions({ isProduction, maxAge: 0 });
}
