# Project Status

## Phase 1 — Engineering foundation
Status: complete and merged to `main` via PR #1.

GitHub Actions for Flutter, Web, and Backend all passed on the Phase 1 head. Local build results were recorded by the implementation session and were not independently rerun during this Phase 2 task.

## Phase 2 — Design system and rich UI
Status: foundation merged to `main` via PR #2. Visual parity against the approved Figma frames remains unverified.

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

### Remaining acceptance work
Inspect rendered screenshots at mobile, tablet, and desktop breakpoints against the approved Figma frames and tune geometry. PR #2 is merged, but visual parity is not claimed until that comparison is complete.

## Phase 3 — Authentication and onboarding (AWS-native)
Status: the authentication/onboarding foundation was merged to `main` via PR #3. Phase 3 remains incomplete and is not production-ready.

### Implemented
- Backend (Spring Boot): Cognito JWT resource server; account states `PENDING_EMAIL_VERIFICATION`, `ONBOARDING_INCOMPLETE`, `ACTIVE`, `INELIGIBLE`, `SUSPENDED`; enum interested-in set (`MEN`, `WOMEN`, `NON_BINARY`, `ALL_IDENTITIES`, mutually exclusive); append-only consents with configurable draft legal versions; ineligible-account retention purge (scheduled, deletes the Cognito identity first).
- Web (Next.js): server-side Cognito auth routes; `HttpOnly` session cookies scoped to `/api`; `Secure` in production; backend proxy with allow-listed paths. No tokens in `localStorage`/`sessionStorage`.
- Mobile (Flutter): `amplify_auth_cognito` integration, route guard, and an API client. Amplify keeps tokens in native secure storage (`amplify_secure_storage` is resolved in `pubspec.lock`).
- Infrastructure (`infra/terraform/`): Cognito user pool with a mobile SRP app client and a web password app client (both refresh rotation enabled, 60-minute access tokens, 30-day refresh, configurable grace period), SES domain identity with DKIM and custom MAIL FROM, ECR repositories, CloudWatch log groups, GitHub OIDC deploy role.

### Implementation and review history (PR #3)
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

### Onboarding Step 1 (commit 0b99f6a, WIP)
- Added `GET /api/v1/onboarding/profile` and the Flutter onboarding flow (date of birth, Terms and Privacy consent, goals, profile, review) with loading, failure and retry states.
- Verified: `mvnw.cmd verify` BUILD SUCCESS, 27 tests, including 13 `OnboardingApiTests` (Docker Desktop was required for the Testcontainers-backed tests). `flutter analyze lib test` no issues. `flutter test test/features test/core` 52 passed.
- Not done: web onboarding and auth pages and BFF routes, screenshots, and Figma comparison. No visual parity is claimed.

### Not done or outstanding
- Web onboarding and auth pages (Step 1 web half), Step 2 remaining auth screens, and Step 3 permission/error states are not implemented.
- Phase 4 (profiles, privacy, photos, discovery and likes) is merged to `main` via PR #5. Automated CI passed on the verified implementation head. Live AWS/Cognito, runtime IAM, shared-database V6 preflight, authenticated device/browser flows and Figma visual parity remain unverified; see the Phase 4 section and release-gate document.
- Production legal versions and HTTPS policy URLs are not approved. Production registration must stay disabled until they are supplied (`PRITIMITRA_LEGAL_REQUIRE_APPROVED=true` enforces this).
- SES DNS records are not created. Creating them requires explicit authorization and an existing Route 53 zone ID.
- Cognito `AdminDeleteUser` (called with the `sub` as username) is unverified against a real dev user pool.
- ECS Fargate, ALB, RDS, and Secrets Manager/SSM runtime configuration are not in Terraform yet. These are a follow-up slice.
- Live Cognito sign-up/verification/refresh-rotation tests need a development user pool.
- Cost considerations before provisioning: ECS Fargate, RDS, ALB, NAT gateway, SES, and Cognito pricing by MAU.

### Remaining Phase 3 release gates
Complete the outstanding web authentication/onboarding routes and end-to-end acceptance. Validate Cognito sign-up, verification, recovery, refresh rotation and user deletion against an authorized development user pool. Review AWS costs and obtain explicit authorization before provisioning. Keep Terraform apply, DNS changes and live sending blocked until authorized and configured.

### Proposed DNS records (awaiting authorization; not created)
| Purpose | Type | Name | Value |
|---|---|---|---|
| SES domain verification | TXT | `_amazonses.mail.pritimitra.com` | the `verification_token` output |
| DKIM (3 records) | CNAME | `<token>._domainkey.mail.pritimitra.com` | `<token>.dkim.amazonses.com` |
| Custom MAIL FROM | MX | `bounce.mail.pritimitra.com` | `10 feedback-smtp.ap-south-1.amazonses.com` |
| Custom MAIL FROM SPF | TXT | `bounce.mail.pritimitra.com` | `v=spf1 include:amazonses.com -all` |

Set `manage_dns_records = true` and `route53_zone_id` only after authorization. Run `terraform output ses_dns_records_required` to get the exact token values.

## Phase 4 — Profiles, privacy and discovery (merged to `main` via PR #5)

### Implemented (backend, Checkpoint A)
- Migration `V4__profiles_discovery_likes.sql` (additive): `onboarding_profile.discovery_visible` (default false), `onboarding_profile.public_id` (unique, random UUID), a partial index for discoverable profiles, and the `profile_like` table with a no-self-like check.
- Owner-only profile API at `/api/v1/profile`: `GET`, `PUT` (explicit request allow-list), and `PUT /discoverability`. Opting in requires a completed profile (409 `PROFILE_INCOMPLETE`).
- Discovery API at `/api/v1/discovery`: keyset cursor pagination over `public_id` (opaque base64url cursor), size 1–50, optional goal filter. Viewer must be `ACTIVE` (403 otherwise). Candidates must be `ACTIVE`, completed, discoverable, not self, and not already liked. Age window comes from the viewer's preferences and is computed in SQL, so date of birth is never returned.
- Idempotent directional likes at `/api/v1/likes/{publicId}` (`PUT` like, `DELETE` unlike). Self-like returns 400 `CANNOT_LIKE_SELF`. Private and unknown targets return the same 404 `PROFILE_NOT_FOUND`.
- Discovery cards expose only display name, bio, prompt, identity fields, goals, and interests.

### Implemented (backend, Checkpoint B: photos)
- Migration `V5__profile_photos.sql` (additive): `profile_photo` table with owner FK (cascade delete), server-generated `storage_key` (unique), content-type and size CHECKs, and `review_status` defaulting to `PENDING_REVIEW`.
- Owner-only photo API at `/api/v1/profile/photos`: `GET` list (metadata only), `POST` multipart upload (201), `GET /{id}/content` (bytes with `Cache-Control: private, no-store` and `X-Content-Type-Options: nosniff`), `DELETE /{id}` (204).
- Validation by decoding the image (JPEG and PNG only; see the hardening pass below), not the client Content-Type. Max 5 MB and 6 photos per user. Concurrent uploads are serialised with a transaction advisory lock.
- Storage is a `PhotoStorage` port with an S3 adapter. Keys are `profiles/<userId>/<uuid>` and are never returned. Upload writes the object first and removes it if the insert fails. Delete removes the row, then the object best-effort.
- Errors: `INVALID_PHOTO` (400), `PHOTO_TOO_LARGE` (413), `PHOTO_LIMIT_REACHED` (409), `PHOTO_NOT_FOUND` (404), `PHOTO_STORAGE_UNAVAILABLE` (503, also when no bucket is configured).
- Photos are owner-only in Phase 4. They are not shown in discovery. Moderation belongs to Phase 7.
- `AWS SDK S3` dependency added to the backend. Config: `PRITIMITRA_PHOTO_BUCKET` (blank by default, so storage fails closed) and `AWS_REGION` (default `ap-south-1`).

### Verification (actual runs, Checkpoint B)
- `backend\mvnw.cmd verify` (Docker Desktop running): BUILD SUCCESS, 49 tests, 0 failures. `PhotoApiTests` 9/9 (mocked `PhotoStorage`, no AWS calls). `ProfileDiscoveryApiTests` 13/13. `OnboardingApiTests` 13/13.
- Live S3 behaviour has not been tested. It needs an authorised development bucket.

### Implemented (Flutter, Checkpoint C)
- Routes `/profile` and `/discover` are reachable only for `ACTIVE` accounts. Home screen links to both.
- Profile screen shows the own profile, the discoverability toggle, and `PROFILE_INCOMPLETE` handling. Discovery screen lists cards with like and unlike, and pages with a cursor.
- `ApiUnavailable` carries the backend error `code`, so 409 `PROFILE_INCOMPLETE` reaches the UI.
- Photo management UI: implemented in Checkpoint F (see below).

### Implemented (Web, Checkpoint D)
- BFF allowlist extended exactly: `GET /api/v1/profile`, `PUT /api/v1/profile/discoverability`, `GET /api/v1/discovery` (first page only, no query string), and `PUT`/`DELETE /api/v1/likes/{publicId}`. `DELETE` is added to the proxy handler with the same same-origin and JSON-content-type CSRF gate as PUT and POST.
- Server Component pages `/profile` and `/discover` with client components for the discoverability toggle and the like button. Shared loader `lib/profile/server.ts` maps backend results to signed-out, not-eligible (403 or 409), unavailable and ok states.
- Response parsers `lib/profile/models.ts` reject malformed payloads and drop unknown fields.
- Limitations: discovery has no viewer-liked flag, so like buttons start in the "Like" state. Server pages do not refresh an expired access token (`canRefresh: false`, same as `/account`), so they show the sign-in prompt in that case. The client calls refresh through the proxy.

### Verification (actual runs, Checkpoint D)
- `web`: `npm test` 70/70 passed (9 files, including `proxy-policy.test.ts` with new profile, discovery and like cases, and `profile/models.test.ts`). `npx tsc --noEmit` exit 0. `npm run lint` exit 0. `npm run build` exit 0, with `/discover` and `/profile` built.
- Not run in this checkpoint: browser or screenshot checks of the new web pages. Visual parity with Figma is not verified.

### Hardening pass (PR #5 review fixes)
- Photo validation: JPEG and PNG only. Files are decoded with ImageIO, which rejects truncated or corrupt images. Limits are 5 MB, 4,096 px per side and 12 MP. Accepted images are re-encoded to strip metadata, and the stored content type matches the output. WebP is removed from the accepted set. Applied migration V5 still allowed `image/webp` in its CHECK constraint; additive migration V6 reconciles this (see Checkpoint F).
- Terraform: mocked-provider tests cover the TLS-deny policy and the stale-upload abort lifecycle. The backend runtime IAM role is still not declared. It is a deployment dependency, and nothing was applied.
- Discovery: inclusive age-window boundary tests and a malformed cursor test were added.
- Discovery preferences: gender and interest preferences are NOT enforced. Candidate gender identity is free text, so reliable matching is not possible yet. Only the age window and goal filter are enforced.
- Likes: `GET /api/v1/likes` (paged, privacy-safe) lists the viewer's outgoing likes. Unlike returns 200 `{liked:false}` for both missing and existing likes, so it does not reveal existence. Flutter maps 404 `PROFILE_NOT_FOUND` correctly, and the liked-profiles sheet is server-backed.
- Web BFF: query forwarding for `/api/v1/discovery` only. Allowlist: `cursor`, `size` (1–50) and `goal` (5 enum values). Unknown, repeated or malformed keys return 400 `invalid_request`, and nothing is forwarded. The discovery page loads more with a cursor and has loading, error and retry states.

### Verification (hardening pass, actual runs)
- `backend\mvnw.cmd -B verify`: exit 0, BUILD SUCCESS, 63 tests, 0 failures, 0 errors, 0 skipped. This includes `PhotoApiTests` 14/14, `PhotoImageProcessorTests` 4/4, `ProfileDiscoveryApiTests` 17/17, `Phase4UpgradeMigrationTests` 1/1 and `OnboardingApiTests` 13/13. Docker was running (engine 29.9.2).
- Flutter: `dart format --set-exit-if-changed` on the discovery and API files exit 0 (0 changed). `flutter analyze` on the discovery and network paths exit 0 with no issues. `flutter test test/features/profile_discovery_api_test.dart` exit 0, 14/14.
- Terraform (from `infra/terraform`): `terraform fmt -check -recursive` exit 0. `terraform init -backend=false` exit 0. `terraform validate` exit 0. `terraform test` exit 0, 6 passed, 0 failed.
- Web (at commit `3c6b82b`): `npm test` 81/81, `tsc --noEmit` exit 0, `npm run lint` exit 0, `npm run build` exit 0.
- Not run: browser or screenshot checks, and Figma comparison. Visual validation is not claimed.

### Outstanding and deferred (Phase 4)
- Flutter and web photo management: implemented and unit-tested (Checkpoint F). Not exercised on a device or in a browser, and no Flutter app was connected for hot reload.
- Android photo-picker configuration is not added. iOS has `NSPhotoLibraryUsageDescription`. Whether `image_picker` returns HEIC on iOS is unverified. The server accepts only JPEG and PNG, so a HEIC pick is rejected by the server.
- Discovery preferences (gender identity and interests): NOT enforced, as described above.
- Viewer-liked state on discovery cards: not provided. Liked profiles are reached through the outgoing-likes list.
- Live AWS S3 and Cognito behaviour is not tested. Authorisation has not been given for a development bucket or `terraform apply`. The backend runtime IAM role is blocked on that infrastructure decision.
- Phase 4 implementation is merged to `main` via PR #5, but it is not production-ready until the runtime IAM/compute design, target-database V6 preflight, live integrations and authenticated browser/device checks are complete.

### Terraform and upgrade checks (Checkpoint E)
- `infra/terraform/photos.tf` declares a private profile photo bucket: public access fully blocked, SSE-S3 (AES256), bucket-owner-enforced ownership, versioning disabled so deleted photos are not retained, TLS-only bucket policy, and abort of incomplete multipart uploads. Name is `pritimitra-profile-photos-<env>-<account_id>`. Output `profile_photo_bucket_name`.
- The API still needs an IAM role with `s3:PutObject`, `GetObject` and `DeleteObject` on this bucket. That role is not declared yet, and nothing has been applied.
- `infra/terraform/tests/profile_photos.tftest.hcl` (plan-only, mocked provider) checks public access block, encryption and ownership.
- Verified: `terraform fmt -check -recursive` exit 0, `terraform init -backend=false` exit 0, `terraform validate` exit 0, `terraform test` 4 passed (3 existing Cognito cases plus 1 new).
- `backend/src/test/.../Phase4UpgradeMigrationTests.java` migrates an isolated schema to V3, inserts pre-Phase-4 users and profiles, migrates to latest, and checks that profiles default to `discovery_visible = false`, that `public_id` is set and distinct per row, and that `profile_photo` exists. The schema is dropped afterwards.
- Verified: `backend\mvnw.cmd -B verify` BUILD SUCCESS, 50 tests, 0 failures.
- Not re-run in this checkpoint: Flutter and web, since neither changed after Checkpoint D.
- Distance and interest matching are not enforced in discovery. Candidate gender identity is free text, and there is no location data.
- Live Cognito and S3 behaviour is untested in this environment. Hot reload of Flutter was not run because no app is connected.

### Checkpoint F: photo management, V6, and final verification
Commits on `feature/phase-4-profiles-discovery`: `394b889` (web photos), `2f3b91b` (V6), `5bb9cbb` (Flutter photos).

- **Flutter photo management** (`5bb9cbb`): own photo list, picker (`image_picker`), upload with transport-level progress, server-confirmed results only, retry for recoverable failures (network, 5xx), delete with confirmation, 401 refresh-and-retry, empty and error states, and a privacy note (not identity-verified, owner-only). The picker accepts only JPEG and PNG by magic bytes. The client check is UX only; the server is authoritative. Upload progress measures bytes handed to the transport, not server acceptance, and the UI shows "Checking your photo…" at 100%.
- **Web photo management** (`394b889`): profile photo UI in `web/app/profile`. Multipart upload goes through the existing BFF `/api/backend/[...path]` route with an explicit allow-list, the same-origin Origin check, and a multipart content-type check. The request body is capped at 5 MiB plus 128 KiB of multipart overhead, checked against the declared length and then read with a bounded reader. Binary media responses are capped at 8 MiB and sent with `Cache-Control: no-store`. Access tokens stay server-side. The BFF is not a general-purpose proxy, and the CSRF gate was not weakened.
- **V6 migration** (`2f3b91b`): `V6__reconcile_profile_photo_content_types.sql` is additive. It fails closed if any row has a non-JPEG/PNG content type. Otherwise it replaces the CHECK constraint with JPEG/PNG only. V1–V5 are unchanged. `ProfilePhotoMigrationTests` covers a clean schema, an upgrade from V5 that keeps rows and rejects WebP, and a failed upgrade when WebP rows exist. Open question: whether any shared environment contains WebP rows.
- **Discovery preferences**: gender identity and interest matching are NOT enforced. Gender identity is free text, so reliable matching is not possible. Eligibility (ACTIVE, onboarded, discoverable, not self), the age window, and the goal filter are enforced server-side.
- **Deployment dependency**: the backend runtime IAM role (`s3:PutObject`, `GetObject`, `DeleteObject` on the profile photo bucket only) is not defined in Terraform. It is not invented here. It must be declared before the live API can store photos. Nothing was applied.

**V6 pre-deployment check (read-only).** Run against each shared database before applying V6. Any row returned blocks the migration, which fails closed by design. Do not convert or delete rows silently:

```sql
SELECT content_type, count(*) AS rows_blocking_v6
FROM profile_photo
WHERE content_type NOT IN ('image/jpeg', 'image/png')
GROUP BY content_type;
```

Compatibility with real environments is unverified. No shared database was queried.

### Closure verification (current tree, `3ef7023` plus docs)
- Backend `mvnw.cmd -B verify`: exit 0, 66 tests, 0 failures, BUILD SUCCESS. Log was written to a temp file, not committed.
- Web: `npm test` 99/99 passed, `tsc --noEmit` exit 0, `npm run lint` exit 0, `npm run build` exit 0.
- Terraform: `fmt -check -recursive` exit 0, `init -backend=false` exit 0, `validate` exit 0, `test` 6 passed.
- Flutter: `flutter analyze` and `flutter test` (91 passed) were run on this same Flutter code (no Flutter code changed since `5bb9cbb`).
- GitHub Actions on the head: Backend CI, Web frontend CI and Flutter mobile CI all succeeded. There is no Terraform CI workflow.

### Verification (Checkpoint F, actual runs)
- Backend `backend\mvnw.cmd -B verify` (Docker 29.9.2 running, `JAVA_HOME` set to JDK 21): exit 0, BUILD SUCCESS, 66 tests, 0 failures, 0 errors. Includes `ProfilePhotoMigrationTests` 3/3, `PhotoApiTests` 14/14, `PhotoImageProcessorTests` 4/4, `ProfileDiscoveryApiTests` 17/17, `Phase4UpgradeMigrationTests` 1/1.
- Flutter: `flutter analyze` on the touched paths exit 0, no issues. Full `flutter test` exit 0, 91 passed. Targeted: `pritimitra_api_client_test` 6/6, `profile_photo_api_test` and `profile_photo_controller_test` pass, `profile_discovery_api_test` 14/14.
- Web (at `394b889`; no web changes since): `npm test` 99/99, `tsc --noEmit` exit 0, lint 0 warnings, `npm run build` exit 0.
- Terraform (from `infra/terraform`): `fmt -check -recursive` exit 0, `init -backend=false` exit 0, `validate` exit 0, `test` 6 passed, 0 failed.
- Not run: browser or device exercise of the photo flows, Figma screenshot comparison, live S3, Cognito, and `terraform apply`. These need explicit authorization or an available device.


## Phase 5 — Matching, messaging, notifications and safety (merged via PR #6)

Branch: `feature/phase-5-matching-messaging`; PR #6 has been retargeted to `main`. The implementation covers transactional mutual-like matching, unmatch, participant-only conversations, idempotent text messages (2,000-character limit), cursor-based catch-up with bounded polling, block/unblock, private reports, and in-app notifications. Block/send serialization locks the conversation row; the PostgreSQL-backed regression test verifies a block waits and sends are rejected after the block succeeds.

### Verification (previous branch head `5537a7570bbbfb6b02173f2aa090b031e5249060`)
- Backend CI passed: 102 tests, 0 failures.
- Web frontend CI passed.
- Flutter CI passed: analyze, tests and Flutter web release build.
- CI must be checked again on the branch after its base-sync merge commit.

### Open risks and next tasks
- Polling intervals (5 seconds, with 60-second backoff) need product confirmation.
- SSE, push and email delivery, browser/device verification and live AWS/Cognito testing are not done.
- Reports remain private pending items; moderation review tooling is Phase 7 scope.
- No infrastructure changed and no deployment occurred.

- Block/send serialization: the block path locks the user pair, then the conversation row, before inserting the block and ending the match. Sends use the conversation row and then share-lock the match; they do not acquire user locks. A send already holding the row completes before the block can proceed; once the block response succeeds, subsequent sends reject. `SafetyApiTests.blockWaitsForConversationLockAndRejectsMessagesAfterCompletion` verifies the lock wait and post-block rejection. See [Phase 5 design](./phase-5-matching-messaging.md#data-model-and-integrity).
- Polling intervals (5 s, 60 s backoff) need product confirmation.
- SSE, push and email delivery, and browser or device verification are not done.
- No live AWS, Cognito, device, browser, or Figma verification was run. No infrastructure changed.


## Current implementation status (updated 2026-10-27)

### Phase 4 — Profiles, privacy, photos and discovery
- **Merged via PR #5:** profile management, discoverability, cursor-paginated discovery, directional likes, private photo APIs, Flutter and web flows, and private S3 bucket Terraform definition.
- Backend, Web and Flutter CI passed on the original application-code head. On the subsequent documentation-sync head, Backend/Web CI and Flutter analyze/tests passed; the Flutter release build was still running when checked. No application source code changed in that docs-only sync.
- Remaining release gates: runtime compute/IAM, authorized live Cognito/S3 testing, read-only V6 preflight against target databases, authenticated browser/device testing, Figma visual comparison and any future gender/interest compatibility decision. Photos remain owner-only and pending review until moderation is implemented.
- No AWS resources have been provisioned; no Terraform apply, DNS changes or deployment have occurred.

### Phase 5 — Matching, messaging, notifications and safety
- **Merged PR #6:** [Phase 5 implementation](https://github.com/operationReact/PritiMitra/pull/6).
- Backend CI passed with 102 tests, Web CI passed, and Flutter mobile CI passed on the original head `5537a7570bbbfb6b02173f2aa090b031e5249060`. The branch-base synchronization commit is documentation/architecture-only; check the latest workflows before merging.
- Browser/device and live AWS/Cognito tests remain open. Moderation review tooling is a later Phase 7 item.

### Repository-wide release boundary
Green unit tests and CI do not prove deployed AWS behavior, approved production legal configuration, or authenticated end-to-end product readiness. No Terraform apply, DNS mutation, live AWS provisioning or production deployment has been performed.


## Phase 6 — Blind Date and community (complete; merged via PR #8)

- Merged PR: https://github.com/operationReact/PritiMitra/pull/8
- Branch: `feature/phase-6-blind-date-community`
- Backend: additive V10 migration; opt-in Blind Date slot validation, same-slot pairing and future-session cancellation; four seeded communities; membership, member-only paginated feed, reactions, own-post removal and private post reports.
- Web: `/community` and `/blind-date`; BFF allow-list only permits the exact Phase 6 endpoint shapes and bounded community-feed query keys.
- Flutter: Community and Blind Date screens linked from the active-account home; explicit membership controls, post/reaction/report actions, slot opt-in and cancellation.
- The final pre-merge Backend, Web and Flutter workflows passed. Authenticated browser/device acceptance and user-facing polish remain release gates.
- Community reports are private and accessible through the moderator-only Phase 7 review queue.
- No AWS resources, Terraform apply, DNS changes or deployment have occurred.


## Phase 7 — Trust and safety (in progress; PR #10)

- Added additive Flyway V11 changes for report review state, reviewer metadata and an append-only `moderation_audit_event` table protected against UPDATE and DELETE.
- Added moderator-only report listing and review APIs. Requests require the Cognito `cognito:groups` claim to contain `PRITIMITRA_MODERATORS` or `PRITIMITRA_ADMINS`; moderator identity is checked against the application account table on every request.
- Added bounded keyset pagination and target context without reporter identity. Review decisions: dismiss, hide a reported community post, or suspend the reported account.
- Account suspension disables discovery, closes active matches, hides the account's published community posts, and cancels scheduled Blind Date sessions and opt-ins.
- Report submission is limited to 10 persisted reports per account per rolling hour; a per-reporter row lock serializes the rate-count-and-submit transaction. Community feeds/interactions omit content from suspended accounts.
- Added the web operations queue at `/moderation` with pending/actioned/dismissed filters, internal review notes and confirm prompts for destructive actions. The BFF allows only the exact listing/review paths and validates query parameters.
- Remaining: account-deletion and consent-revocation workflows, additional anti-abuse controls, reviewer-group provisioning and runbooks. AWS resources, Cognito groups and DNS have not been provisioned or changed. CI results must be checked on the current PR head before calling this slice verified.
