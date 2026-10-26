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
  { method: "GET", path: "/api/v1/profile/photos" },
  { method: "POST", path: "/api/v1/profile/photos" },
  { method: "GET", path: "/api/v1/matches" },
  { method: "GET", path: "/api/v1/conversations" },
  { method: "GET", path: "/api/v1/notifications" },
  { method: "PUT", path: "/api/v1/notifications/read-all" },
  { method: "POST", path: "/api/v1/reports" },
];

const SEGMENT_PATTERN = /^[A-Za-z0-9-]{1,64}$/;
const PHOTO_ID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_SEGMENTS = 8;

export function isBackendMethod(method: string): method is BackendMethod {
  return method === "GET" || method === "PUT" || method === "POST" || method === "DELETE";
}

/** Photo content GET and photo DELETE address one photo by UUID. The id must be a canonical UUID. */
function isPhotoTarget(method: BackendMethod, segments: readonly string[]): boolean {
  const prefixed = segments.length >= 4 && segments[0] === "v1" && segments[1] === "profile" && segments[2] === "photos";
  if (!prefixed || !PHOTO_ID_PATTERN.test(segments[3] ?? "")) {
    return false;
  }
  if (segments.length === 4) {
    return method === "DELETE";
  }
  return segments.length === 5 && segments[4] === "content" && method === "GET";
}

/**
 * Messaging, notification and block routes that address one resource by its opaque public id.
 * Segment shape is checked here; the id itself is checked by SEGMENT_PATTERN in resolveBackendPath.
 */
function isMessagingTarget(method: BackendMethod, segments: readonly string[]): boolean {
  if (segments[0] !== "v1" || segments.length < 3) {
    return false;
  }
  const resource = segments[1];
  const action = segments[3];
  switch (resource) {
    case "matches":
      return segments.length === 3 && method === "DELETE";
    case "conversations":
      if (segments.length === 4 && action === "messages") {
        return method === "GET" || method === "POST";
      }
      return segments.length === 4 && action === "read" && method === "PUT";
    case "notifications":
      return segments.length === 4 && action === "read" && method === "PUT";
    case "blocks":
      return segments.length === 3 && (method === "PUT" || method === "DELETE");
    default:
      return false;
  }
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
  if (isLikeTarget) {
    return path;
  }
  return isPhotoTarget(method, segments) || isMessagingTarget(method, segments) ? path : null;
}

/** True for the photo content path, whose successful response is an image body rather than JSON. */
export function isBinaryBackendPath(path: string): boolean {
  return /^\/api\/v1\/profile\/photos\/[0-9a-f-]{36}\/content$/i.test(path);
}

/** True for the photo upload path, which accepts a multipart body instead of JSON. */
export function isMultipartBackendRoute(method: string, path: string): boolean {
  return method === "POST" && path === "/api/v1/profile/photos";
}


const GOAL_VALUES = ["DATING", "FRIENDSHIP", "COMMUNITY", "MEET_PEOPLE", "OPEN_TO_CONNECTION"] as const;

/** Cursor and page size rules shared by every paged list endpoint. */
const PAGED_RULES: Readonly<Record<string, RegExp>> = {
  cursor: /^[A-Za-z0-9_-]{1,128}$/,
  size: /^(?:[1-9]|[1-4][0-9]|50)$/,
};

/** Conversation message history addresses one conversation by its public id. */
const MESSAGE_HISTORY_PATH = /^\/api\/v1\/conversations\/[A-Za-z0-9-]{1,64}\/messages$/;

/** Per-path query parameter rules. Any parameter not listed here is rejected. */
const QUERY_RULES: Readonly<Record<string, Readonly<Record<string, RegExp>>>> = {
  "/api/v1/discovery": {
    ...PAGED_RULES,
    goal: new RegExp(`^(?:${GOAL_VALUES.join("|")})$`),
  },
  "/api/v1/matches": PAGED_RULES,
  "/api/v1/conversations": PAGED_RULES,
  "/api/v1/notifications": PAGED_RULES,
};

function queryRulesFor(path: string): Readonly<Record<string, RegExp>> {
  if (MESSAGE_HISTORY_PATH.test(path)) {
    return PAGED_RULES;
  }
  return QUERY_RULES[path] ?? {};
}

/**
 * Validates the query string for an allow-listed path and returns a normalized query (without "?"), or null
 * when any key is unknown, repeated or malformed. Paths without rules accept no query parameters at all.
 */
export function resolveBackendQuery(path: string, search: URLSearchParams): string | null {
  const rules = queryRulesFor(path);

  const normalized = new URLSearchParams();
  const seen = new Set<string>();
  for (const [key, value] of search) {
    const rule = Object.prototype.hasOwnProperty.call(rules, key) ? rules[key] : undefined;
    if (!rule || seen.has(key) || !rule.test(value)) {
      return null;
    }
    seen.add(key);
    normalized.set(key, value);
  }
  return normalized.toString();
}

