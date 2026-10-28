# PritiMitra Implementation Roadmap

## Phase 1 — Engineering foundation

Status: complete and merged to `main`.

Flutter, Next.js, Spring Boot, PostgreSQL/Flyway, Docker Compose, and CI foundations are in place.

## Phase 2 — Design system and rich UI

Status: foundation merged to `main` via PR #2; visual parity against approved Figma frames remains unconfirmed.

- Establish semantic color, spacing, radius, typography, motion, and accessibility tokens.
- Implement the responsive marketing landing page against the approved Figma desktop and mobile landing frames.
- Upgrade the Flutter welcome screen with the same brand language and reduced-motion-aware entrance transitions.
- Add meaningful interaction feedback and responsive navigation.
- Run Flutter format/analyze/tests/web build, Next.js lint/build, and compare actual rendered screenshots against Figma. Do not claim visual parity until this comparison is complete.

Acceptance criteria: CI green; welcome UI responsive and accessible; primary controls have clear behavior; motion respects reduced-motion preferences; authentication and app-store links are not described as implemented while they remain unwired.

## Phase 3 — Authentication and onboarding

Status: **authentication foundation merged to `main` via PR #3, but Phase 3 remains in progress and is not production-ready.** Merging the foundational implementation does not mean the full onboarding experience or AWS integration is complete.

### Implemented in the repository

- Spring Security resource-server validation for Cognito access tokens and explicit account lifecycle states: `PENDING_EMAIL_VERIFICATION`, `ONBOARDING_INCOMPLETE`, `ACTIVE`, `INELIGIBLE`, and `SUSPENDED`.
- Versioned, append-only legal consent records and a fail-closed production legal-configuration guard.
- Next.js server-side authentication/session boundary using `HttpOnly` cookies and an allow-listed backend proxy; tokens are not stored in browser `localStorage` or `sessionStorage`.
- Flutter Amplify Cognito integration, authentication routing, and API-client foundations.
- Terraform definitions for Cognito app clients and refresh-token rotation, SES identity/DKIM/custom MAIL FROM, ECR repositories, CloudWatch log groups, and GitHub OIDC.

### Remaining before Phase 3 can be considered complete

- Finish the web sign-in, sign-up, verification, recovery, onboarding, and relevant permission/error-state screens; connect them to the existing server-side auth boundary.
- Complete end-to-end onboarding acceptance checks across web, Android, and iOS. Rendered screenshots still need to be compared against the approved Figma frames; do not claim visual parity before that comparison.
- Run authorized development-user-pool tests for sign-up, email verification, password recovery, refresh-token rotation, and Cognito user deletion. The `AdminDeleteUser` behavior using the Cognito `sub` has not been verified against a live pool.
- Approve production Terms and Privacy versions and HTTPS policy URLs. Draft policy versions must not be accepted as production-approved versions.
- Obtain explicit authorization before creating SES DNS records or provisioning AWS resources. SES DNS verification and live sending have not been confirmed.
- Add the remaining deployment/runtime infrastructure in a later, separately reviewed slice: ECS Fargate, ALB, RDS PostgreSQL, and runtime secrets/configuration.
- Run an iOS build and record the result.

### Infrastructure and cost boundary

Terraform has been formatted, validated, and tested with a mocked provider/plan-only checks as reported in the project status. Those checks do **not** provision AWS resources or prove live Cognito/SES behavior. No Terraform apply, production DNS change, or live AWS integration test should be implied by the repository configuration.

Review likely costs before authorizing provisioning, including Cognito usage, SES, ECS Fargate, ALB, RDS PostgreSQL, NAT gateway, CloudWatch, and any Route 53 charges that apply to the existing DNS setup.

## Phase 4 — Profiles and discovery

Status: **merged to `main` via PR #5; implementation complete for this phase's committed scope, but not production-ready.** Implemented and unit-tested: backend profile, privacy, discovery, likes, photo APIs and the V6 photo-type migration; Flutter profile, discovery and photo management (picker, upload, list, delete); web profile, discovery (with load-more), likes and photo management through the BFF; Terraform photo bucket. Deferred or blocked: gender and interest preference matching (not enforced), backend runtime IAM, live S3/Cognito tests, shared-database V6 preflight, authenticated browser/device checks, and Figma visual comparison. See `docs/project-status.md` and `docs/phase-4-release-gate.md`.

### Scope

- Profile read/update APIs and mobile UI, with server-side validation and explicit field allow-lists.
- Profile photo upload and lifecycle using private object storage, short-lived presigned upload authorization, and server-side validation.
- User-controlled profile privacy and discovery visibility.
- Cursor-based discovery pagination and validated filters.
- Idempotent like/unlike actions. A like must not create a match automatically; matching belongs to Phase 5.

### Security and privacy requirements

- Only eligible, fully onboarded `ACTIVE` accounts can appear in discovery. Pending, incomplete, ineligible, and suspended accounts must never be discoverable.
- Return explicit public-profile DTOs; never expose email, Cognito identifiers, date of birth, consent records, internal moderation fields, or other private account data through discovery responses.
- Enforce authorization and privacy rules on the server for every profile, photo, and like operation; do not rely on client-side hiding.
- Keep uploaded media private. Require TLS, encryption at rest, S3 Block Public Access, no public object ACLs, and least-privilege upload/read permissions. Validate file signatures, allowed image types, size and dimensions before publication. Do not introduce a public CDN path unless its access model has been designed and reviewed.
- Use stable keyset/cursor pagination with deterministic ordering and bounded page sizes; validate filter values and avoid unbounded result queries.
- Enforce a unique directional like pair in the database and make repeated like/unlike requests safe and predictable.

### Acceptance criteria

- Backend tests cover profile field validation, ownership/IDOR boundaries, inactive-account exclusion, privacy DTOs, photo authorization and validation, pagination boundaries, filter validation, and duplicate like/unlike requests.
- Mobile tests cover loading, empty, error, retry, privacy controls, and photo upload states.
- Web behavior is either implemented or explicitly marked as not yet available; do not imply that responsive web product flows are complete.
- No AWS resources are provisioned and no DNS is changed as part of implementation unless separately authorized.

## Phase 5 — Matching and messaging

Status: **merged to main via PR #6.** Scope includes transactional reciprocal-like matching, unmatch, participant-only conversations, idempotent text messaging, cursor-based catch-up with bounded polling, block/unblock, private reports and in-app notifications. Browser/device acceptance and live AWS/Cognito validation remain outstanding.

## Phase 6 — Blind Date and community

Status: merged to main via PR #8.

Implemented:
- Additive Flyway V10 schema for Blind Date opt-ins/pairings, curated groups, posts, reactions and private community-post reporting.
- Backend opt-in scheduling and cancellation APIs. Pairing occurs only when two eligible, unblocked users independently choose the same aligned slot.
- Backend community groups, explicit memberships, member-only paginated plain-text feeds, post removal and LIKE/HEART/SUPPORT reactions.
- Web pages at /community and /blind-date, and Flutter Community and Blind Date screens.
- Backend, Web and Flutter CI passed on the Phase 6 head. Live cloud/device validation remains outstanding.

## Phase 7 — Trust and safety

Status: **moderation, account lifecycle controls, and community/messaging rate limits merged to `main` via PRs #10, #16, and #17; Phase 7 remains in progress.** See docs/phase-7-trust-safety.md, docs/phase-7-account-lifecycle-controls.md, docs/phase-7-community-messaging-rate-limits.md, and docs/phase-7-moderation-audit-history.md.

Implemented on `main`: moderator-only report review, content/profile hiding, account suspension with a required reason, append-only safety audit events, report/post/message per-account rate limits with concurrency controls, suspension effects that close active matches and Blind Date pairings and hide community posts, retryable self-service account deletion, private photo cleanup, a no-reprovision tombstone, append-only privacy-safe lifecycle audit, optional community-nickname consent history, and responsive web account controls.

Current Phase 7 work adds moderator-only audit-history review and a bounded CSV export with privacy-safe actor references, event/date filters, cursor pagination, and spreadsheet formula-injection protection. Follow-up still required: live Cognito/S3 staging verification, incident/escalation procedures, and authorized browser/mobile checks. No AWS resources or DNS records are created by this work.

## Phase 8 — Responsive web product experience

Status: **Phase 8A merged via PR #12; Phase 8B web authentication/onboarding is in PR #13.**

This first slice adds a shared responsive authenticated workspace shell, route-aware navigation, a data-backed Home dashboard, and richer discovery cards. The public landing page stays outside the workspace. Desktop receives a persistent sidebar; tablet collapses to an icon rail; mobile receives a compact bottom navigation that respects safe-area insets. The dashboard uses the existing authenticated profile, conversation, community, and Blind Date APIs; unavailable data is shown as unavailable rather than fabricated. Discovery cards use abstract gradients and initials because the discovery API does not return profile photos. Motion honors reduced-motion preferences.

Phase 8B adds public sign-in/sign-up, email confirmation/resend, password recovery/reset, account-state routing and a guided age/consent/profile onboarding flow. Production onboarding stays blocked until non-draft approved policy versions and HTTPS Terms/Privacy URLs are configured. Remaining Phase 8 scope: mobile/tablet/desktop browser acceptance, conversation/community layout refinement, approved-Figma comparison and validation of the current PR #13 head. No AWS provisioning or DNS changes are part of Phase 8.

## Phase 9 — Release readiness

Status: **in progress.** Phase 7 account lifecycle, moderation audit history, and community/messaging rate limits are merged. Phase 8 desktop/mobile browser smoke tests are merged. Phase 9 has merged Android APK/App Bundle and iOS no-signing build gates. The next slice adds CodeQL analysis for Java and TypeScript/JavaScript plus a manually triggered, production-blocked k6 smoke test with automated target-guard tests; see `docs/phase-9-release-readiness.md` and `docs/phase-9-security-performance-gates.md`.

Remaining before production: complete browser/device and accessibility acceptance; compare rendered screens against approved Figma; validate authorized development Cognito and private S3 flows; approve production legal policy versions/HTTPS URLs; verify runtime IAM and deletion-worker telemetry; then perform security/cost review, load/restore testing, and deployment rehearsal. Do not apply Terraform, change DNS, add signing secrets, or deploy without the relevant explicit authorization.

Release-readiness work must distinguish CI compilation from signed store artifacts and live cloud behavior. CI green alone does not make the app production-ready.
