import { readBackendOrigin } from "./config";
import { resolveBackendPath, type BackendMethod } from "./proxy-policy";
import { resolveFreshSession, type CookieReader, type ResolvedSession } from "./session";

const BACKEND_TIMEOUT_MS = 10_000;

export class BackendTimeoutError extends Error {
  constructor() {
    super("Backend request timed out");
    this.name = "BackendTimeoutError";
  }
}

export class BackendUnavailableError extends Error {
  constructor() {
    super("Backend request failed");
    this.name = "BackendUnavailableError";
  }
}

export type BackendCallInput = {
  method: string;
  segments: readonly string[];
  read: CookieReader;
  /** Raw request body; only forwarded for PUT and POST. */
  body?: string;
};

export type BackendCallResult =
  | { kind: "not_allowed" }
  | { kind: "unauthenticated" }
  | {
      kind: "response";
      status: number;
      body: string;
      contentType: string | null;
      refreshed?: ResolvedSession["refreshed"];
    };

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
  const origin = readBackendOrigin();

  const session = await resolveFreshSession(input.read);
  if (!session) {
    return { kind: "unauthenticated" };
  }

  let attempt = await send(origin, path, method, session.accessToken, input.body);
  if (attempt.status === 401 && !session.refreshed) {
    const renewed = await resolveFreshSession(input.read, { force: true });
    if (!renewed) {
      return { kind: "unauthenticated" };
    }
    attempt = await send(origin, path, method, renewed.accessToken, input.body);
    return { ...attempt, refreshed: renewed.refreshed };
  }
  return { ...attempt, refreshed: session.refreshed };
}

async function send(
  origin: string,
  path: string,
  method: BackendMethod,
  accessToken: string,
  body: string | undefined,
): Promise<{ kind: "response"; status: number; body: string; contentType: string | null }> {
  const forwardBody = method !== "GET" && body !== undefined;
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    Accept: "application/json",
  };
  if (forwardBody) {
    headers["Content-Type"] = "application/json";
  }

  let upstream: Response;
  try {
    upstream = await fetch(new URL(path, origin), {
      method,
      headers,
      body: forwardBody ? body : undefined,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
  } catch (error) {
    if (error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError")) {
      throw new BackendTimeoutError();
    }
    throw new BackendUnavailableError();
  }

  return {
    kind: "response",
    status: upstream.status,
    body: await upstream.text(),
    contentType: upstream.headers.get("content-type"),
  };
}
