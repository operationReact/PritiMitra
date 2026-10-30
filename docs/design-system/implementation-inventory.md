# Dual UI implementation inventory

Status: Phase 0 audit for the Spark/Essence program. This inventory reflects the current repository baseline on `main` and the approved dual-design requirements. It does not claim either experience is fully implemented.

## Design authority

Figma file key: `jMH2MRen8uohma80Xrw42B`

Canonical references:
- Approved master logo: node `15:2`
- Approved wordmark artwork: node `191:6`
- Screen inventory: page/node `37:25`
- Product design pages: Brand & Design System, Onboarding & Authentication, Discover, Community, Blind Date, Matches & Chat, Profile & Preferences, Privacy & Safety, Account & Settings, Website Landing, Design System & Assets, V4 Phase Screens P0–P10, V4 Phase Screens P11–P21, and V4 Cross-Phase E2E Journeys.

Each frontend PR must resolve and record the exact implementation node(s) it targets. Visual completion requires rendered screenshot/golden comparison with those nodes.

## Architecture

| Surface | Stack | Current design foundation | Required dual-design work |
|---|---|---|---|
| Android | Flutter 3.47.8, Riverpod, GoRouter | `mobile/lib/core/theme/` contains colors, tokens and `ThemeData` | Typed Spark/Essence model, semantic experience tokens/composition, persisted preference, selector, widget/golden coverage |
| iOS | Same Flutter codebase with native iOS shell | Same shared Flutter theme foundation | Same product capability as Android, with safe areas/platform conventions, iOS release compilation and device/golden validation |
| Responsive web | Next.js 16 App Router, React 19, strict TypeScript | Global CSS and shared product/auth components; responsive workspace exists | Typed experience preference, semantic CSS tokens, server/client-safe persistence, selector and responsive Spark/Essence compositions |
| Backend | Java 21, Spring Boot 4.0.9, PostgreSQL/Flyway | Existing auth/profile/trust-safety contracts | Preference API/storage only when required for authenticated cross-device sync; design choice must never affect authorization or business rules |

## Current product-route inventory

### Web
Observed App Router surfaces include:
- public landing;
- sign in / sign up;
- confirmation/resend verification;
- forgot/reset password;
- guided onboarding;
- home/dashboard;
- discovery;
- profile/account surfaces;
- messages/conversations;
- community;
- Blind Date;
- moderation/admin safety tooling;
- account lifecycle/privacy controls.

### Flutter Android/iOS
The mobile app is organized under `mobile/lib/features/`, with shared routing/networking/theme foundations under `mobile/lib/core/`. Equivalent mobile product capability is required for authentication/onboarding, profiles/discovery, matching/messaging, Blind Date, community, trust/safety and account lifecycle controls.

## Design-system baseline

Existing Flutter design primitives:
- `mobile/lib/core/theme/app_colors.dart`
- `mobile/lib/core/theme/app_tokens.dart`
- `mobile/lib/core/theme/app_theme.dart`

Existing web styling baseline:
- `web/app/globals.css`
- route-scoped React components under `web/app/`
- shared logic under `web/lib/`

The dual-design implementation extends these foundations rather than forking product logic.

## Approved experiences

### Spark
- Vibrant pink/purple energy using approved brand tokens.
- Photo-first discovery.
- Expressive cards and playful interactions.
- Restrained glass overlays.
- Purposeful animation with reduced-motion fallback.

### Essence
- Editorial photo-first minimalism.
- Refined typography and whitespace.
- Restrained surfaces and overlays.
- Prominent trust/privacy affordances.
- Minimal motion with equivalent functional feedback.

Neumorphism is excluded. Spark and Essence are not aliases for light/dark mode and must not replace the approved PritiMitra brand palette with arbitrary experience-specific colors.

## Shared acceptance contract

For every user-facing slice:
1. Web, Android and iOS expose equivalent product capability where supported.
2. Platform-native layout/navigation conventions may differ.
3. Switching experience must not lose auth, navigation, forms, conversations, onboarding progress, profile edits or other in-progress flows.
4. Server authorization, privacy, consent, eligibility and moderation behavior remain invariant across experiences.
5. Loading, skeleton, empty, error, retry and success states exist in both variants where applicable.
6. Accessibility covers contrast, semantics/screen-reader labels, web keyboard/focus, touch targets, safe areas and reduced motion.
7. Visual completion requires rendered screenshot/golden evidence; successful compilation alone is insufficient.
8. Any verification badge/status must be server-backed; the current repository must not imply selfie/photo/identity verification before that capability exists.

## PR-sized implementation order

### PR A — typed foundation
- Add typed `DesignExperience` on Flutter and web (`spark`, `essence`).
- Preserve the existing approved brand tokens as immutable shared identity.
- Add safe default and invalid stored-value fallback.
- Add tests proving supported-value round trips and brand-token invariance.

### PR B — local preference and selector
- Add `Settings → Appearance → Experience` selector with accessible preview cards.
- Add local persistence for anonymous/offline startup.
- Prevent startup flicker and preserve current route/product state when switching.
- Add unit/widget tests for persistence, fallback and state preservation.

### PR C — authenticated preference sync
- Add backend preference storage/API only if cross-device sync cannot safely reuse an existing profile/preferences contract.
- Reconcile local and authenticated preference deterministically at sign-in.
- Never expose design preference to discovery or authorization logic.
- Add API authorization/idempotency tests.

### PR D — first vertical slice
Implement both Spark and Essence for:
- authentication shell;
- guided onboarding;
- home/discovery;
- profile detail;
- match/conversation entry and chat shell.

Validate responsive web, Android and iOS behavior, including mid-flow switching and reduced motion.

### PR E — remaining product surfaces
Extend both variants to:
- Likes/matches;
- Community;
- Blind Date;
- account/settings;
- consent withdrawal/account deletion;
- report/block/moderation surfaces where user-facing;
- remaining loading/empty/error states.

### PR F — quality gates
- Web Playwright matrix for both experiences and key responsive widths.
- Flutter widget/golden matrix for both experiences.
- Android release APK/App Bundle and iOS no-code-sign release compilation.
- Accessibility checks and manual device acceptance evidence.
- Figma comparison screenshots for exact referenced nodes and approved breakpoints.
- Rollback/default strategy and release checklist.

## Current dependencies / controlled gates

- The dual-design requirements specification is a documentation dependency and should be merged once its reviewed head is current and mergeable.
- PR #27 implements the typed foundation and must remain unmerged until exact-head CI passes and the brand-token invariance correction is present.
- PR #23 security scanning/smoke-test work remains a separate release-readiness track.
- PR #9 Terraform state bootstrap is a separate infrastructure track. Terraform code may be reviewed/merged without provisioning, but `terraform apply`, paid AWS resource creation, DNS mutation and deployment require explicit authorization.
- Physical-device acceptance, live Cognito/S3 checks, production legal approval, signing/store publishing and production deployment remain controlled release gates.

## Next implementation action

Complete PR A on the existing typed-foundation branch, validate exact-head CI, merge only when the approved brand-token contract is preserved, then proceed directly to local preference/selector work. Update `docs/project-status.md` and the Notion implementation checklist after each merge.
