# PritiMitra Architecture

## Stack

| Layer | Technology | Location / status |
|---|---|---|
| Mobile (Android, iOS) | Flutter 3.47.8, Riverpod, GoRouter | `mobile/` |
| Web (responsive browser) | Next.js 16, React 19, strict TypeScript (App Router) | `web/` |
| Backend API | Java 21, Spring Boot 4.0.9, Maven | `backend/` |
| Database | PostgreSQL 17, Flyway migrations | `backend/src/main/resources/db/migration/` |
| Local infrastructure | Docker Compose | `infra/docker-compose.yml` |
| CI | GitHub Actions | `.github/workflows/` |
| Authentication | Amazon Cognito User Pools design; mobile SRP, web auth through the Next.js server-side boundary, Spring Security resource server | Code and Terraform definitions in `backend/`, `web/`, `mobile/`, and `infra/terraform/`; live Cognito integration has not been verified |
| Transactional email | Amazon SES configuration for Cognito verification and recovery email | Terraform definitions in `infra/terraform/ses.tf`; DNS verification/live sending are not confirmed |
| AWS infrastructure | Terraform, region `ap-south-1` (configurable), GitHub OIDC | `infra/terraform/`; resources have not been provisioned |

## Decisions

- **Spring Boot 4.0.9 is the selected backend baseline.** This is the version recorded in the setup decision and uses the Boot 4 starter names. Do not downgrade or change major versions implicitly. Review this choice deliberately when upgrading dependencies or preparing the production release.
- **Why not Spring Boot 3.5.x?** Spring's official announcement says 3.5.16 was the last open-source release of the 3.5 generation and recommends moving to 4.x for continued OSS support: https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/
- **JVM timezone pinned to UTC.** The pgjdbc driver sends the JVM timezone to PostgreSQL. The startup code sets the JVM default to UTC, and Surefire runs with `user.timezone=UTC`. Revisit this if a more targeted JDBC/session-timezone configuration is adopted; keep user-facing timestamps timezone-aware.
- **Mobile bottom navigation (proposed):** Discover, Likes, Blind Date, Community, Chats. Profile is reached from the avatar. The Figma bottom-nav component still needs updating from three tabs.
- **Secrets stay out of Git.** Commit only `.env.example` templates. Root `.gitignore` excludes actual dotenv files across all modules.
- **Infrastructure definitions are not deployed infrastructure.** Terraform formatting, validation, and mocked-provider/plan-only tests do not create AWS resources or prove live service behavior. Do not describe resources, DNS verification, or integrations as live until they have been explicitly authorized, provisioned, and tested.

## Authentication and account lifecycle (Phase 3)

The authentication foundation was merged to `main` in PR #3. Phase 3 remains in progress: the merge does not mean the complete web onboarding experience or live AWS integration has been completed.

- **Identity provider:** Amazon Cognito User Pools is the selected provider for sign-up, email verification, password reset, and access/ID/refresh tokens. The application does not store password hashes, verification codes, reset-token digests, or refresh tokens.
- **Resource server:** Spring Boot validates Cognito access tokens (signature, issuer, expiry, `token_use=access`, and `client_id`). The external identity key is the immutable Cognito `sub`, stored uniquely on `app_user`.
- **Web:** Next.js route handlers hold tokens server-side in `HttpOnly` cookies scoped to `/api`, with `Secure` enabled in production. Protected backend calls go through an allow-listed proxy. Tokens must not be stored in browser `localStorage` or `sessionStorage`.
- **Cognito app clients (declared in Terraform; live pool behavior is not yet verified):**
  - Mobile client: `ALLOW_USER_SRP_AUTH` only. Amplify performs SRP. Refresh uses the rotation path, not a refresh flow.
  - Web client: `ALLOW_USER_PASSWORD_AUTH` only, with no client secret. The browser sends the password over TLS to the Next.js route handler, which passes it to Cognito. SRP would not keep the password from that server, and running SRP in the browser would put tokens there. Custom SRP cryptography is not implemented.
  - `ALLOW_REFRESH_TOKEN_AUTH` is not enabled on either client because it is incompatible with refresh-token rotation. `infra/terraform/tests/cognito_auth_flows.tftest.hcl` guards this contract with a mocked provider. Flutter pins `amplify_auth_cognito_dart` 0.11.26, and `mobile/test/features/auth/data/amplify_auth_flow_test.dart` checks the version and that the mobile config sets no flow override. These are code/configuration tests, not a live Cognito test.
  - Refresh uses `GetTokensFromRefreshToken`. The declared configuration enables refresh-token rotation with a retry grace period (`cognito_refresh_retry_grace_seconds`, default 10, range 0–60). Rotated refresh tokens are persisted, including when the backend call then times out. A Server Component cannot set cookies, so it does not perform refresh.
  - Backend `COGNITO_CLIENT_IDS` lists both client IDs.
- **Live Cognito validation:** Sign-up, email verification, password recovery, refresh rotation, and Cognito deletion have not been verified against an authorized development user pool. In particular, `AdminDeleteUser` using the Cognito `sub` still needs a live test.
- **Profile verification:** Selfie or identity verification is not implemented. Profiles must not be labelled photo-verified or identity-verified.
- **Mobile token storage:** `amplify_auth_cognito` uses Amplify's native secure storage (Keychain/Keystore).
- **Email:** Terraform defines an SES identity, DKIM, custom MAIL FROM, and the intended Cognito email integration. Required SES DNS records have not been created and verified, so do not describe `mail.pritimitra.com` as a verified sending identity or claim live email delivery. Local Mailpit is not provisioned: Compose starts PostgreSQL only, the backend currently has no application-generated email flow, and Cognito mail does not route to Mailpit.

### Account states

Account states are explicit and must not be inferred only from whether Cognito authentication succeeded.

| State | Meaning | Protected APIs |
|---|---|---|
| `PENDING_EMAIL_VERIFICATION` | Cognito account exists; email not verified | Denied |
| `ONBOARDING_INCOMPLETE` | Verified; onboarding not completed | Denied |
| `ACTIVE` | Eligible and onboarded (set only by `complete`) | Allowed, subject to endpoint-specific authorization |
| `INELIGIBLE` | Failed the 18+ rule; no date of birth is stored | Denied |
| `SUSPENDED` | Restricted for safety, policy, or administrative reasons | Denied |

- **Age:** Enforced server-side. A client-side checkbox is never proof of eligibility. Underage attempts are marked `INELIGIBLE` with no date of birth stored, then purged after `PRITIMITRA_INELIGIBLE_RETENTION_DAYS` by a scheduled job that deletes the Cognito identity first.
- **Consents:** Append-only (`consent_record`). Each row records the policy version, timestamp, and account. Local versions are `terms-draft-v1` and `privacy-draft-v1`. Production startup fails unless `PRITIMITRA_LEGAL_REQUIRE_APPROVED=true` and approved versions and HTTPS URLs are set. Production legal versions and URLs have not yet been approved.
- **Interested-in:** Validated enum set (`MEN`, `WOMEN`, `NON_BINARY`, `ALL_IDENTITIES`). `ALL_IDENTITIES` is exclusive. Optional, never inferred from other profile fields.
- **Identity visibility:** Defaults to private. Authenticated access must not imply discovery visibility.

## Data flow

- **Web authentication and product calls:** Browser requests go through Next.js route handlers. The server-side auth boundary manages session cookies and the allow-listed proxy for protected backend calls; do not expose Cognito tokens to browser storage.
- **Mobile authentication and product calls:** Flutter uses Amplify Cognito for authentication and sends the access token when calling protected backend APIs.
- **Backend authorization:** Spring Security validates access tokens, then application services enforce account state, ownership, consent, and endpoint-specific access rules. Client-side checks are for UX only and are not security controls.
- **Database:** The backend reads its datasource from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Flyway applies migrations at startup.
- **Phase boundary:** Phase 3 authentication/account-lifecycle foundations exist in the repository, but the end-to-end onboarding UI is not complete. Phase 4 is in progress on draft PR #5. The backend profile, discovery, likes, and photo APIs, plus Flutter and web profile, discovery, and photo UI, are implemented and unit-tested. Live S3/Cognito and device or browser validation are not done.

## Profiles, photos, privacy, and discovery (Phase 4, in progress)

This section records the Phase 4 requirements and the current state. Implemented items are listed in `docs/project-status.md`. Gender and interest preferences are not enforced, because gender identity is free text.

### Profile APIs and privacy

- Use server-side field allow-lists for profile create/update requests and explicit response DTOs for discovery. Never expose email, Cognito `sub`, date of birth, consent records, internal moderation fields, or internal security state in public-profile responses.
- Only `ACTIVE`, fully onboarded accounts may appear in discovery. `PENDING_EMAIL_VERIFICATION`, `ONBOARDING_INCOMPLETE`, `INELIGIBLE`, and `SUSPENDED` accounts are never discoverable.
- Enforce profile ownership and discovery privacy on every server request. A client-side hidden profile or disabled control does not enforce access control.
- Keep discovery visibility user-controlled and private by default. Apply privacy filters in the backend query, not after returning results to the client.

### Photo media

- Profile media should use private object storage rather than streaming large media through the Spring Boot process.
- Use short-lived presigned upload authorization issued only after server-side authentication and authorization. Configure S3 Block Public Access, disallow public ACLs, require TLS, encrypt at rest, and grant least-privilege permissions.
- Validate file signature/content rather than trusting file extensions or client MIME types; enforce allowed formats, maximum byte size and dimensions. Treat an uploaded object as pending/unpublished until its validation and any required moderation complete.
- Define private read authorization and photo removal behavior before implementing delivery. Do not add a public object URL or public CDN URL to profile DTOs without a reviewed access model.
- Keep AWS creation and DNS changes out of feature implementation unless separately authorized. Terraform declarations alone do not mean a bucket exists.

### Discovery and likes

- Use bounded, stable cursor/keyset pagination with deterministic ordering. Validate filter values and cap page sizes; avoid unbounded offset scans and unrestricted result queries.
- Filter discovery server-side for account eligibility, visibility, blocked/reported relationships where applicable, and any supported discovery criteria.
- Store likes with a database-enforced unique directional pair. Repeated like/unlike operations must have deterministic, idempotent behavior.
- A like must not automatically create a match. Mutual likes create a match transactionally. Phase 5 contracts are in [phase-5-matching-messaging.md](./phase-5-matching-messaging.md). Real-time delivery is bounded polling, with SSE as a follow-up.

### Phase 4 test expectations

Cover profile field validation and ownership/IDOR boundaries, inactive-account exclusion, privacy-safe DTOs, photo upload authorization and validation, pagination boundaries, filter validation, and duplicate like/unlike requests. Mobile UI tests should cover loading, empty, error, retry, privacy, and photo-upload states. Do not claim tests passed until they have actually been run on the relevant branch.

## Local development

1. Copy `infra/.env.example` to `infra/.env` and set local-only credentials.
2. Run `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d` to start PostgreSQL 17. The database port is bound to loopback by default.
3. In `backend/`, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` using local values from `backend/.env.example`, then run the Maven wrapper (`./mvnw spring-boot:run` on Unix or `mvnw.cmd spring-boot:run` on Windows).
4. In `web/`, run `npm ci` then `npm run dev`.
5. In `mobile/`, run `flutter pub get` then `flutter run`.

## Testing and verification boundaries

- Backend context tests use Testcontainers with PostgreSQL 17.
- Mobile checks include Dart formatting, Flutter analysis, widget tests, and a Flutter web build; an iOS build still needs to be recorded for Phase 3.
- Web checks include ESLint, type checking, tests, and a production build.
- CI validates the applications; backend CI also validates the local Compose configuration.
- Terraform formatting, validation, and mocked-provider tests can validate configuration but do not apply infrastructure or validate live AWS behavior.
- Visual parity is not established by builds or unit tests. Compare rendered implementation screenshots against the approved Figma frames before claiming it.
- Phase 4 profile, photo, discovery, and like endpoints are implemented on the backend (see `docs/project-status.md`). Do not use the old Phase 1 statement that no product API endpoints exist, because it predates the authentication and account-lifecycle work.
