# PritiMitra — repository-wide engineering instructions

## Product and architecture
Build PritiMitra as a secure, privacy-respecting LGBTQ+ connection platform. Follow approved Figma designs and preserve the existing approved brand assets.
- Mobile: Flutter/Dart, Riverpod, GoRouter.
- Web: Next.js App Router, React, strict TypeScript.
- Backend: Java 21, Spring Boot 4.0.x, Spring Security, Spring Data JPA, PostgreSQL, Flyway, OpenAPI.
- Architecture: modular monolith first; do not introduce microservices without measured justification.
- Local infrastructure: Docker Compose. CI: GitHub Actions.
- Keep client UI implementations native to Flutter and React; share API contracts and design principles rather than UI components.

## Authoritative delivery sources
- GitHub `main` is the factual implementation baseline.
- Notion page `PritiMitra — Unified Low-Level Implementation Plan` is the execution/acceptance/cursor authority.
- Figma is the intended UI/UX authority; never claim visual parity without rendered comparison evidence.
- For Copilot Agent mode, use `.github/agents/implementation-agent.agent.md`.
- Follow `docs/engineering/ci-cost-control-policy.md` for evidence frequency and GitHub Actions cost control.

## Autonomous execution
1. Before changes, read this file, `docs/architecture.md`, `docs/implementation-roadmap.md` if present, `docs/project-status.md`, the CI cost policy, and the authoritative Notion cursor when available.
2. Inspect existing code and identify the next incomplete task. Implement it rather than repeatedly returning only a plan.
3. Work in small phases/PRs with explicit acceptance criteria. Preserve working behavior and avoid broad rewrites.
4. Run the relevant formatter, static analysis, tests, build, and configuration validation required by the active CI policy. Fix failures and rerun only after correcting deterministic causes.
5. Record only checks that actually ran and their actual results. Never claim visual validation without comparing rendered screenshots with approved Figma frames.
6. Update `docs/project-status.md` with changes, evidence, open risks, and next task after each merged work item/phase.
7. After every merge and phase milestone, update the PritiMitra Notion plan with PR number/URL, exact reviewed head SHA, actual CI evidence, merge SHA, platform/Figma evidence, remaining gaps and next cursor.
8. Continue to the next authorized work item without asking routine permission.
9. Read relevant installed framework documentation when APIs/conventions may have changed.
10. Do not add infrastructure or dependencies without a clear requirement and rationale.

## CI cost control
- Routine Flutter PRs: Ubuntu `pub get` + format + analyze + tests + Flutter Web release build only.
- Android/iOS native acceptance: required for native-sensitive changes, final phase heads and release candidates; do not burn macOS/native minutes on every routine PR.
- Routine web PRs: npm install/test/lint/build; Playwright reserved for auth/routing/material responsive changes, phase boundaries and RCs.
- Cancel superseded runs, cache dependencies, use bounded timeouts, keep failure artifacts short-lived, and never rerun an unchanged deterministic failure.
- Cost optimization changes evidence frequency only; it never waives final Android/iOS/web acceptance requirements.

## Security and privacy
- Never commit secrets, credentials, tokens, private keys, or real environment files.
- Validate and authorize every protected operation on the server. Treat user-generated content as untrusted.
- Use safe, bounded pagination, timeouts, rate limits, and idempotency for sensitive operations as appropriate.
- Never expose exact user coordinates to other users; public clients receive derived distance/nearby semantics only.
- Do not make raw profile-media S3 objects public for convenience.
- Do not send raw messages, exact location, Cognito subject, sexual-orientation profile data, or similarly sensitive attributes to third-party analytics without explicit privacy review.
- Ask before destructive data changes, production deployment, publishing, paid-resource use, DNS, signing/certificate changes or Terraform apply. Never bypass security controls.

## Quality gates
- Keep tests deterministic and use isolated test resources.
- Verify database schema changes with Flyway and do not edit migrations already applied in shared environments.
- Treat accessibility, reduced motion, responsive layouts, performance, privacy, trust-and-safety, and platform parity as acceptance criteria, not polish added at the end.
- Do not mark placeholders, TODOs, unimplemented API behavior, unverified platforms or unverified Figma parity as complete.
- Phase completion requires its defined exact-head phase-boundary evidence.
