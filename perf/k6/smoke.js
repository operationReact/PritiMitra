import http from "k6/http";
import { check, sleep } from "k6";

/**
 * Low-intensity release smoke test for a LOCAL or STAGING API only.
 * Required: K6_BASE_URL and K6_TARGET_ENV=local|staging.
 * The guards intentionally fail before sending traffic to production hosts.
 */
const baseUrl = (__ENV.K6_BASE_URL || "").replace(/\/+$/, "");
const targetEnv = (__ENV.K6_TARGET_ENV || "").toLowerCase();

if (!baseUrl) {
  throw new Error("K6_BASE_URL is required; no traffic has been sent.");
}
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
const productionDomains = [
  "pritimitra.com",
  "pritimitra.in",
  "pritimitra.org",
  "pritimitra.online",
];
if (productionDomains.some((domain) => hostname === domain || hostname.endsWith("." + domain))) {
  throw new Error("Production PritiMitra hosts are blocked by this smoke test.");
}

const localHosts = ["localhost", "127.0.0.1", "::1"];
if (targetEnv === "local") {
  if (!localHosts.includes(hostname) || !["http:", "https:"].includes(parsed.protocol)) {
    throw new Error("Local tests may target only localhost or loopback.");
  }
} else if (parsed.protocol !== "https:") {
  throw new Error("Staging tests require HTTPS.");
}

export const options = {
  stages: [
    { duration: "10s", target: 1 },
    { duration: "20s", target: 5 },
    { duration: "10s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    checks: ["rate>0.99"],
    "http_req_duration{endpoint:health}": ["p(95)<750"],
    "http_req_duration{endpoint:protected-api}": ["p(95)<750"],
  },
};

export default function () {
  const health = http.get(`${baseUrl}/actuator/health`, {
    tags: { endpoint: "health" },
    timeout: "5s",
  });
  check(health, {
    "health endpoint returns 200": (response) => response.status === 200,
  });

  // Without a bearer token, protected APIs must fail closed. This verifies the auth boundary
  // without using real user credentials or mutating application data.
  const protectedResponse = http.get(`${baseUrl}/api/v1/me`, {
    tags: { endpoint: "protected-api" },
    timeout: "5s",
  });
  check(protectedResponse, {
    "protected profile endpoint rejects anonymous access": (response) => response.status === 401,
  });

  sleep(1);
}
