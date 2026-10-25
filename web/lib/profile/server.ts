import { cookies } from "next/headers";
import { callBackend } from "@/lib/auth/backend-proxy";

export type ProtectedLoad =
  | { kind: "ok"; body: unknown }
  | { kind: "not-eligible" }
  | { kind: "signed-out" }
  | { kind: "unavailable" };

/**
 * Reads a protected backend GET from a Server Component. Refresh is disabled because Server Components
 * cannot persist rotated cookies; an expired session therefore falls back to the sign-in prompt.
 */
export async function loadProtectedJson(segments: string[]): Promise<ProtectedLoad> {
  let result;
  try {
    result = await callBackend({ method: "GET", segments, read: await cookies(), canRefresh: false });
  } catch {
    return { kind: "unavailable" };
  }
  if (result.kind === "unauthenticated") {
    return { kind: "signed-out" };
  }
  if (result.kind !== "response") {
    return { kind: "unavailable" };
  }
  if (result.status === 401) {
    return { kind: "signed-out" };
  }
  // 403 means the account is not ACTIVE; 409 means the profile is incomplete.
  if (result.status === 403 || result.status === 409) {
    return { kind: "not-eligible" };
  }
  if (result.status !== 200) {
    return { kind: "unavailable" };
  }
  try {
    return { kind: "ok", body: JSON.parse(result.body) };
  } catch {
    return { kind: "unavailable" };
  }
}
