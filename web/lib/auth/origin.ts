export type StateChangeCheck = { ok: true } | { ok: false; reason: "origin" | "content_type" };

/** True only when the Origin header is present and matches the request's own origin. */
export function isSameOrigin(headers: Headers, requestOrigin: string): boolean {
  const origin = headers.get("origin");
  if (!origin) {
    return false;
  }
  try {
    return new URL(origin).origin === requestOrigin;
  } catch {
    return false;
  }
}

export type AcceptedBodyType = "application/json" | "multipart/form-data";

function hasContentType(headers: Headers, expected: AcceptedBodyType): boolean {
  const value = headers.get("content-type");
  if (!value) {
    return false;
  }
  return value.split(";")[0]?.trim().toLowerCase() === expected;
}

export function hasJsonContentType(headers: Headers): boolean {
  return hasContentType(headers, "application/json");
}

/** CSRF gate for every state-changing request: same-origin Origin header and the accepted body content type. */
export function checkStateChange(
  headers: Headers,
  requestOrigin: string,
  accepted: AcceptedBodyType = "application/json",
): StateChangeCheck {
  if (!isSameOrigin(headers, requestOrigin)) {
    return { ok: false, reason: "origin" };
  }
  if (!hasContentType(headers, accepted)) {
    return { ok: false, reason: "content_type" };
  }
  return { ok: true };
}
