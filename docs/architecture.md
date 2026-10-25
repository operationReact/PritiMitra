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

## Decisions

- **Spring Boot 4.0.9 is the selected backend baseline.** This is the version recorded in the Phase 1 setup decision and uses the Boot 4 starter names. Do not downgrade or change major versions implicitly. Review this choice deliberately when upgrading dependencies or preparing the production release.
- **Why not Spring Boot 3.5.x?** Spring's official announcement says 3.5.16 was the last open-source release of the 3.5 generation and recommends moving to 4.x for continued OSS support: https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/
- **JVM timezone pinned to UTC.** The pgjdbc driver sends the JVM timezone to PostgreSQL. The startup code sets the JVM default to UTC, and Surefire runs with `user.timezone=UTC`. Revisit this if a more targeted JDBC/session-timezone configuration is adopted; keep user-facing timestamps timezone-aware.
- **Mobile bottom navigation (proposed):** Discover, Likes, Blind Date, Community, Chats. Profile is reached from the avatar. The Figma bottom-nav component still needs updating from three tabs.
- **Secrets stay out of Git.** Commit only `.env.example` templates. Root `.gitignore` excludes actual dotenv files across all modules.

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
