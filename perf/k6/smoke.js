import { validateTarget } from "./target.mjs";
import http from "k6/http";
import { check, sleep } from "k6";

const { baseUrl } = validateTarget(__ENV.K6_BASE_URL, __ENV.K6_TARGET_ENV);

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
