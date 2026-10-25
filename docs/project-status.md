# Project Status

## Phase 1 — Engineering foundation
Status: complete and merged to `main` via PR #1.

GitHub Actions for Flutter, Web, and Backend all passed on the Phase 1 head. Local build results were recorded by the implementation session and were not independently rerun during this Phase 2 task.

## Phase 2 — Design system and rich UI
Status: implementation in progress on `feature/phase-2-rich-design-system`.

### Implemented in this change
- Expanded Flutter semantic colors and added shared spacing/radius tokens.
- Extended the Material theme for buttons, cards, inputs, and typography.
- Centralized entrance/route motion tokens and reduced-motion behavior.
- Replaced the bare Flutter welcome placeholder with a branded introduction, illustrated hero, CTAs, and consent-first trust card.
- Replaced the Next.js placeholder with a responsive landing page based on the Figma desktop and mobile landing frames.
- Added interactive mobile navigation, profile pass/like preview, and clear coming-soon feedback for app-store/auth actions.
- Reused the existing local brand images in the web public assets.

### Verification status
- Figma desktop/mobile screenshots were inspected as visual references.
- Branch CI must pass before merge.
- Rendered implementation screenshots have not yet been compared against Figma, so pixel-perfect visual parity is not claimed.
- Authentication and app-store download links are not wired; CTAs clearly show that they are not ready.
- The hero phone/profile artwork is composed from CSS shapes and Flutter shapes; it is illustrative UI, not a live profile or a downloaded Figma vector asset.

### Next task
Run branch CI to completion, fix build/test issues, then inspect rendered screenshots at mobile, tablet, and desktop breakpoints against Figma and tune geometry before marking Phase 2 complete.

## Phase 3 — Authentication and onboarding (AWS-native)
Status: in progress on `feature/phase-3-auth-onboarding`. Work is committed and pushed to origin; no PR has been opened or merged. Not production-ready.

### Implemented
- Backend (Spring Boot): Cognito JWT resource server; account states `PENDING_EMAIL_VERIFICATION`, `ONBOARDING_INCOMPLETE`, `ACTIVE`, `INELIGIBLE`, `SUSPENDED`; enum interested-in set (`MEN`, `WOMEN`, `NON_BINARY`, `ALL_IDENTITIES`, mutually exclusive); append-only consents with configurable draft legal versions; ineligible-account retention purge (scheduled, deletes the Cognito identity first).
- Web (Next.js): server-side Cognito auth routes; `HttpOnly` session cookies scoped to `/api`; `Secure` in production; backend proxy with allow-listed paths. No tokens in `localStorage`/`sessionStorage`.
- Mobile (Flutter): `amplify_auth_cognito` integration, route guard, and an API client. Amplify keeps tokens in native secure storage (`amplify_secure_storage` is resolved in `pubspec.lock`).
- Infrastructure (`infra/terraform/`): Cognito user pool with a mobile SRP app client and a web password app client (both refresh rotation enabled, 60-minute access tokens, 30-day refresh, configurable grace period), SES domain identity with DKIM and custom MAIL FROM, ECR repositories, CloudWatch log groups, GitHub OIDC deploy role.

### Pre-PR review (this change)
- Backend `mvnw.cmd -B verify`: 27 tests passed (adds LegalPropertiesTests 4).
- Web, Flutter, and Terraform results above were re-run on the current tree; only backend code changed afterwards.
- Fixed: the legal guard defaulted to off, so drafts were accepted in production. It now defaults to `true` (fail closed). Local `.env.example` sets `false`, and Maven surefire sets `false` for tests.
- Fixed: docs described a running Mailpit. Compose only runs Postgres, and the backend sends no application email, so Mailpit is not provisioned.
- Secret scan over the diff: no keys, tokens, or real credentials. The only match is a fake test password literal.
- Known residual: Cognito self sign-up is open at the pool level. Product access still requires server-side age, consent, and activation. Pool-level sign-up restrictions and live verification are not yet tested.

### Cognito flow and rotation fixes (PR review)
- Web sign-in uses `USER_PASSWORD_AUTH` on the new web app client. The Terraform client previously enabled only SRP, so the web sign-in would have failed. Mobile keeps SRP. See `docs/architecture.md`.
- Web refresh uses `GetTokensFromRefreshToken`, not `REFRESH_TOKEN_AUTH`. Each rotated refresh token is persisted, including when the backend call times out or is unreachable. A refresh rejection does not clear cookies on the proxy path, so a concurrent request cannot be overwritten with stale tokens. A missing refresh token in the response is treated as an error.
- Rotation grace period: `cognito_refresh_retry_grace_seconds` (default 10, range 0–60). Whether live Cognito returns a different refresh token on grace-period reuse is not yet verified.
- `ALLOW_REFRESH_TOKEN_AUTH` removed from both app clients. Rotation is incompatible with it. Mobile keeps `ALLOW_USER_SRP_AUTH` only, and web keeps `ALLOW_USER_PASSWORD_AUTH` only. Regression guards: `infra/terraform/tests/cognito_auth_flows.tftest.hcl` (`terraform test`) and `mobile/test/features/auth/data/amplify_auth_flow_test.dart`. Amplify `amplify_auth_cognito_dart` 0.11.26 is pinned, which supports rotation (0.11.14+). Live refresh against a development user pool has not been tested and needs authorization.
- Known limitation: a Server Component (`/account`) cannot refresh, so an expired access token shows a sign-in message until the user refreshes through a route handler.
- Tests added with mocked AWS SDK responses: `cognito.test.ts` (sign-in flow, refresh request, missing refresh token, rejection), `session.test.ts` (refresh outcome, cookie flags, clearing), `backend-proxy.test.ts` (rotated tokens kept on timeout and unreachable backend, no refresh for Server Components).
- Selfie or identity verification is not implemented. No profile is labelled verified.

### Verification status (actual runs, refresh-flow fix)
- Backend: `mvnw.cmd -B verify` BUILD SUCCESS, 27 tests, 0 failures.
- Web: `vitest run` 60 tests passed (8 files); `tsc --noEmit` exit 0; `eslint` exit 0; `next build` exit 0.
- Flutter: `flutter analyze` no issues; `flutter test` 46 tests passed (2 new flow-contract tests).
- Terraform 1.16.6: `fmt -check -recursive` exit 0; `init -backend=false` exit 0; `validate` valid; `terraform test` 3 passed (mocked provider, plan only; no apply, no credentials).
- No live Cognito test has run. A development user pool test has not been authorized or run.

### Verification status (earlier run before the refresh-flow fix)
- Backend: `mvnw.cmd -B verify` passed with 27 tests (AuthSchemaMigrationTests 7, BackendApplicationTests 1, IneligibleAccountPurgeTests 2, LegalPropertiesTests 4, OnboardingApiTests 13). Testcontainers PostgreSQL 17.
- Web: `npm test` 42 tests passed; `npx tsc --noEmit`, `npm run lint`, and `npm run build` exit 0. No live Cognito test has run.
- Mobile: `flutter analyze` no issues; `flutter test` 44 tests passed; `flutter build apk --debug` succeeded. iOS was not built. No live Cognito test has run.
- Terraform 1.16.6: `terraform fmt -check -recursive` exit 0; `terraform init -backend=false` exit 0; `terraform validate` valid. Nothing was planned or applied.

### Not done or outstanding
- Production legal versions and HTTPS policy URLs are not approved. Production registration must stay disabled until they are supplied (`PRITIMITRA_LEGAL_REQUIRE_APPROVED=true` enforces this).
- SES DNS records are not created. Creating them requires explicit authorization and an existing Route 53 zone ID.
- Cognito `AdminDeleteUser` (called with the `sub` as username) is unverified against a real dev user pool.
- ECS Fargate, ALB, RDS, and Secrets Manager/SSM runtime configuration are not in Terraform yet. These are a follow-up slice.
- Live Cognito sign-up/verification/refresh-rotation tests need a development user pool.
- Cost considerations before provisioning: ECS Fargate, RDS, ALB, NAT gateway, SES, and Cognito pricing by MAU.

### Next task
Complete the remaining Phase 3 automated checks (backend env example, docs). Then request authorization for a development AWS environment after reviewing the cost list. DNS records stay un-created per your instruction. Keep Terraform plan/apply blocked until a dev environment is authorized.

### Proposed DNS records (awaiting authorization; not created)
| Purpose | Type | Name | Value |
|---|---|---|---|
| SES domain verification | TXT | `_amazonses.mail.pritimitra.com` | the `verification_token` output |
| DKIM (3 records) | CNAME | `<token>._domainkey.mail.pritimitra.com` | `<token>.dkim.amazonses.com` |
| Custom MAIL FROM | MX | `bounce.mail.pritimitra.com` | `10 feedback-smtp.ap-south-1.amazonses.com` |
| Custom MAIL FROM SPF | TXT | `bounce.mail.pritimitra.com` | `v=spf1 include:amazonses.com -all` |

Set `manage_dns_records = true` and `route53_zone_id` only after authorization. Run `terraform output ses_dns_records_required` to get the exact token values.
