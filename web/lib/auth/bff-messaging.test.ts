import { beforeEach, describe, expect, it, vi } from "vitest";

const callBackend = vi.hoisted(() => vi.fn());

vi.mock("@/lib/auth/backend-proxy", () => ({ callBackend }));
vi.mock("next/headers", () => ({
  cookies: async () => ({ get: () => undefined, getAll: () => [], has: () => false }),
}));

import * as route from "../../app/api/backend/[...path]/route";

const ORIGIN = "https://pritimitra.example";
const CONVERSATION_ID = "0f8fad5b-d9cb-469f-a165-70867728950e";
const CLIENT_MESSAGE_ID = "6b1d3c2a-5e4f-4a3b-8c2d-1e0f9a8b7c6d";

function context(...segments: string[]) {
  return { params: Promise.resolve({ path: segments }) };
}

function sendRequest(init: { origin?: string | null; contentType?: string; body?: string; path?: string } = {}) {
  const headers = new Headers();
  if (init.origin !== null) {
    headers.set("origin", init.origin ?? ORIGIN);
  }
  headers.set("content-type", init.contentType ?? "application/json");
  const body = init.body ?? JSON.stringify({ clientMessageId: CLIENT_MESSAGE_ID, body: "Hello" });
  headers.set("content-length", String(new TextEncoder().encode(body).byteLength));
  return new Request(`${ORIGIN}/api/backend/${init.path ?? `v1/conversations/${CONVERSATION_ID}/messages`}`, {
    method: "POST",
    headers,
    body,
  });
}

beforeEach(() => {
  callBackend.mockReset();
});

describe("messaging BFF route", () => {
  it("forwards a same-origin validated send to the backend", async () => {
    callBackend.mockResolvedValue({
      kind: "response",
      status: 201,
      body: '{"messageId":"m1"}',
      contentType: "application/json",
    });

    const response = await route.POST(sendRequest(), context("v1", "conversations", CONVERSATION_ID, "messages"));

    expect(response.status).toBe(201);
    expect(callBackend).toHaveBeenCalledTimes(1);
    expect(callBackend.mock.calls[0][0]).toMatchObject({ method: "POST", body: expect.stringContaining("Hello") });
  });

  it("rejects a cross-origin send before reaching the backend", async () => {
    const response = await route.POST(
      sendRequest({ origin: "https://evil.example" }),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(403);
    expect(await response.json()).toEqual({ error: "csrf_rejected" });
    expect(callBackend).not.toHaveBeenCalled();
  });

  it("rejects a send with a missing Origin header", async () => {
    const response = await route.POST(
      sendRequest({ origin: null }),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(403);
    expect(callBackend).not.toHaveBeenCalled();
  });

  it("rejects a same-origin send that is not JSON", async () => {
    const response = await route.POST(
      sendRequest({ contentType: "text/plain" }),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(415);
    expect(callBackend).not.toHaveBeenCalled();
  });

  it("rejects a malformed conversation id without calling the backend", async () => {
    const response = await route.POST(
      sendRequest({ path: "v1/conversations/..%2Fadmin/messages" }),
      context("v1", "conversations", "..%2Fadmin", "messages"),
    );

    expect(response.status).toBe(404);
    expect(callBackend).not.toHaveBeenCalled();
  });

  it("rejects an oversized send body before reaching the backend", async () => {
    const oversized = JSON.stringify({ clientMessageId: CLIENT_MESSAGE_ID, body: "a".repeat(70 * 1024) });
    const response = await route.POST(
      sendRequest({ body: oversized }),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(413);
    expect(callBackend).not.toHaveBeenCalled();
  });

  it("rejects a malformed JSON send body", async () => {
    const response = await route.POST(
      sendRequest({ body: "{not json" }),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(400);
    expect(callBackend).not.toHaveBeenCalled();
  });

  it("returns 401 and does not relay data for an unauthenticated request", async () => {
    callBackend.mockResolvedValue({ kind: "unauthenticated" });

    const response = await route.GET(
      new Request(`${ORIGIN}/api/backend/v1/conversations/${CONVERSATION_ID}/messages`),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(401);
    expect(await response.json()).toEqual({ error: "session_expired" });
  });

  it("relays the backend CONVERSATION_CLOSED response for a blocked or unmatched conversation", async () => {
    const problem = '{"code":"CONVERSATION_CLOSED","title":"Conversation closed"}';
    callBackend.mockResolvedValue({ kind: "response", status: 409, body: problem, contentType: "application/problem+json" });

    const response = await route.POST(sendRequest(), context("v1", "conversations", CONVERSATION_ID, "messages"));

    expect(response.status).toBe(409);
    expect(await response.text()).toBe(problem);
  });

  it("denies a non-allow-listed conversation action even with a valid id", async () => {
    const response = await route.DELETE(
      new Request(`${ORIGIN}/api/backend/v1/conversations/${CONVERSATION_ID}/messages`, {
        method: "DELETE",
        headers: { origin: ORIGIN },
      }),
      context("v1", "conversations", CONVERSATION_ID, "messages"),
    );

    expect(response.status).toBe(404);
    expect(callBackend).not.toHaveBeenCalled();
  });
});
