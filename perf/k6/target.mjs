const PRODUCTION_DOMAINS = [
  "pritimitra.com",
  "pritimitra.in",
  "pritimitra.org",
  "pritimitra.online",
];
const LOOPBACK_HOSTS = new Set(["localhost", "127.0.0.1", "::1", "[::1]"]);

/**
 * Validate an explicit non-production target before any request is sent.
 * This is a guardrail, not a substitute for choosing an authorized staging endpoint.
 */
export function validateTarget(baseUrlValue, targetEnvValue) {
  const baseUrl = String(baseUrlValue || "").replace(/\/+$/, "");
  const targetEnv = String(targetEnvValue || "").toLowerCase();

  if (!baseUrl) throw new Error("K6_BASE_URL is required; no traffic has been sent.");
  if (!["local", "staging"].includes(targetEnv)) {
    throw new Error("K6_TARGET_ENV must be exactly 'local' or 'staging'.");
  }

  let parsed;
  try {
    parsed = new URL(baseUrl);
  } catch {
    throw new Error("K6_BASE_URL must be an absolute URL.");
  }

  const hostname = parsed.hostname.toLowerCase();
  if (PRODUCTION_DOMAINS.some((domain) => hostname === domain || hostname.endsWith("." + domain))) {
    throw new Error("Production PritiMitra hosts are blocked by this smoke test.");
  }
  if (parsed.username || parsed.password || parsed.search || parsed.hash || parsed.pathname !== "/") {
    throw new Error("K6_BASE_URL must be a bare origin without credentials, path, query, or fragment.");
  }

  if (targetEnv === "local") {
    if (!LOOPBACK_HOSTS.has(hostname) || !["http:", "https:"].includes(parsed.protocol)) {
      throw new Error("Local tests may target only localhost or loopback.");
    }
  } else {
    if (parsed.protocol !== "https:") {
      throw new Error("Staging tests require HTTPS.");
    }
    if (LOOPBACK_HOSTS.has(hostname)) {
      throw new Error("Staging tests may not target loopback hosts.");
    }
  }

  return { baseUrl, targetEnv };
}
