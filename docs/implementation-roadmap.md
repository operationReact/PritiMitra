# PritiMitra Implementation Roadmap

## Phase 1 — Engineering foundation
Status: complete and merged to `main`. Flutter, Next.js, Spring Boot, PostgreSQL/Flyway, Docker Compose, and CI are in place.

## Phase 2 — Design system and rich UI
Status: in progress.
- Establish semantic color, spacing, radius, typography, motion, and accessibility tokens.
- Implement the responsive marketing landing page against the approved Figma desktop and mobile landing frames.
- Upgrade the Flutter welcome screen with the same brand language and reduced-motion-aware entrance transitions.
- Add meaningful interaction feedback and responsive navigation.
- Run Flutter format/analyze/tests/web build, Next.js lint/build, and inspect rendered UI against Figma. Do not claim visual parity until actual screenshots are compared.

Acceptance criteria: CI green; welcome UI responsive and accessible; primary controls have clear behavior; motion respects reduced-motion preferences; authentication and app-store links are not described as implemented while they remain unwired.

## Phase 3 — Authentication and onboarding
Status: implementation committed and pushed on `feature/phase-3-auth-onboarding` (local commit `9c59a12`, pushed to origin). Not production-ready; no PR merged.
- Done: Cognito resource-server backend, explicit account states, enum interested-in, consent versioning with draft/approved guard, ineligible retention purge, Next.js secure session boundary, Flutter Amplify integration, Terraform skeleton (validated only).
- Outstanding: production legal versions and https policy URLs; SES DNS records (not created; awaiting authorization); live Cognito/SES integration tests in a development pool; ECS, ALB, RDS, and runtime secrets in Terraform (follow-up slice); `AdminDeleteUser` with `sub` verified in a dev pool; iOS build; Figma visual parity for auth screens.
- Costs to approve before provisioning: Cognito (per monthly active user), SES, ECS Fargate, ALB, RDS PostgreSQL, NAT gateway, CloudWatch, Route 53 hosted zone (existing zone used).

## Phase 4 — Profiles and discovery
Profile editing, photos, privacy settings, discovery pagination, filters, and likes.

## Phase 5 — Matching and messaging
Transactional match creation, conversation APIs, reliable message persistence, real-time delivery, notification jobs, and block/report integration.

## Phase 6 — Blind Date and community
Opt-in Blind Date flows, scheduling, community feed, groups, reactions, and moderation.

## Phase 7 — Trust and safety
Reporting queues, abuse prevention, consent controls, account deletion, audit events, and operational review tooling.

## Phase 8 — Responsive web product experience
Authenticated product flows on desktop/tablet/mobile web, using the same backend APIs and conceptual design tokens.

## Phase 9 — Release readiness
Performance/load tests, security review, backups/recovery, observability, deployment automation, store readiness, and cross-platform regression testing.
