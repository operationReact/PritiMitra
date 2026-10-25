# PritiMitra — repository-wide engineering instructions

## Product and architecture
Build PritiMitra as a secure, privacy-respecting LGBTQ+ connection platform. Follow approved Figma designs and preserve the existing approved brand assets.
- Mobile: Flutter/Dart, Riverpod, GoRouter.
- Web: Next.js App Router, React, strict TypeScript.
- Backend: Java 21, Spring Boot 4.0.x, Spring Security, Spring Data JPA, PostgreSQL, Flyway, OpenAPI.
- Architecture: modular monolith first; do not introduce microservices without measured justification.
- Local infrastructure: Docker Compose. CI: GitHub Actions.
- Keep client UI implementations native to Flutter and React; share API contracts and design principles rather than UI components.

## Autonomous execution
1. Before changes, read this file, `docs/architecture.md`, `docs/implementation-roadmap.md` if present, and `docs/project-status.md`.
2. Inspect existing code and identify the next incomplete task. Implement it rather than repeatedly returning only a plan.
3. Work in small phases with explicit acceptance criteria. Preserve working behavior and avoid broad rewrites.
4. Run the relevant formatter, static analysis, tests, build, and configuration validation. Fix failures and rerun checks.
5. Record only checks that actually ran and their actual results. Never claim visual validation without comparing rendered screenshots with the approved Figma frames.
6. Update `docs/project-status.md` with changes, evidence, open risks, and the next task after each phase.
7. Read the relevant installed framework documentation when APIs or conventions may have changed.
8. Do not add infrastructure or dependencies without a clear requirement and rationale.

## Security and privacy
- Never commit secrets, credentials, tokens, private keys, or real environment files.
- Validate and authorize every protected operation on the server. Treat user-generated content as untrusted.
- Use safe, bounded pagination, timeouts, rate limits, and idempotency for sensitive operations as appropriate.
- Ask before destructive data changes, production deployment, publishing, or paid-resource use. Never bypass security controls.

## Quality gates
- Keep tests deterministic and use isolated test resources.
- Verify database schema changes with Flyway and do not edit migrations that have already been applied in shared environments.
- Treat accessibility, reduced motion, responsive layouts, performance, privacy, and trust-and-safety as acceptance criteria, not polish added at the end.
- Do not mark placeholders, TODOs, unimplemented API behavior, or unverified platforms as complete.
