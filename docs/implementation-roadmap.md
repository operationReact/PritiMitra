# PritiMitra Implementation Roadmap

## Phase 1 — Engineering foundation

Status: complete and merged to `main`.

Flutter, Next.js, Spring Boot, PostgreSQL/Flyway, Docker Compose, and CI foundations are in place.

## Phase 2 — Design system and rich UI

Status: in progress; visual parity has not been confirmed.

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

Status: **in progress on `feature/phase-4-profiles-discovery` (draft PR #5). Not complete.** Implemented and unit-tested: backend profile, privacy, discovery, likes, photo APIs and the V6 photo-type migration; Flutter profile, discovery and photo management (picker, upload, list, delete); web profile, discovery (with load-more), likes and photo management through the BFF; Terraform photo bucket. Deferred or blocked: gender and interest preference matching (not enforced, documented), the backend runtime IAM role (deployment dependency, not declared), live S3 and Cognito tests (need authorization), and device or browser validation of the photo flows. See `docs/project-status.md` for the verified evidence.

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

Transactional match creation, conversation APIs, reliable message persistence, real-time delivery, notification jobs, and block/report integration.

Status: in progress on `feature/phase-5-matching-messaging` (stacked on Phase 4; draft PR #6, not merged). Backend, Flutter and web messaging are implemented and tested locally. Bounded polling is the delivery method. Blocking from conversation screens is implemented; SSE, push/email delivery, and browser/device verification remain open. The block/send locking fix is covered by a PostgreSQL-backed regression test. Details and verification are in [phase-5-matching-messaging.md](./phase-5-matching-messaging.md).

## Phase 6 — Blind Date and community

Opt-in Blind Date flows, scheduling, community feed, groups, reactions, and moderation.

## Phase 7 — Trust and safety

Reporting queues, abuse prevention, consent controls, account deletion, audit events, and operational review tooling.

## Phase 8 — Responsive web product experience

Authenticated product flows on desktop/tablet/mobile web, using the same backend APIs and conceptual design tokens.

## Phase 9 — Release readiness

Performance/load tests, security review, backups/recovery, observability, deployment automation, store readiness, and cross-platform regression testing.
