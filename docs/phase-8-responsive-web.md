# Phase 8 — Responsive web product experience

## Goal

Build an inviting, premium-feeling authenticated web workspace on desktop, tablet, and mobile, while reusing the same Spring Boot APIs and the existing brand/design tokens. Rich visual treatment must not imply data or product capabilities the API does not provide.

## Implemented in this first slice (PR #12)

- Shared route-aware product shell for `/home`, `/discover`, `/profile`, `/community`, `/blind-date`, `/messages` (including conversation routes), `/account`, and `/moderation`. The public landing page remains outside the authenticated shell.
- Desktop sidebar, condensed tablet navigation rail, mobile bottom navigation, active-route indication, consistent logo lockup, a privacy/consent-oriented sidebar note, and profile shortcuts.
- Data-backed `/home` overview showing conversation counts/unread messages, joined communities, active Blind Date opt-ins/pairings, and real profile visibility. When an API is unavailable, its metric is shown as unavailable instead of guessing a value.
- Responsive discovery cards with abstract visual covers and initials, structured profile prompts, connection-goal and interest chips, and the existing server-confirmed like action. Discovery responses do not include profile photos, so no fake person imagery is shown as real user photos.
- CSS-based visual polish and entrance/hover motion with a global reduced-motion accommodation; no additional UI dependency was introduced.
- Unit tests for product route detection, nested active navigation, and page-title selection.

## Design and accessibility decisions

- Maintain a calm, expressive violet/lilac/pink palette with strong typography, generous spacing, soft surface layers, responsive layouts, and restrained transitions.
- Keep navigation semantic and keyboard accessible; show visible focus rings and active-route state.
- Respect mobile safe-area insets and provide a touch-friendly bottom navigation.
- Keep loading, signed-out/incomplete-account, and unavailable-data states explicit.
- Preserve existing backend authorization and default-deny BFF rules; client-side navigation is not an access-control boundary.
- Do not add fake matches, messages, profile photos, notification counts, or “verified” badges.

## Validation and remaining work

CI is pending on PR #12. Before calling Phase 8 complete, test the full web journey at narrow mobile, large mobile, tablet, and desktop breakpoints; refine conversation and community interactions; capture and compare rendered screenshots against approved Figma frames; test accessible focus/keyboard and reduced-motion behavior; and complete the missing sign-in, sign-up, verification, recovery, and onboarding web UI tracked in Phase 3.

No AWS resources, DNS records, production data, or live user pools are touched by this implementation.
