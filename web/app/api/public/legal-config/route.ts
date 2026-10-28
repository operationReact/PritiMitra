import { NextResponse } from "next/server";

function httpsUrl(value: string | undefined): string | null {
  if (!value) return null;
  try {
    const url = new URL(value);
    return url.protocol === "https:" ? url.toString() : null;
  } catch {
    return null;
  }
}

/** Public, non-secret policy metadata. Approved URLs and versions must be configured per environment. */
export function GET(): NextResponse {
  const termsVersion = process.env.PRITIMITRA_TERMS_VERSION?.trim() || "terms-draft-v1";
  const privacyVersion = process.env.PRITIMITRA_PRIVACY_VERSION?.trim() || "privacy-draft-v1";
  const termsUrl = httpsUrl(process.env.PRITIMITRA_TERMS_URL);
  const privacyUrl = httpsUrl(process.env.PRITIMITRA_PRIVACY_URL);
  const ready = Boolean(
    termsUrl && privacyUrl &&
    termsVersion && privacyVersion &&
    !/draft/i.test(termsVersion) && !/draft/i.test(privacyVersion),
  );
  return NextResponse.json(
    { termsVersion, privacyVersion, termsUrl, privacyUrl, ready },
    { headers: { "Cache-Control": "no-store" } },
  );
}
