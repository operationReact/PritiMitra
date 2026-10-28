# Dual UI implementation inventory

Status: Phase 0 audit for issue #25. This inventory reflects the current repository on `main` and the approved Spark/Essence specification in PR #24. It does not claim either design is implemented.

## Architecture

| Surface | Stack | Current design foundation | Required dual-design work |
|---|---|---|---|
| Android | Flutter 3.47.8, Riverpod, GoRouter | `mobile/lib/core/theme/` contains colors, tokens and `ThemeData` | Add typed `Spark`/`Essence` variant model, variant-specific semantic tokens/composition, persisted preference, selector, widget/golden coverage |
| iOS | Same Flutter codebase with native iOS shell | Same shared Flutter theme foundation | Same product capability as Android, with Cupertino/native platform conventions, safe areas and iOS build/golden validation |
| Responsive web | Next.js 16 App Router, React 19, strict TypeScript | Global CSS and shared product/auth components; responsive workspace already exists | Add typed experience preference, semantic CSS tokens, server/client-safe persistence, selector and responsive Spark/Essence compositions |
| Backend | Java 21, Spring Boot 4.0.9, PostgreSQL/Flyway | Existing auth/profile/trust-safety contracts | Add preference API/storage only when needed for authenticated cross-device sync; design selection must never affect authorization or business rules |

## Current product-route inventory

### Web
Observed App Router surfaces include:
- Public landing
- Sign in / sign up
- Confirm sign up / resend verification
- Forgot/reset password
- Guided onboarding
- Home/dashboard
- Discover
- Profile/account surfaces
- Messages/conversations
- Community
- Blind Date
- Moderation/admin safety tooling
- Account lifecycle/privacy controls

### Flutter Android/iOS
The mobile app is organized under `mobile/lib/features/`, with shared routing/networking/theme foundations under `mobile/lib/core/`. The product roadmap and current architecture require equivalent mobile capability for authentication/onboarding, profiles/discovery, matching/messaging, Blind Date, community, trust/safety and account lifecycle controls.

## Design-system baseline

Existing Flutter design primitives:
- `mobile/lib/core/theme/app_colors.dart`
- `mobile/lib/core/theme/app_tokens.dart`
- `mobile/lib/core/theme/app_theme.dart`

Existing web styling baseline:
- `web/app/globals.css`
- route-scoped React components under `web/app/`
- shared logic under `web/lib/`

The dual-design implementation must extend these foundations rather than fork business logic.

## Approved variants

### Spark
- Vibrant pink/purple gradients
- Photo-first discovery
- Expressive cards and playful interactions
- Restrained glass overlays
- Purposeful animation with reduced-motion fallback

### Essence
- Editorial photo-first minimalism
- Refined typography and whitespace
- Restrained surfaces and overlays
- Prominent trust/privacy affordances
- Minimal motion with the same functional feedback guarantees

Neumorphism is explicitly excluded. Spark and Essence are not aliases for light/dark mode.

## Shared acceptance contract

For every user-facing slice:
1. Web, Android and iOS must expose equivalent product capability.
2. Platform-native layout/navigation conventions may differ.
3. Switching experience must not lose auth, navigation, forms, conversations, profile edits or in-progress flows.
4. Server authorization, privacy, consent and moderation behavior must remain invariant across designs.
5. Loading, skeleton, empty, error, retry and success states must exist in both variants.
6. Accessibility includes contrast, semantics/screen-reader labels, keyboard/focus on web, touch targets, safe areas and reduced motion.
7. Visual completion requires rendered screenshot/golden evidence; successful compilation alone is insufficient.

## PR-sized implementation order

### PR A — foundation
- Add typed `DesignExperience`/`DesignVariant` enum on Flutter and web (`spark`, `essence`).
- Add semantic variant token sets while preserving existing tokens as migration fallback.
- Add local persistence for anonymous/offline startup.
- Add Appearance → Experience selector with accessible preview cards.
- Do not add backend persistence yet unless the existing preferences/profile contract has a clean extension point identified by implementation review.
- Add unit/widget tests for default, persistence, invalid stored value fallback and switching without state reset.

### PR B — authenticated preference sync
- Add backend preference storage/API only if cross-device sync cannot reuse an existing safe profile/preferences contract.
- Reconcile local and authenticated preference deterministically at sign-in.
- Never expose design preference to discovery or authorization logic.
- Add API/authorization/idempotency tests.

### PR C — first vertical slice
Implement both Spark and Essence for:
- authentication shell
- guided onboarding
- home/discovery
- profile detail
- match/conversation entry and chat shell

Validate desktop/responsive web, Android and iOS behavior, including mid-flow switching and reduced motion.

### PR D — remaining product surfaces
Extend both variants to:
- Likes/matches
- Community
- Blind Date
- account/settings
- consent withdrawal and account deletion
- report/block/moderation surfaces where user-facing
- remaining loading/empty/error states

### PR E — quality gates
- Web Playwright matrix for both variants and key responsive widths
- Flutter widget/golden matrix for both variants
- Android release build and iOS no-code-sign release build
- accessibility checks and manual device acceptance evidence
- Figma comparison screenshots at approved breakpoints
- rollback/default strategy and release checklist

## Known blockers / non-goals

- PR #24 contains the approved specification but is not merged; its exact head was reviewed before this audit.
- PR #23 CodeQL is blocked by repository code-scanning capability/settings, not by product code.
- PR #9 Terraform/AWS remains outside this feature and must not be merged or provisioned without separate authorization.
- Physical-device acceptance, live Cognito/S3 tests, production legal policy approval and production deployment remain separate release gates.

## Next implementation action

Start PR A from `main`: add the typed experience abstraction and local persistence on Flutter and web, wired through existing theme/provider boundaries without duplicating business logic. Keep existing visual styling as the safe fallback until both variant token sets are connected and tested.