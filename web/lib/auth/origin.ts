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

export function hasJsonContentType(headers: Headers): boolean {
  const value = headers.get("content-type");
  if (!value) {
    return false;
  }
  return value.split(";")[0]?.trim().toLowerCase() === "application/json";
}

/** CSRF gate for every state-changing request: same-origin Origin header and JSON content type. */
export function checkStateChange(headers: Headers, requestOrigin: string): StateChangeCheck {
  if (!isSameOrigin(headers, requestOrigin)) {
    return { ok: false, reason: "origin" };
  }
  if (!hasJsonContentType(headers)) {
    return { ok: false, reason: "content_type" };
  }
  return { ok: true };
}
