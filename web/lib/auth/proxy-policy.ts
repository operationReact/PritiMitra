export type BackendMethod = "GET" | "PUT" | "POST" | "DELETE";

type AllowedRoute = { method: BackendMethod; path: string };

/** Exact method and path pairs that the browser may reach through /api/backend. Default-deny. */
const ALLOWED_ROUTES: readonly AllowedRoute[] = [
  { method: "GET", path: "/api/v1/me" },
  { method: "GET", path: "/api/v1/onboarding" },
  { method: "PUT", path: "/api/v1/onboarding/age-verification" },
  { method: "PUT", path: "/api/v1/onboarding/consents" },
  { method: "PUT", path: "/api/v1/onboarding/profile" },
  { method: "POST", path: "/api/v1/onboarding/complete" },
  { method: "GET", path: "/api/v1/profile" },
  { method: "PUT", path: "/api/v1/profile/discoverability" },
  { method: "GET", path: "/api/v1/discovery" },
];

const SEGMENT_PATTERN = /^[A-Za-z0-9-]{1,64}$/;
const MAX_SEGMENTS = 8;

export function isBackendMethod(method: string): method is BackendMethod {
  return method === "GET" || method === "PUT" || method === "POST" || method === "DELETE";
}

/**
 * Maps catch-all segments (for example ["v1", "me"]) to an upstream path under /api/v1/.
 * Segments must be plain tokens, which rejects encoded slashes, dot segments and query-like input.
 * Returns null when the method or path is not allow-listed.
 */
export function resolveBackendPath(method: string, segments: readonly string[]): string | null {
  if (!isBackendMethod(method)) {
    return null;
  }
  if (segments.length === 0 || segments.length > MAX_SEGMENTS) {
    return null;
  }
  if (!segments.every((segment) => SEGMENT_PATTERN.test(segment))) {
    return null;
  }
  const path = `/api/${segments.join("/")}`;
  const allowed = ALLOWED_ROUTES.some((route) => route.method === method && route.path === path);
  if (allowed) {
    return path;
  }
  // Like and unlike address one profile by its public id: PUT or DELETE /api/v1/likes/{publicId}.
  const isLikeTarget =
    segments.length === 3 && segments[0] === "v1" && segments[1] === "likes" && (method === "PUT" || method === "DELETE");
  return isLikeTarget ? path : null;
}

