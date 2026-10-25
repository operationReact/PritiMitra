import { readBackendOrigin } from "./config";
import { resolveBackendPath, resolveBackendQuery, type BackendMethod } from "./proxy-policy";
import { resolveFreshSession, type CookieReader, type RefreshedTokens } from "./session";

const BACKEND_TIMEOUT_MS = 10_000;

export type BackendCallInput = {
  method: string;
  segments: readonly string[];
  /** Query parameters from the browser request; validated against the per-path allow-list before forwarding. */
  search?: URLSearchParams;
  read: CookieReader;
  /** Raw request body; only forwarded for PUT and POST. */
  body?: string;
  /**
   * Only route handlers can write rotated cookies. Server Components pass false: refreshing there would consume
   * the refresh token without persisting its replacement, which would log the user out.
   */
  canRefresh: boolean;
};

type BackendAttempt =
  | { kind: "response"; status: number; body: string; contentType: string | null }
  | { kind: "unavailable"; reason: "timeout" | "unreachable" };

/** `refreshed` is set whenever a rotation happened, even if the backend call then failed. */
export type BackendCallResult =
  | { kind: "not_allowed" }
  | { kind: "invalid_query" }
  | { kind: "unauthenticated" }
  | (BackendAttempt & { refreshed?: RefreshedTokens });

/**
 * Forwards an allow-listed request to the backend with the access token from the session cookie.
 * Client identity headers are never forwarded. One retry follows a 401 after a forced refresh.
 */
export async function callBackend(input: BackendCallInput): Promise<BackendCallResult> {
  const method = input.method as BackendMethod;
  const path = resolveBackendPath(method, input.segments);
  if (!path) {
    return { kind: "not_allowed" };
  }
  const query = resolveBackendQuery(path, input.search ?? new URLSearchParams());
  if (query === null) {
    return { kind: "invalid_query" };
  }
  const target = query ? `${path}?${query}` : path;
  const origin = readBackendOrigin();

  const session = await resolveFreshSession(input.read, { allowRefresh: input.canRefresh });
  if (!session) {
    return { kind: "unauthenticated" };
  }

  let current = session;
  let attempt = await send(origin, target, method, current.accessToken, input.body);
  if (attempt.kind === "response" && attempt.status === 401 && !current.refreshed) {
    const renewed = await resolveFreshSession(input.read, { force: true, allowRefresh: input.canRefresh });
    if (!renewed) {
      return { kind: "unauthenticated" };
    }
    current = renewed;
    attempt = await send(origin, target, method, current.accessToken, input.body);
  }
  return { ...attempt, refreshed: current.refreshed };
}

async function send(
  origin: string,
  target: string,
  method: BackendMethod,
  accessToken: string,
  body: string | undefined,
): Promise<BackendAttempt> {
  const forwardBody = method !== "GET" && body !== undefined;
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
  };
  if (forwardBody) {
    headers["Content-Type"] = "application/json";
  }

  try {
    const upstream = await fetch(new URL(target, origin), {
      method,
      headers,
      body: forwardBody ? body : undefined,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
    return {
      kind: "response",
      status: upstream.status,
      body: await upstream.text(),
      contentType: upstream.headers.get("content-type"),
    };
  } catch (error) {
    const timedOut = error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError");
    return { kind: "unavailable", reason: timedOut ? "timeout" : "unreachable" };
  }
}
