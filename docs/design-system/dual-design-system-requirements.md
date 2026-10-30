# Dual Design System — approved product requirement

## Design authority

The implementation must use the approved PritiMitra Figma design file as the visual/UX authority for UI work. Canonical references:

- Figma file: `jMH2MRen8uohma80Xrw42B`
- Approved master logo: node `15:2`
- Approved wordmark artwork: node `191:6`
- Screen inventory: page/node `37:25`
- Product design pages: Brand & Design System, Onboarding & Authentication, Discover, Community, Blind Date, Matches & Chat, Profile & Preferences, Privacy & Safety, Account & Settings, Website Landing, Design System & Assets, V4 phase screens, and cross-phase E2E journeys.

Every frontend PR must identify the exact Figma node(s) it implements. A successful build or unit test is not visual acceptance; rendered implementation screenshots must be compared against the referenced Figma frames before visual parity is claimed.

## Approved variants

- **Design A — Spark:** expressive pink/purple gradients, photo-first discovery, playful but accessible profile cards, selective glass overlays, purposeful motion with reduced-motion fallback.
- **Design B — Essence:** elegant editorial photo-first minimalism, refined typography/whitespace, restrained surfaces and overlays, privacy-forward controls, minimal motion with equivalent feedback.
- **Explicitly excluded:** neumorphism / neumorphic styling. Do not reintroduce it.

Spark and Essence are distinct UX/composition experiences, not merely color themes. The approved PritiMitra logo, wordmark and core brand palette remain shared and immutable across both experiences. Differences should come from semantic composition, hierarchy, typography, spacing, density, surfaces and motion rather than arbitrary rebranding.

## Objective

Deliver two polished UI experiences across Flutter Android/iOS and the supported responsive Next.js web client while preserving one product/domain implementation. Keep business rules, API contracts, auth/privacy behavior, analytics semantics and trust/safety behavior shared.

## Required user experience

1. Add **Settings → Appearance → Experience** with accessible preview cards and instant selection between Spark and Essence.
2. Persist the selected experience locally for anonymous/offline startup and in authenticated user preferences when the backend preference contract is available; reconcile deterministically on sign-in and across devices without flicker.
3. Preserve deep link/route, navigation stack, authentication, onboarding progress, form edits, conversation state, profile edits and other in-progress product state when switching experiences.
4. Design mobile-first with Android/iOS safe areas, touch targets, keyboard handling, contrast, screen-reader semantics and reduced-motion support; web must provide keyboard/focus support and responsive desktop/tablet/mobile layouts.
5. Implement semantic design tokens and reusable primitives without duplicating business logic, networking, state management or backend services.
6. Loading, skeleton, empty, error, retry and success states must exist in both experiences where applicable.

## Feature coverage matrix

Both variants must support the applicable product capabilities: adult eligibility/onboarding, discovery, profiles, matching, chat, communities, Blind Date, moderation/reporting, consent/account deletion, privacy/safety and settings. Verification UI/indicators may only be shown after a server-backed verification capability exists; the current repository must not imply photo/identity verification that has not been implemented.

## Domain-specific non-regression rules

- Preserve privacy/consent, moderation, inclusive language and the approved logo/wordmark.
- Do not expose exact user coordinates or sensitive profile attributes through visual redesigns.
- Never make authorization, eligibility, visibility, moderation or account-state decisions client-side.
- Spark/Essence selection must not affect server authorization or product eligibility.

## Incremental implementation plan

- **Phase 0: Audit.** Inspect actual repo architecture, routes/screens, design assets, platforms, CI and tests. Maintain `docs/design-system/implementation-inventory.md` with screen/platform matrix, gaps, Figma references and implementation order.
- **Phase 1: Foundation.** Add typed experience identifiers, safe default/fallback behavior, semantic token foundation and tests. Preserve approved brand tokens across variants.
- **Phase 2: Preference.** Add accessible selector, local persistence, backend preference contract where needed, sign-in reconciliation and no-flicker startup.
- **Phase 3: Vertical slice.** Build both variants for authentication/onboarding, home/discovery, profile and match/chat entry across supported clients.
- **Phase 4: Full coverage.** Extend both variants to remaining product flows and all non-happy states.
- **Phase 5: Quality gates.** Run unit/integration/E2E, Flutter golden/web screenshot, accessibility and performance checks for both designs, including switching mid-flow. Capture evidence at relevant 320/375/411/768/1024/1440px web widths and representative Android/iOS devices.
- **Phase 6: Controlled rollout.** Add feature flag/default/rollback policy and privacy-safe adoption/regression metrics. No production deployment or paid-resource provisioning without explicit approval.

## Frontend PR evidence contract

Every frontend PR must record:

- exact Figma file/node references;
- experience(s): Spark, Essence or shared;
- platform(s): Flutter Android/iOS and/or Next.js web;
- implementation paths changed;
- formatter/static-analysis/unit/build checks actually run;
- rendered screenshot/golden evidence where the PR changes visuals;
- accessibility/reduced-motion evidence where relevant;
- known deltas from Figma and follow-up work.

## Definition of done

- Every applicable production screen exists in both experiences with no dead controls, inaccessible UI or lost functionality.
- Preference persists/reconciles correctly and switching does not lose in-progress state.
- Approved PritiMitra brand assets/tokens remain invariant.
- Platform builds/tests pass and rendered visuals have been compared with their exact Figma references.
- Accessibility and performance stay within approved budgets.
- `docs/project-status.md` and the Notion implementation checklist are updated with actual evidence after each merged PR/phase.

## Working instructions

Implement in small, reviewable branches/PRs; resolve ordinary CI and merge conflicts independently; never bypass safety checks, conceal failing tests, or assert completion based on documentation alone. Infrastructure apply, paid AWS provisioning, DNS mutation, signing-secret changes, store publishing and production deployment remain explicit approval gates.
