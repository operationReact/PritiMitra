import { isProductionRuntime } from "./config";
import { errorName } from "./errors";
import { refreshTokens, type IssuedTokens } from "./cognito";
import {
  ACCESS_COOKIE_NAME,
  REFRESH_COOKIE_MAX_AGE_SECONDS,
  REFRESH_COOKIE_NAME,
  accessTokenMaxAge,
  buildClearedCookieOptions,
  buildSessionCookieOptions,
  type SessionCookieOptions,
} from "./cookies";
import { needsRefresh } from "./tokens";

/** Minimal read surface shared by next/headers cookies() and NextRequest.cookies. */
export type CookieReader = {
  get(name: string): { value: string } | undefined;
};

/** Minimal write surface shared by NextResponse.cookies. */
export type CookieWriter = {
  set(name: string, value: string, options: SessionCookieOptions): unknown;
};

export type ResolvedSession = {
  accessToken: string;
  /** Present only when the session was refreshed; callers must persist it when they can write cookies. */
  refreshed?: {
    accessToken: string;
    refreshToken?: string;
    expiresIn?: number;
  };
};

const SESSION_REJECTION_ERRORS = new Set(["NotAuthorizedException", "UserNotFoundException"]);

export function isSessionRejection(error: unknown): boolean {
  const name = errorName(error);
  return name !== undefined && SESSION_REJECTION_ERRORS.has(name);
}

export function readSessionTokens(read: CookieReader): { accessToken?: string; refreshToken?: string } {
  return {
    accessToken: read.get(ACCESS_COOKIE_NAME)?.value || undefined,
    refreshToken: read.get(REFRESH_COOKIE_NAME)?.value || undefined,
  };
}

/**
 * Returns a usable access token. Refreshes with the refresh token when the access token is missing,
 * near expiry, or when force is set (after the backend rejected it). Returns null when there is no
 * valid session, and throws only for non-session failures such as network or configuration errors.
 */
export async function resolveFreshSession(
  read: CookieReader,
  options: { force?: boolean; nowSeconds?: number } = {},
): Promise<ResolvedSession | null> {
  const now = options.nowSeconds ?? Math.floor(Date.now() / 1000);
  const { accessToken, refreshToken } = readSessionTokens(read);

  if (accessToken && !options.force && !needsRefresh(accessToken, now)) {
    return { accessToken };
  }
  if (!refreshToken) {
    return null;
  }

  let issued: IssuedTokens;
  try {
    issued = await refreshTokens(refreshToken);
  } catch (error) {
    if (isSessionRejection(error)) {
      return null;
    }
    throw error;
  }
  return {
    accessToken: issued.accessToken,
    refreshed: {
      accessToken: issued.accessToken,
      refreshToken: issued.refreshToken,
      expiresIn: issued.expiresIn,
    },
  };
}

/** Writes the session cookies after sign-in or a refresh. Tokens are never returned in response bodies. */
export function setSessionCookies(
  target: CookieWriter,
  tokens: { accessToken: string; refreshToken?: string; expiresIn?: number },
  isProduction: boolean = isProductionRuntime(),
): void {
  target.set(
    ACCESS_COOKIE_NAME,
    tokens.accessToken,
    buildSessionCookieOptions({ isProduction, maxAge: accessTokenMaxAge(tokens.expiresIn) }),
  );
  if (tokens.refreshToken) {
    target.set(
      REFRESH_COOKIE_NAME,
      tokens.refreshToken,
      buildSessionCookieOptions({ isProduction, maxAge: REFRESH_COOKIE_MAX_AGE_SECONDS }),
    );
  }
}

export function clearSessionCookies(target: CookieWriter, isProduction: boolean = isProductionRuntime()): void {
  const cleared = buildClearedCookieOptions(isProduction);
  target.set(ACCESS_COOKIE_NAME, "", cleared);
  target.set(REFRESH_COOKIE_NAME, "", cleared);
}
