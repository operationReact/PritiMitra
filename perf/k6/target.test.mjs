import test from "node:test";
import assert from "node:assert/strict";
import { validateTarget } from "./target.mjs";

test("requires an explicit URL and environment", () => {
  assert.throws(() => validateTarget("", "staging"), /K6_BASE_URL is required/);
  assert.throws(() => validateTarget("https://api.staging.example.test", ""), /K6_TARGET_ENV/);
  assert.throws(() => validateTarget("not-a-url", "staging"), /absolute URL/);
});

test("blocks all PritiMitra production domains and their subdomains", () => {
  for (const host of [
    "pritimitra.com",
    "api.pritimitra.com",
    "pritimitra.in",
    "www.pritimitra.org",
    "pritimitra.online",
  ]) {
    assert.throws(() => validateTarget(`https://${host}`, "staging"), /production PritiMitra hosts are blocked/);
  }
});

test("local mode only accepts loopback", () => {
  assert.equal(validateTarget("http://127.0.0.1:8080", "local").baseUrl, "http://127.0.0.1:8080");
  assert.equal(validateTarget("http://localhost:8080/", "local").baseUrl, "http://localhost:8080");
  assert.throws(() => validateTarget("https://api.staging.example.test", "local"), /only localhost or loopback/);
});

test("staging requires HTTPS and rejects loopback", () => {
  assert.equal(validateTarget("https://api.staging.example.test", "staging").targetEnv, "staging");
  assert.throws(() => validateTarget("http://api.staging.example.test", "staging"), /require HTTPS/);
  assert.throws(() => validateTarget("http://127.0.0.1:8080", "staging"), /require HTTPS/);
  assert.throws(() => validateTarget("https://localhost", "staging"), /may not target loopback/);
});

test("rejects URL credentials, paths, query parameters, and fragments", () => {
  for (const url of [
    "https://user:pass@api.staging.example.test",
    "https://api.staging.example.test/api",
    "https://api.staging.example.test?debug=1",
    "https://api.staging.example.test#fragment",
  ]) {
    assert.throws(() => validateTarget(url, "staging"), /bare origin/);
  }
});
