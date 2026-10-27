import { readBackendOrigin } from "./config";
import { readBoundedBytes } from "./http";
import {
  isBinaryBackendPath,
  resolveBackendPath,
  resolveBackendQuery,
  type BackendMethod,
} from "./proxy-policy";
import { resolveFreshSession, type CookieReader, type RefreshedTokens } from "./session";

const BACKEND_TIMEOUT_MS = 10_000;
/** Upper bound for an image body relayed from the backend. Photos are capped at 5 MiB upstream. */
const MAX_BINARY_RESPONSE_BYTES = 8 * 1024 * 1024;

export type BackendCallInput = {
  method: string;
  segments: readonly string[];
  /** Query parameters from the browser request; validated against the per-path allow-list before forwarding. */
  search?: URLSearchParams;
  read: CookieReader;
  /** Raw request body; only forwarded for PUT and POST. */
  body?: string;
  /** Fully buffered multipart body, already bounded by the route. Retried as-is after a forced refresh. */
  upload?: { bytes: Uint8Array<ArrayBuffer>; contentType: string };
  /**
   * Only route handlers can write rotated cookies. Server Components pass false: refreshing there would consume
   * the refresh token without persisting its replacement, which would log the user out.
   */
  canRefresh: boolean;
};

type RequestPayload = { body: string | Uint8Array<ArrayBuffer>; contentType: string };

type BackendAttempt =
  | { kind: "response"; status: number; body: string; bytes?: Uint8Array<ArrayBuffer>; contentType: string | null }
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
  const payload = resolvePayload(input);
  const binary = method === "GET" && isBinaryBackendPath(path);

  const session = await resolveFreshSession(input.read, { allowRefresh: input.canRefresh });
  if (!session) {
    return { kind: "unauthenticated" };
  }

  let current = session;
  let attempt = await send(origin, target, method, current.accessToken, payload, binary);
  if (attempt.kind === "response" && attempt.status === 401 && !current.refreshed) {
    const renewed = await resolveFreshSession(input.read, { force: true, allowRefresh: input.canRefresh });
    if (!renewed) {
      return { kind: "unauthenticated" };
    }
    current = renewed;
    attempt = await send(origin, target, method, current.accessToken, payload, binary);
  }
  return { ...attempt, refreshed: current.refreshed };
}

function resolvePayload(input: BackendCallInput): RequestPayload | undefined {
  if (input.upload) {
    return { body: input.upload.bytes, contentType: input.upload.contentType };
  }
  if (input.body !== undefined) {
    return { body: input.body, contentType: "application/json" };
  }
  return undefined;
}

async function send(
  origin: string,
  target: string,
  method: BackendMethod,
  accessToken: string,
  payload: RequestPayload | undefined,
  binary: boolean,
): Promise<BackendAttempt> {
  const forwardBody = method !== "GET" && payload !== undefined;
  const headers: Record<string, string> = {
    Authorization: `Bearer ${accessToken}`,
    Accept: binary ? "image/jpeg, image/png" : "application/json",
  };
  if (forwardBody) {
    headers["Content-Type"] = payload.contentType;
  }

  try {
    const upstream = await fetch(new URL(target, origin), {
      method,
      headers,
      body: forwardBody ? payload.body : undefined,
      cache: "no-store",
      redirect: "error",
      signal: AbortSignal.timeout(BACKEND_TIMEOUT_MS),
    });
    const contentType = upstream.headers.get("content-type");
    if (binary && upstream.ok) {
      const bytes = await readBoundedBytes(upstream.body, MAX_BINARY_RESPONSE_BYTES);
      if (!bytes) {
        return { kind: "unavailable", reason: "unreachable" };
      }
      return { kind: "response", status: upstream.status, body: "", bytes, contentType };
    }
    return {
      kind: "response",
      status: upstream.status,
      body: await upstream.text(),
      contentType,
    };
  } catch (error) {
    const timedOut = error instanceof Error && (error.name === "TimeoutError" || error.name === "AbortError");
    return { kind: "unavailable", reason: timedOut ? "timeout" : "unreachable" };
  }
}
