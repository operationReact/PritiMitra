# PritiMitra Architecture

## Stack

| Layer | Technology | Location |
|---|---|---|
| Mobile (Android, iOS) | Flutter 3.47.8, Riverpod, GoRouter | `mobile/` |
| Web (responsive browser) | Next.js 16, React 19, strict TypeScript (App Router) | `web/` |
| Backend API | Java 21, Spring Boot 4.0.9, Maven | `backend/` |
| Database | PostgreSQL 17, Flyway migrations | `backend/src/main/resources/db/migration/` |
| Local infrastructure | Docker Compose | `infra/docker-compose.yml` |
| CI | GitHub Actions (path-filtered per project) | `.github/workflows/` |

## Decisions

- **Spring Boot 4.0.9 instead of 3.x.** Spring Initializr no longer offers a 3.5 line (`bootVersion=3.5.4` returns HTTP 400). The user approved the 4.0.9 line. Dependencies use the Boot 4 starter names (`spring-boot-starter-webmvc`, `spring-boot-starter-flyway`, `flyway-database-postgresql`).
- **JVM timezone pinned to UTC.** The pgjdbc driver sends the JVM timezone to PostgreSQL. On the development machine (Asia/Calcutta), the server rejected the value. `BackendApplication.main` sets UTC before startup, and Surefire runs with `user.timezone=UTC`.
- **Mobile bottom navigation (proposed):** Discover, Likes, Blind Date, Community, Chats. Profile is reached from the avatar. The Figma bottom nav component still shows three tabs and needs updating.
- **Secrets stay out of git.** Each project has a `.env.example`. Real `.env` files are ignored by the root `.gitignore`.

## Data flow

- Mobile and web call the backend REST API at a configured base URL (`NEXT_PUBLIC_API_BASE_URL` for web). No API endpoints are implemented yet.
- The backend reads its datasource from `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`. Flyway applies migrations at startup.

## Local development

1. `docker compose -f infra/docker-compose.yml --env-file infra/.env up -d` starts PostgreSQL 17 on port 5432.
2. `backend/`: `mvn spring-boot:run` (set the `DB_*` variables from `backend/.env.example`).
3. `web/`: `npm ci`, then `npm run dev`.
4. `mobile/`: `flutter pub get`, then `flutter run`.

## Testing

- Backend tests use Testcontainers (`postgres:17`).
- Mobile tests are widget tests run with `flutter test`.
- Web checks are `npm run lint` and `npm run build`.
