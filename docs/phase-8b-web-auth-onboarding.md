# Phase 8B — Web authentication and onboarding

## Scope delivered in this slice

- Adds public sign-in, sign-up, email-confirmation, resend-code, forgot-password, and reset-password screens.
- Reuses the existing Next.js Cognito route handlers and keeps token handling in HttpOnly server-side session cookies.
- Routes a successful sign-in using the backend's account status: ACTIVE to Home, ONBOARDING_INCOMPLETE to onboarding, and unknown/restricted states to the account status surface.
- Adds a guided onboarding flow for age verification, required legal consent, display profile and connection goals.
- Adds a public legal-policy metadata endpoint. It exposes only configured policy versions and HTTPS URLs; onboarding does not proceed until both policies have non-draft versions and valid HTTPS links. This prevents draft policies from being mistaken for approved production terms.
- Adds unit tests for the account-state redirect policy and responsive, reduced-motion-aware styling for the public flows.

## Boundaries

- UI routes and mockable HTTP contracts are implemented in code, but this PR must still pass GitHub Actions before it is considered validated.
- Live Cognito verification, production Terms/Privacy approval and URLs, and authenticated browser/device acceptance remain environment-level gates.
- Account deletion and consent withdrawal API work remain separate Phase 7 work; this slice does not claim those features are complete.
- No AWS resources, DNS, secrets, data migrations or production deployments are changed by this slice.

## Acceptance

- Web Vitest, lint and production build pass on the exact PR head.
- Sign-in lands according to the server-reported account status.
- Errors stay generic and do not display raw server/provider output.
- Confirmation and recovery pages use the existing non-enumerating API responses.
- Onboarding is blocked when approved legal configuration is absent.
