/**
 * Reads the `exp` claim without verifying the signature. This is only a refresh-timing hint;
 * the backend verifies every token before trusting it.
 */
export function readUnverifiedExpiry(jwt: string): number | undefined {
  const parts = jwt.split(".");
  if (parts.length !== 3) {
    return undefined;
  }
  try {
    const payload = JSON.parse(Buffer.from(parts[1], "base64url").toString("utf8")) as { exp?: unknown };
    return typeof payload.exp === "number" ? payload.exp : undefined;
  } catch {
    return undefined;
  }
}

export function needsRefresh(accessToken: string | undefined, nowSeconds: number, skewSeconds = 60): boolean {
  if (!accessToken) {
    return true;
  }
  const expiry = readUnverifiedExpiry(accessToken);
  if (expiry === undefined) {
    return true;
  }
  return expiry - skewSeconds <= nowSeconds;
}
