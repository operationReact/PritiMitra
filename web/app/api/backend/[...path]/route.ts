import { NextResponse } from "next/server";
import { cookies } from "next/headers";
import { authErrorResponse, readBoundedBytes, readBoundedText, rejectUnsafeRequest, withSessionCookies } from "@/lib/auth/http";
import { callBackend } from "@/lib/auth/backend-proxy";
import { isMultipartBackendRoute, resolveBackendPath } from "@/lib/auth/proxy-policy";
import { clearSessionCookies, type CookieWriter } from "@/lib/auth/session";

const MAX_PROXY_BODY_BYTES = 64 * 1024;
/** Five MiB photo plus multipart framing. The backend enforces the same photo limit and its own request cap. */
const MAX_PHOTO_UPLOAD_BYTES = 5 * 1024 * 1024 + 128 * 1024;
const MULTIPART_CONTENT_TYPE = /^multipart\/form-data; boundary=[A-Za-z0-9'()+_,./:=?-]{1,70}$/i;
const RELAYED_IMAGE_TYPES = new Set(["image/jpeg", "image/png"]);

type RouteContext = { params: Promise<{ path: string[] }> };

async function proxy(request: Request, context: RouteContext): Promise<NextResponse> {
  const { path } = await context.params;
  const method = request.method;

  const upstreamPath = resolveBackendPath(method, path);
  const multipart = upstreamPath !== null && isMultipartBackendRoute(method, upstreamPath);
  let body: string | undefined;
  let upload: { bytes: Uint8Array<ArrayBuffer>; contentType: string } | undefined;
  if (method !== "GET") {
    const rejected = rejectUnsafeRequest(request, multipart ? "multipart/form-data" : "application/json");
    if (rejected) {
      return rejected;
    }
  }
  if (multipart) {
    const contentType = request.headers.get("content-type") ?? "";
    if (!MULTIPART_CONTENT_TYPE.test(contentType)) {
      return authErrorResponse(415, "unsupported_media_type");
    }
    const declared = request.headers.get("content-length");
    if (declared !== null && (!/^\d+$/.test(declared) || Number(declared) > MAX_PHOTO_UPLOAD_BYTES)) {
      return authErrorResponse(413, "payload_too_large");
    }
    const bytes = await readBoundedBytes(request.body, MAX_PHOTO_UPLOAD_BYTES);
    if (!bytes) {
      return authErrorResponse(413, "payload_too_large");
    }
    upload = { bytes, contentType };
  }
  if (!multipart && (method === "PUT" || method === "POST")) {
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
    result = await callBackend({
      method,
      segments: path,
      search: new URL(request.url).searchParams,
      read: await cookies(),
      body,
      upload,
      canRefresh: true,
    });
  } catch {
    return authErrorResponse(503, "auth_unavailable");
  }

  if (result.kind === "not_allowed") {
    return authErrorResponse(404, "not_found");
  }
  if (result.kind === "invalid_query") {
    return authErrorResponse(400, "invalid_request");
  }
  if (result.kind === "unauthenticated") {
    return authErrorResponse(401, "session_expired");
  }
  if (result.kind === "unavailable") {
    const failure = authErrorResponse(
      result.reason === "timeout" ? 504 : 502,
      result.reason === "timeout" ? "backend_timeout" : "backend_unavailable",
    );
    return result.refreshed ? withSessionCookies(failure, result.refreshed) : failure;
  }
  if (result.status === 401) {
    const response = authErrorResponse(401, "session_expired");
    clearSessionCookies(response.cookies as CookieWriter);
    return response;
  }

  const response = result.bytes
    ? relayImage(result.status, result.bytes, result.contentType)
    : new NextResponse(result.body, {
        status: result.status,
        headers: {
          "Content-Type": result.contentType ?? "application/json",
          "Cache-Control": "no-store",
        },
      });
  return result.refreshed ? withSessionCookies(response, result.refreshed) : response;
}

/** Relays a stored photo only when the upstream type is one the upload pipeline produces. */
function relayImage(status: number, bytes: Uint8Array<ArrayBuffer>, contentType: string | null): NextResponse {
  const type = contentType?.split(";")[0]?.trim().toLowerCase() ?? "";
  if (!RELAYED_IMAGE_TYPES.has(type)) {
    return authErrorResponse(502, "backend_unavailable");
  }
  return new NextResponse(new Blob([bytes], { type }), {
    status,
    headers: {
      "Content-Type": type,
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    },
  });
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

export function DELETE(request: Request, context: RouteContext): Promise<NextResponse> {
  return proxy(request, context);
}
