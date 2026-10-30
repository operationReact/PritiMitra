# PritiMitra Production Implementation Plan v2

Status: proposed production implementation plan based on the current `main` baseline (`aa5783ae3ea6c02ef8708288778f1eab033ec5b5`) and the approved dual-design direction tracked in PR #24 / issue #25.

This document supersedes the old phase sequence as the implementation coordinator for work **after the current main baseline**. It does not rewrite completed features. It converts the existing modular-monolith codebase into a production-ready, dual-design, cross-platform product through small, reviewable pull requests.

## 1. Product objective

PritiMitra is an adult LGBTQ+ dating, friendship and community product with:
- secure onboarding and explicit account state;
- profile/discovery, likes, matching and conversations;
- Blind Date and community features;
- privacy, consent, reporting, moderation, account deletion and abuse controls;
- responsive web plus Flutter Android/iOS;
- two mandatory product experiences: **Spark** and **Essence**.

The production goal is not merely to make screens compile. The finished product must be visually premium, safe, observable, testable, resilient, accessible and operationally releasable.

## 2. Architecture that remains authoritative

Do not introduce a rewrite unless a measured limitation justifies it.

| Layer | Production direction |
|---|---|
| Android / iOS | Flutter 3.47.x, Riverpod, GoRouter, platform secure storage, one shared business-logic layer |
| Responsive web | Next.js App Router, React, strict TypeScript, server-side authentication boundary |
| Backend | Java 21, Spring Boot 4.0.x modular monolith |
| Database | PostgreSQL 17 with append-only Flyway migrations |
| Identity | Amazon Cognito User Pools; immutable Cognito `sub` is the external identity key |
| Media | Private object storage; authorization remains server-controlled |
| Infrastructure | Terraform; AWS region configurable, current intended default `ap-south-1` |
| CI/CD | GitHub Actions; required checks must execute and pass before merge |

### Non-negotiable architecture boundaries

1. Business rules and authorization stay server-side.
2. Web tokens remain behind the Next.js server-side boundary; no browser `localStorage`/`sessionStorage` token persistence.
3. Flutter credentials/tokens use native secure storage through the selected Cognito/Amplify path.
4. Spark and Essence must not fork networking, domain logic, authentication or backend services.
5. Flyway migrations already merged to shared branches are immutable.
6. Production infrastructure, DNS, signing, paid resources and deployment remain separate explicit gates.
7. No feature is considered done only because documentation, mocks or Terraform definitions exist.

## 3. Current `main` baseline — do not reimplement

The following capabilities already exist on `main` and should be evolved rather than rewritten:

### Foundation and clients
- Flutter Android/iOS application structure, Riverpod/GoRouter foundations and shared theme/motion primitives.
- Next.js responsive browser application with an authenticated product shell.
- Spring Boot/PostgreSQL/Flyway backend and local Docker Compose.
- Backend, web and Flutter CI.
- Android release APK/App Bundle compilation and iOS release compilation without signing.

### Identity and onboarding
- Cognito-oriented backend JWT validation and explicit account states.
- Mobile Cognito/Amplify authentication foundation.
- Server-managed web authentication/session cookies and allow-listed backend proxy.
- Sign-in, sign-up, verification/resend, password recovery/reset and guided web onboarding.
- Adult age enforcement and append-only required legal-consent records.
- Ineligible-account retention/purge flow.

### Core product
- Profile read/update and discoverability controls.
- Private profile-photo upload/list/delete lifecycle and server-side image validation.
- Discovery, bounded cursor pagination and likes.
- Transactional mutual-like matching.
- Unmatch, conversations, durable text messaging and bounded polling.
- Blocks, private reports and in-app notifications.
- Blind Date opt-in/pairing/cancellation.
- Community groups, membership, feeds, posts, reactions and reporting.

### Trust, privacy and operations
- Moderator report queue and audited moderation decisions.
- Account suspension side effects across discovery/matches/community/Blind Date.
- Per-account report/community/message abuse limits with concurrency controls.
- Self-service account deletion with retryable external cleanup.
- Anti-reprovision tombstone and privacy-safe lifecycle audit.
- Optional community-nickname consent history.
- Responsive account/privacy controls.
- Browser acceptance tests for key auth/onboarding flows.

## 4. Existing open work to reconcile before starting new product branches

These are **not part of `main` yet** and must be handled explicitly:

- **PR #24** — approved Spark/Essence design-system specification. Merge only after final review; documentation is not implementation.
- **PR #26** — Phase 0 dual-design architecture/screen inventory. Rebase after the specification if needed and merge as the audited implementation baseline.
- **PR #27** — typed Spark/Essence foundation. Keep unmerged until the brand-token invariance issue is resolved and exact-head CI passes.
- **PR #23** — CodeQL + guarded non-production performance smoke work. Resolve capability/settings blockers and validate before merging.
- **PR #9** — Terraform state bootstrap. Keep separate until infrastructure work is explicitly authorized and Terraform checks are complete.

The implementation plan below assumes these branches are reconciled in dependency order, not blindly merged.

---

# 5. Production implementation phases

## Phase 0 — Re-baseline and plan governance

**Goal:** establish one trustworthy source of truth for what is on `main`, what is in-flight and what remains.

### Planned PR P0-01 — Production plan and status reconciliation
- Add this plan.
- Update `docs/project-status.md` after every merged phase.
- Add a requirement-to-PR traceability table.
- Remove stale statements that contradict current `main`.
- Record exact external release gates separately from code-complete gates.

**Acceptance:** documentation matches current code/merged PRs; no feature is marked complete from an unmerged branch.

### Planned PR P0-02 — Repository quality baseline
- Add/normalize root developer commands for backend/web/mobile checks.
- Make required CI paths explicit.
- Add dependency-update policy and security ownership notes.
- Add a release checklist template and PR checklist template.

**Acceptance:** a new engineer can run all non-cloud checks without guessing.

---

## Phase 1 — Dual-design contract and foundation

**Goal:** establish Spark and Essence as first-class product experiences without changing business logic.

### Existing PR #24 — specification
- Spark and Essence definitions.
- Immutable PritiMitra brand identity.
- Neumorphism explicitly excluded.
- Feature/platform coverage matrix.

### Existing PR #26 — implementation inventory
- Route/screen/platform inventory.
- Existing theme foundation audit.
- Shared acceptance contract.

### Existing PR #27 — typed experience foundation
Required before merge:
- remove arbitrary Essence rebranding colors;
- source immutable brand identity from shared approved brand tokens;
- make differentiation semantic/compositional rather than changing the brand;
- add regression tests proving brand-token invariance;
- preserve safe Spark fallback;
- exact-head web/mobile CI green.

### Planned PR P1-04 — semantic token systems
- Define semantic tokens for Spark and Essence on web and Flutter: surfaces, text hierarchy, spacing, radius, elevation, typography scale, density and motion.
- Keep brand logo/wordmark and core brand colors invariant.
- Map existing components to semantic roles rather than raw colors.
- Add reduced-motion behavior and contrast tests.

**Acceptance:** both design systems are structurally distinct without duplicating domain logic.

---

## Phase 2 — Experience preference, selector and cross-device sync

**Goal:** users can intentionally choose Spark or Essence and keep the choice across sessions/devices.

### Planned PR P2-01 — local Experience selector
- Add **Appearance → Experience** on web and Flutter.
- Preview cards for Spark and Essence.
- Instant switch without route/auth/form resets.
- Persist anonymous/offline preference locally.
- Invalid stored values fall back safely.
- Accessibility labels, keyboard/touch handling and reduced motion.

### Planned PR P2-02 — backend preference contract
- Add a small user-preference table/API using a new additive migration.
- Preference values limited to `SPARK` / `ESSENCE`.
- Owner-only read/update.
- Design preference must not participate in discovery, matching, moderation or authorization.
- Idempotent update and optimistic-safe semantics.

### Planned PR P2-03 — authenticated reconciliation
- Sync local and server preference deterministically after sign-in.
- Define first-login precedence and offline behavior.
- No startup flicker where practical.
- Cross-device tests.

**Acceptance:** design switching never drops authentication, navigation, conversation state or in-progress form state.

---

## Phase 3 — Dual-design vertical slice

**Goal:** prove the architecture end-to-end before converting every screen.

### Planned PR P3-01 — authentication and onboarding in both experiences
- Sign in/up, confirm, recovery and onboarding.
- Spark: expressive, gradient-led, energetic composition.
- Essence: editorial, calm, privacy-forward composition.
- Same forms, validation and backend contracts.
- Responsive web + Android + iOS.

### Planned PR P3-02 — Home + discovery in both experiences
- Home/dashboard and discovery card composition.
- Loading/skeleton/empty/error/retry states.
- Preserve filters, pagination and like state while switching.

### Planned PR P3-03 — profile + match/chat entry vertical slice
- Public-safe profile detail.
- Likes/match affordances.
- Conversation entry and chat shell.
- Mid-flow experience switch regression tests.

**Acceptance:** one complete sign-in → onboarding → discovery → profile → like/match → chat journey works in both Spark and Essence on all supported clients.

---

## Phase 4 — Production photo-first media delivery

**Why this is separate:** the product direction is photo-first, but current discovery intentionally avoids inventing profile photos and the existing media model is primarily owner-controlled/private.

### Planned PR P4-01 — media visibility model
- Define photo publication states and ordering.
- Separate owner-private, approved/public-to-authorized-users and removed states.
- Explicit server-side authorization for viewing another user's profile media.
- Block/suspension/deletion behavior.
- Migration and IDOR/privacy tests.

### Planned PR P4-02 — authorized media delivery
- Short-lived signed read URLs or equivalent protected delivery contract.
- Never expose a permanently public bucket/object path.
- Cache policy, content type and download headers.
- Rate/size bounds and abuse-safe error handling.

### Planned PR P4-03 — photo-first discovery/profile clients
- Web and Flutter use real authorized photos where available.
- Graceful placeholder for profiles without approved media.
- Image loading/error/retry, accessibility text and performance optimization.
- Spark/Essence image composition differs while data contract stays shared.

### Planned PR P4-04 — media moderation pipeline baseline
- Explicit pending/approved/rejected lifecycle.
- Moderator workflow for flagged media.
- No claim of automated AI moderation unless a reviewed provider/contract is actually implemented.

**Acceptance:** photo-first discovery is real, privacy-preserving and block/suspension aware.

---

## Phase 5 — Adult/profile verification and trust signals

**Critical gap:** current architecture explicitly does not implement selfie or identity verification, so no profile should currently be advertised as verified.

### Planned PR P5-01 — verification domain model
- Verification request/status model.
- States: not-started, pending, verified, rejected/expired as appropriate.
- Privacy-minimized storage; do not store unnecessary identity documents.
- Server-controlled trust badge rules.

### Planned PR P5-02 — provider abstraction / manual-safe baseline
- Create a provider-neutral verification service contract.
- Start with a controlled/manual review path if no external provider is approved.
- Document retention/deletion requirements for verification artifacts.

### Planned PR P5-03 — verification UI
- Web + Flutter verification entry, progress, failure/retry and privacy explanation.
- Spark/Essence presentation variants.
- Never imply verification before server-confirmed status.

### Planned PR P5-04 — discovery trust integration
- Show verified indicator only from server DTO.
- Optional product policy for verified-only discovery can be added only after enough verified inventory exists and product rules are approved.

**Acceptance:** trust indicators are cryptographically/auth-server-backed, not client-declared.

---

## Phase 6 — Discovery quality and user preference completeness

### Planned PR P6-01 — discovery preference contract
- Review intended matching rules for dating/friendship intent, interested-in values and gender identity.
- Do not infer sensitive identity fields.
- Add only explicitly user-controlled preference filters.
- Preserve inclusive behavior for free-text identity fields.

### Planned PR P6-02 — location/privacy model
- Decide whether distance-based discovery is required.
- If enabled, store only the minimum precision needed and never expose exact coordinates.
- Permission-denied/manual-location behavior on Flutter and web.
- Safety tests for location leakage.

### Planned PR P6-03 — ranking and pagination hardening
- Deterministic ranking contract that can evolve without breaking keyset pagination.
- Exclude blocked/suspended/hidden/already-exhausted profiles correctly.
- Explainable user-facing filters; no opaque sensitive-trait inference.

### Planned PR P6-04 — discovery QA matrix
- Boundary/race tests, large dataset test fixture and performance baseline.
- Spark/Essence screenshots at target breakpoints.

---

## Phase 7 — Matching and relationship lifecycle hardening

### Planned PR P7-01 — likes/matches UX completeness
- Incoming/outgoing likes and matches surfaces on web/mobile.
- Stable empty/loading/error states.
- Mutual-like and unmatch flows confirmed in both experiences.

### Planned PR P7-02 — relationship safety invariants
- Re-verify block/unmatch/report ordering under concurrency.
- Ensure blocks immediately affect discovery, conversation access, notifications, community interactions and Blind Date eligibility.

### Planned PR P7-03 — match lifecycle audit/retention
- Define retention for ended matches/conversations consistent with safety/legal needs.
- Keep private audit separate from user-visible data.

---

## Phase 8 — Messaging realtime, reliability and richer conversation UX

Current delivery uses bounded polling. Production can launch with polling if load tests support it, but the transport should be intentionally decided.

### Planned PR P8-01 — messaging reliability contract
- Delivery ordering, idempotency, retry and pagination invariants.
- Network reconnect/offline behavior in Flutter and web.
- Conversation loading/error states.

### Planned PR P8-02 — realtime transport decision
- Measure polling load first.
- If justified, implement SSE for web/server events or another reviewed transport.
- Do not introduce WebSockets merely for novelty.

### Planned PR P8-03 — conversation product polish
- Read/delivery state only if backend semantics are implemented.
- Typing/presence only if privacy and load costs are accepted.
- No fake presence.

### Planned PR P8-04 — media messaging decision
- Explicitly decide whether image/media messages are in v1.
- If yes, reuse private-media authorization principles with separate limits/moderation.
- If no, keep text-only scope documented.

---

## Phase 9 — Notifications and re-engagement

### Planned PR P9-01 — notification preference model
- Per-category in-app/push/email choices.
- Safe defaults and quiet-hours capability if required.

### Planned PR P9-02 — mobile push foundation
- APNs/FCM-compatible provider abstraction.
- Device-token registration/rotation/revocation.
- Never place sensitive message/profile content in lock-screen payloads by default.

### Planned PR P9-03 — notification event delivery
- Match/message/community/moderation-safe events.
- Idempotent delivery records and retry policy.
- In-app notification state remains authoritative.

### Planned PR P9-04 — deep-link handling
- Route safely into the authenticated destination after notification tap.
- Preserve auth and experience selection.
- Validate revoked/blocked/deleted resource behavior.

---

## Phase 10 — Community and Blind Date production hardening

### Planned PR P10-01 — community UX completeness
- Feed pagination, membership, post/reaction/report states in both designs.
- Accessible compose and moderation feedback.
- Review abuse thresholds using test data before production.

### Planned PR P10-02 — Blind Date scheduling UX
- Timezone-safe picker on all platforms instead of platform-specific assumptions.
- Clear consent, cancellation and unmatched states.
- Calendar/time boundary tests.

### Planned PR P10-03 — operational safety
- Monitoring for pairing failures and abnormal abuse patterns.
- Re-test suspension/block/delete interactions.

---

## Phase 11 — Full dual-design product coverage

### Planned PR P11-01 — remaining user surfaces
Convert both Spark and Essence for:
- Likes/matches;
- full chat;
- Community;
- Blind Date;
- account/settings;
- consent/account deletion;
- notification settings;
- verification.

### Planned PR P11-02 — moderation/admin surfaces
- Keep staff tooling function-first.
- Spark/Essence need not create two visually extravagant moderator consoles, but shared design tokens/accessibility and consistent component states must apply.

### Planned PR P11-03 — visual regression matrix
- Web screenshots/goldens for 320/375/411/768/1024/1440px where meaningful.
- Flutter golden/widget matrix for both experiences.
- Approved-Figma comparison evidence.

---

## Phase 12 — Security, abuse and privacy production review

### Planned PR P12-01 — static/dependency security gates
- Finish CodeQL or equivalent supported scanning.
- Dependency vulnerability policy for Maven/npm/pub.
- Secret scanning/pre-commit guidance.

### Planned PR P12-02 — API security regression suite
- IDOR matrix across every owner/member/moderator endpoint.
- Invalid/expired/wrong-client JWT behavior.
- Request size, content type and pagination bounds.
- CSV/export formula protection and no-store behavior.

### Planned PR P12-03 — abuse-control review
- Validate report, message, community and auth abuse limits.
- Add login/recovery/API rate limiting where the identity provider/backend boundary needs it.
- Ensure rate-limit telemetry does not leak sensitive data.

### Planned PR P12-04 — privacy/data-retention matrix
- Enumerate personal-data classes, retention, deletion and audit exceptions.
- Verify account deletion removes/anonymizes all covered data.
- Add automated deletion completeness tests.

---

## Phase 13 — Observability and operational readiness

### Planned PR P13-01 — structured logging and correlation
- Request correlation ID.
- Structured, privacy-safe server logs.
- Redaction tests for tokens, subjects, object keys and user content.

### Planned PR P13-02 — metrics and health
- API latency/error metrics by route class.
- DB pool, deletion worker, moderation queue and messaging metrics.
- Health/readiness endpoints suitable for orchestration.

### Planned PR P13-03 — alert/runbook mapping
- Actionable alerts tied to documented owners/runbooks.
- No alerting on sensitive user attributes.

### Planned PR P13-04 — backup/restore rehearsal tooling
- Document RPO/RTO target.
- Staging restore procedure and verification checklist.

---

## Phase 14 — Performance and scale validation

### Planned PR P14-01 — non-production load harness
- Guarded k6 or equivalent harness.
- Production domains hard-blocked.
- Read-heavy discovery, messaging polling/realtime and write workloads modeled separately.

### Planned PR P14-02 — database query/index review
- EXPLAIN plans for discovery, messages, community, moderation and deletion jobs using production-like data volume.
- Add indexes only with evidence.

### Planned PR P14-03 — frontend performance budgets
- Web Core Web Vitals budgets.
- Flutter frame/render and image-memory budgets.
- Photo-first loading strategy and cache limits.

---

## Phase 15 — Staging AWS environment

**Requires explicit authorization before paid-resource provisioning.**

### Planned PR P15-01 — Terraform state/bootstrap finalization
- Reconcile PR #9.
- Terraform fmt/validate/test in CI.
- Remote state locking/backup policy.

### Planned PR P15-02 — runtime infrastructure
- VPC/network design with cost review.
- RDS PostgreSQL.
- ECS/Fargate or explicitly approved alternative runtime.
- ALB/TLS.
- runtime secrets/configuration.
- least-privilege task role for Cognito/S3/other required services.

### Planned PR P15-03 — staging identity/email/media
- Development/staging Cognito pool and app clients.
- SES staging configuration if required.
- private media bucket and policies.
- no production DNS cutover.

### Planned PR P15-04 — staging deploy pipeline
- GitHub OIDC, immutable artifact/image references and environment approvals.
- database migration gate.
- rollback procedure.

---

## Phase 16 — Live staging acceptance

### Planned PR P16-01 — Cognito acceptance suite
Real authorized staging tests for:
- sign-up;
- verification;
- sign-in;
- refresh rotation/grace;
- password recovery;
- sign-out/revocation behavior;
- account deletion / `AdminDeleteUser` behavior.

### Planned PR P16-02 — private media acceptance
- upload/validation/read authorization/delete;
- blocked-user access rejection;
- suspended/deleted-user behavior;
- IAM negative tests.

### Planned PR P16-03 — cross-platform journey acceptance
- Real test user on web, Android and iOS.
- onboarding → discovery → match → chat → block/report/delete.
- Spark and Essence on every supported platform.

### Planned PR P16-04 — accessibility and device matrix
- Screen reader/keyboard/focus/touch target checks.
- reduced motion.
- representative Android/iOS physical devices and browsers.

---

## Phase 17 — Legal, policy and store readiness

### Planned PR P17-01 — approved legal configuration
- Production Terms/Privacy versions and HTTPS URLs supplied by owners/legal review.
- Production startup fail-closed behavior verified.

### Planned PR P17-02 — mobile release metadata
- Final application IDs/bundle IDs.
- icons/splash/permissions/privacy descriptions.
- Android signing and iOS certificates/profiles through secure CI/store mechanisms, never Git.

### Planned PR P17-03 — store compliance package
- App privacy disclosures.
- moderation/reporting/contact process.
- age rating and account deletion disclosures.
- screenshots from approved production UI.

---

## Phase 18 — Production deployment and controlled rollout

**Requires explicit production authorization.**

### Planned PR P18-01 — production environment configuration
- isolated production state/environment;
- least-privilege IAM;
- production database/media/identity configuration;
- backup/monitoring confirmed before traffic.

### Planned PR P18-02 — production deployment rehearsal
- migration rehearsal from a production-like snapshot;
- rollback/fail-forward decision tree;
- smoke tests and monitoring dashboard ready.

### Planned PR P18-03 — controlled launch
- feature flag/default strategy for Spark/Essence;
- staged user percentage or invite rollout;
- crash/error/latency/moderation monitoring;
- explicit rollback thresholds.

### Planned PR P18-04 — post-launch hardening
- review real metrics and incidents;
- tune abuse/performance limits from evidence;
- close temporary launch flags only after stability window.

---

# 6. PR execution rules

Every implementation PR must:

1. Start from the latest intended base branch, normally `main`.
2. Implement one reviewable outcome; avoid giant cross-phase PRs.
3. State what is intentionally **not** implemented.
4. Include tests for new logic and regressions.
5. Run every applicable backend/web/Flutter workflow on the exact head.
6. Keep security, lint and test gates enabled; never bypass a failing required check.
7. Update `docs/project-status.md` after completion.
8. Add migration notes for every new Flyway migration.
9. Add screenshots/goldens for UI-complete claims.
10. Keep cloud apply, DNS, production deployment, signing credentials and paid resources outside ordinary feature PRs unless separately authorized.

## Suggested branch convention

- `feature/p<phase>-<short-scope>`
- `fix/p<phase>-<short-scope>`
- `docs/p<phase>-<short-scope>`

## Merge order

A phase may have parallel PRs only when their contracts are independent. Stacked UI branches must not be merged before the shared API/token/preference foundation they depend on.

---

# 7. Required CI matrix

## Backend
- Maven verify.
- Testcontainers PostgreSQL.
- Flyway upgrade/migration checks.
- authorization/IDOR tests.
- concurrency/idempotency tests for state-changing flows.

## Web
- unit tests.
- TypeScript/lint.
- production build.
- Playwright browser acceptance for both experiences where applicable.
- responsive screenshot/overflow checks.

## Flutter
- format.
- analyze.
- unit/widget tests.
- Spark/Essence golden matrix for converted screens.
- Android release APK and App Bundle compilation.
- iOS release no-code-sign compilation in CI.
- physical-device checks remain a manual/staging gate.

## Security / release
- supported static analysis.
- dependency review/vulnerability policy.
- guarded non-production performance smoke tests.
- infrastructure fmt/validate/test when infrastructure changes.

---

# 8. Release definition of done

PritiMitra is **production-ready** only when all of these are true:

- Spark and Essence cover every required user-facing flow on web, Android and iOS.
- Approved brand identity is invariant across both experiences.
- Actual photo-first profile delivery is authorized and privacy-safe.
- Verification/trust claims reflect server-confirmed status.
- Core dating/friendship/community flows pass real staging cross-platform tests.
- Blocking, reporting, suspension and deletion work across all relevant data paths.
- Approved Terms/Privacy configuration is active.
- Live Cognito/media behavior and least-privilege IAM have been validated.
- Security, performance, accessibility and visual regression gates pass.
- Backup/restore, observability, alerting and incident runbooks are tested.
- Android/iOS signing and store requirements are complete.
- Production infrastructure/deployment has received explicit authorization.
- A controlled rollout and rollback strategy is ready.

A green compilation or unit-test suite alone is not production readiness.

---

# 9. Immediate execution order from today

1. Review and merge PR #24 (spec) if no unresolved requirement conflict remains.
2. Rebase/reconcile and merge PR #26 (Phase 0 inventory).
3. Fix PR #27 brand-token violation; run exact-head CI; merge the typed Spark/Essence foundation.
4. Resolve PR #23 CodeQL/capability path independently; do not let it block product UI development if the blocker is repository settings rather than code.
5. Implement P1-04 semantic tokens.
6. Implement P2-01 local Experience selector.
7. Implement P2-02/P2-03 cross-device preference sync.
8. Begin the dual-design vertical slice (Phase 3).
9. Before claiming photo-first UI complete, implement Phase 4 authorized media delivery.
10. Implement the verification baseline before advertising verified profiles.

This sequence keeps current working product behavior intact while moving directly toward a premium, dual-design, production-grade release.