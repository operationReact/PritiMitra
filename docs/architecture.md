# PritiMitra Architecture

## Stack

| Layer | Technology | Location |
|---|---|---|
| Mobile (Android, iOS) | Flutter 3.47.8, Riverpod, GoRouter | `mobile/` |
| Web (responsive browser) | Next.js 16, React 19, strict TypeScript (App Router) | `web/` |
| Backend API | Java 21, Spring Boot 4.0.9, Maven | `backend/` |
| Database | PostgreSQL 17, Flyway migrations | `backend/src/main/resources/db/migration/` |
| Local infrastructure | Docker Compose | `infra/docker-compose.yml` |
| CI | GitHub Actions | `.github/workflows/` |
| Authentication | Amazon Cognito User Pools (mobile: SRP; web: password flow over TLS to the BFF; refresh rotation with grace period), validated by Spring Security resource server | `backend/`, `web/`, `mobile/` |
| Transactional email | Amazon SES for Cognito verification and recovery emails | `infra/terraform/ses.tf` |
| AWS infrastructure | Terraform, region `ap-south-1` (configurable), GitHub OIDC | `infra/terraform/` |

## Decisions

- **Spring Boot 4.0.9 is the selected backend baseline.** This is the version recorded in the Phase 1 setup decision and uses the Boot 4 starter names. Do not downgrade or change major versions implicitly. Review this choice deliberately when upgrading dependencies or preparing the production release.
- **Why not Spring Boot 3.5.x?** Spring's official announcement says 3.5.16 was the last open-source release of the 3.5 generation and recommends moving to 4.x for continued OSS support: https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/
- **JVM timezone pinned to UTC.** The pgjdbc driver sends the JVM timezone to PostgreSQL. The startup code sets the JVM default to UTC, and Surefire runs with `user.timezone=UTC`. Revisit this if a more targeted JDBC/session-timezone configuration is adopted; keep user-facing timestamps timezone-aware.
- **Mobile bottom navigation (proposed):** Discover, Likes, Blind Date, Community, Chats. Profile is reached from the avatar. The Figma bottom-nav component still needs updating from three tabs.
- **Secrets stay out of Git.** Commit only `.env.example` templates. Root `.gitignore` excludes actual dotenv files across all modules.

## Authentication and account lifecycle (Phase 3)

- **Identity provider:** Amazon Cognito User Pools handles sign-up, email verification, password reset, and access/ID/refresh tokens. The application never stores password hashes, verification codes, reset-token digests, or refresh tokens.
- **Resource server:** Spring Boot validates Cognito access tokens (signature, issuer, expiry, `token_use=access`, `client_id`). The external identity key is the immutable Cognito `sub`, stored uniquely on `app_user`.
- **Web:** Next.js route handlers hold tokens server-side in `HttpOnly` cookies scoped to `/api`, `Secure` in production. Protected backend calls go through an allow-listed proxy. No tokens in `localStorage` or `sessionStorage`.
- **Cognito app clients (split by least privilege):**
  - Mobile client: SRP (`ALLOW_USER_SRP_AUTH`) and refresh flows only. Amplify performs SRP, so the password is never sent in the clear.
  - Web client: `ALLOW_USER_PASSWORD_AUTH` and refresh flows, no client secret. The browser sends the password over TLS to the Next.js route handler, which passes it to Cognito. SRP would not keep the password from that server, and running SRP in the browser would put tokens there. Custom SRP cryptography is not implemented.
  - `ALLOW_REFRESH_TOKEN_AUTH` is not enabled on either client. Cognito rejects it when refresh-token rotation is on. `infra/terraform/tests/cognito_auth_flows.tftest.hcl` guards this contract; `terraform test` runs it with a mocked provider. Amplify Flutter pins `amplify_auth_cognito_dart` 0.11.26, which supports rotation (0.11.14+). `mobile/test/features/auth/data/amplify_auth_flow_test.dart` checks that version and that the mobile config sets no flow override. Live refresh against a development user pool is not yet tested.
  - Refresh uses `GetTokensFromRefreshToken`. Both clients enable refresh-token rotation with a retry grace period (`cognito_refresh_retry_grace_seconds`, default 10, range 0–60). Every rotated refresh token is persisted, including when the backend then times out. A Server Component cannot set cookies, so it never refreshes.
  - Backend `COGNITO_CLIENT_IDS` lists both client IDs.
- **Profile verification:** Selfie or identity verification is not implemented. Profiles must not be labelled photo-verified or identity-verified.
- **Mobile:** `amplify_auth_cognito` stores tokens through Amplify's native secure storage (Keychain/Keystore).
- **Email:** Cognito sends verification and recovery mail through SES from the verified `mail.pritimitra.com` identity. Local Mailpit is not provisioned yet: the backend sends no application-generated email, and Cognito mail never goes to Mailpit.

Account states (explicit, not overloaded):

| State | Meaning | Protected APIs |
|---|---|---|
| `PENDING_EMAIL_VERIFICATION` | Cognito account exists; email not verified | Denied |
| `ONBOARDING_INCOMPLETE` | Verified; onboarding not completed | Denied |
| `ACTIVE` | Eligible and onboarded (set only by `complete`) | Allowed |
| `INELIGIBLE` | Failed the 18+ rule; no date of birth is stored | Denied |
| `SUSPENDED` | Restricted for safety, policy, or administrative reasons | Denied |

- **Age:** Enforced server-side. A client-side checkbox is never proof of eligibility. Underage attempts are marked `INELIGIBLE` with no date of birth stored, then purged after `PRITIMITRA_INELIGIBLE_RETENTION_DAYS` by a scheduled job that deletes the Cognito identity first.
- **Consents:** Append-only (`consent_record`). Each row records the policy version, timestamp, and account. Local versions are `terms-draft-v1` and `privacy-draft-v1`. Production startup fails unless `PRITIMITRA_LEGAL_REQUIRE_APPROVED=true` and approved versions and https URLs are set.
- **Interested-in:** Validated enum set (`MEN`, `WOMEN`, `NON_BINARY`, `ALL_IDENTITIES`). `ALL_IDENTITIES` is exclusive. Optional, never inferred from other profile fields.
- **Identity visibility:** Defaults to private.

## Data flow

- Mobile and web will call the backend REST API using configured base URLs (`NEXT_PUBLIC_API_BASE_URL` for web). Product API endpoints have not been implemented in Phase 1.
- The backend reads its datasource from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Flyway applies migrations at startup.
- Profile media should use private object storage and CDN delivery rather than streaming large media through the Spring Boot process. Upload authorization, validation, moderation, and access control belong in later feature phases.

## Local development

1. Copy `infra/.env.example` to `infra/.env` and set local-only credentials.
2. Run `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d` to start PostgreSQL 17. The database port is bound to loopback by default.
3. In `backend/`, set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` using local values from `backend/.env.example`, then run the Maven wrapper (`./mvnw spring-boot:run` on Unix or `mvnw.cmd spring-boot:run` on Windows).
4. In `web/`, run `npm ci` then `npm run dev`.
5. In `mobile/`, run `flutter pub get` then `flutter run`.

## Testing

- Backend context tests use Testcontainers with PostgreSQL 17.
- Mobile checks include Dart formatting, Flutter analysis, widget tests, and a Flutter web build.
- Web checks include ESLint and a production build.
- CI validates each application; backend CI also validates the local Compose configuration.
- Phase 1 is an engineering foundation only. There are no product API endpoints yet.
