import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { authErrorResponse, readBoundedText, rejectUnsafeRequest, withSessionCookies } from "@/lib/auth/http";
import { BackendTimeoutError, BackendUnavailableError, callBackend } from "@/lib/auth/backend-proxy";
import { clearSessionCookies, type CookieWriter } from "@/lib/auth/session";

const MAX_PROXY_BODY_BYTES = 64 * 1024;

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: Request, context: RouteContext): Promise<NextResponse> {
  const { path } = await context.params;
  const method = request.method;

  let body: string | undefined;
  if (method === "PUT" || method === "POST") {
    const rejected = rejectUnsafeRequest(request);
    if (rejected) {
      return rejected;
    }
    const text = await readBoundedText(request, MAX_PROXY_BODY_BYTES);
    if (text === null) {
      return authErrorResponse(413, "payload_too_large");
    }
    if (text.length > 0) {
      try {
        JSON.parse(text);
      } catch {
        return authErrorResponse(400, "invalid_request");
      }
      body = text;
    }
  }

  let result;
  try {
    result = await callBackend({ method, segments: path, read: await cookies(), body });
  } catch (error) {
    if (error instanceof BackendTimeoutError) {
      return authErrorResponse(504, "backend_timeout");
    }
    if (error instanceof BackendUnavailableError) {
      return authErrorResponse(502, "backend_unavailable");
    }
    return authErrorResponse(503, "auth_unavailable");
  }

  if (result.kind === "not_allowed") {
    return authErrorResponse(404, "not_found");
  }
  if (result.kind === "unauthenticated") {
    const response = authErrorResponse(401, "session_expired");
    clearSessionCookies(response.cookies as CookieWriter);
    return response;
  }
  if (result.status === 401) {
    const response = authErrorResponse(401, "session_expired");
    clearSessionCookies(response.cookies as CookieWriter);
    return response;
  }

  const response = new NextResponse(result.body, {
    status: result.status,
    headers: {
      "Content-Type": result.contentType ?? "application/json",
      "Cache-Control": "no-store",
    },
  });
  return result.refreshed ? withSessionCookies(response, result.refreshed) : response;
}

export function GET(request: Request, context: RouteContext): Promise<NextResponse> {
  return proxy(request, context);
}

export function PUT(request: Request, context: RouteContext): Promise<NextResponse> {
  return proxy(request, context);
}

export function POST(request: Request, context: RouteContext): Promise<NextResponse> {
  return proxy(request, context);
}
