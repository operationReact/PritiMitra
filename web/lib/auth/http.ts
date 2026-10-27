import { NextResponse } from "next/server";
import { AuthConfigError, isProductionRuntime } from "./config";
import { checkStateChange, type AcceptedBodyType } from "./origin";
import { errorName, mapCognitoError, type AuthErrorCode, type AuthOperation } from "./errors";
import { setSessionCookies, type CookieWriter } from "./session";

export const DEFAULT_MAX_BODY_BYTES = 16 * 1024;

const NO_STORE = { "Cache-Control": "no-store" };

export function authErrorResponse(status: number, code: AuthErrorCode): NextResponse {
  return NextResponse.json({ error: code }, { status, headers: NO_STORE });
}

export function authResponse(body: Record<string, unknown>, status = 200): NextResponse {
  return NextResponse.json(body, { status, headers: NO_STORE });
}

/** Maps a thrown Cognito or configuration error to a generic response. Only the error name is logged. */
export function failureResponse(
  error: unknown,
  operation: AuthOperation,
  generic?: { status: number; code: AuthErrorCode },
): NextResponse {
  if (error instanceof AuthConfigError) {
    console.error(`[auth] ${operation}: configuration error`);
    return authErrorResponse(503, "auth_unavailable");
  }
  const name = errorName(error);
  if (generic) {
    console.warn(`[auth] ${operation}: ${name ?? "unknown"}`);
    return authErrorResponse(generic.status, generic.code);
  }
  console.warn(`[auth] ${operation}: ${name ?? "unknown"}`);
  const mapped = mapCognitoError(name, operation);
  return authErrorResponse(mapped.status, mapped.code);
}

/** Returns an error response when the request fails the CSRF gate, or null when it may proceed. */
export function rejectUnsafeRequest(
  request: Request,
  accepted: AcceptedBodyType = "application/json",
): NextResponse | null {
  const result = checkStateChange(request.headers, new URL(request.url).origin, accepted);
  if (result.ok) {
    return null;
  }
  return result.reason === "origin"
    ? authErrorResponse(403, "csrf_rejected")
    : authErrorResponse(415, "unsupported_media_type");
}

export type JsonBodyResult =
  | { ok: true; value: Record<string, unknown> }
  | { ok: false; response: NextResponse };

/** Reads a bounded JSON object body. Rejects oversized, malformed, or non-object payloads. */
export async function readJsonObject(request: Request, maxBytes = DEFAULT_MAX_BODY_BYTES): Promise<JsonBodyResult> {
  const declared = request.headers.get("content-length");
  if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > maxBytes)) {
    return { ok: false, response: authErrorResponse(413, "payload_too_large") };
  }
  const text = await readBoundedText(request, maxBytes);
  if (text === null) {
    return { ok: false, response: authErrorResponse(413, "payload_too_large") };
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(text);
  } catch {
    return { ok: false, response: authErrorResponse(400, "invalid_request") };
  }
  if (typeof parsed !== "object" || parsed === null || Array.isArray(parsed)) {
    return { ok: false, response: authErrorResponse(400, "invalid_request") };
  }
  return { ok: true, value: parsed as Record<string, unknown> };
}

/** Returns the body text, or null when it exceeds maxBytes. */
export async function readBoundedText(request: Request, maxBytes: number): Promise<string | null> {
  if (!request.body) {
    return "";
  }
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  return new TextDecoder().decode(Buffer.concat(chunks));
}

/** Reads a stream into memory, or returns null as soon as it exceeds maxBytes. */
export async function readBoundedBytes(
  stream: ReadableStream<Uint8Array> | null,
  maxBytes: number,
): Promise<Uint8Array<ArrayBuffer> | null> {
  if (!stream) {
    return new Uint8Array(0);
  }
  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    total += value.byteLength;
    if (total > maxBytes) {
      await reader.cancel();
      return null;
    }
    chunks.push(value);
  }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return bytes;
}

/** Attaches refreshed or newly issued session cookies to a response. */
export function withSessionCookies(
  response: NextResponse,
  tokens: { accessToken: string; refreshToken: string; expiresIn?: number },
): NextResponse {
  setSessionCookies(response.cookies as CookieWriter, tokens, isProductionRuntime());
  return response;
}
