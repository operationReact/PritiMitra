# Phase 5 — Matching, messaging, notifications and safety

Branch: `feature/phase-5-matching-messaging`, stacked on `feature/phase-4-profiles-discovery` (commit `41e2988`). Draft PR; do not merge. Phase 4 PR #5 remains a draft.

Status: **in progress.** Backend, Flutter and web messaging are implemented and tested locally. Live AWS, device, browser and Figma verification have not been run. See [Open items](#open-items).

## Product rules

- **Matching:** a match exists only when both users have an explicit like for each other. A unilateral like never creates a match. Repeated likes are idempotent. Simultaneous reciprocal likes converge on one match.
- **Eligibility:** both users must be active, fully onboarded accounts. Self-likes and blocks in either direction are rejected. Moderation restrictions are checked.
- **Gender and interest compatibility filtering is deferred.** Matching does not infer identity or attraction from free text. It is a future product and schema decision and does not block Phase 5.
- **Messaging:** text only, one conversation per match, participants only. The body limit is 2,000 characters, enforced on the server and in both clients.
- **Real-time delivery:** bounded polling with cursor catch-up (see [Real-time delivery](#real-time-delivery)). SSE is a follow-up within Phase 5.
- **Notifications:** in-app only. External push or email is behind a future adapter and is not claimed as delivered.

## Lifecycle decisions

| Event | Behaviour |
|---|---|
| Reciprocal likes | One `ACTIVE` match row, one conversation row. The unique key is the canonical unordered pair, `LEAST` and `GREATEST` of the user UUIDs. |
| Unmatch (`DELETE /api/v1/matches/{id}`) | Sets the match to `UNMATCHED` and deletes both likes, so a later match needs fresh mutual intent. Idempotent. Messages are kept. Sends return `409 CONVERSATION_CLOSED`. |
| Removing a like | Does not end an existing match. |
| Block (`PUT /api/v1/blocks/{profilePublicId}`) | Records one directional block. Checks run in both directions. Ends the match and closes the conversation. Excludes the pair from discovery. Future sends are refused. Idempotent. Unblock does not restore a closed match. |
| Report (`POST /api/v1/reports`) | Stored privately with status `PENDING`. Not returned in any discovery or profile DTO. Reporting does not block automatically. |

Message history and report evidence are retained. Nothing is silently deleted.

## Data model and integrity

- Matches and conversations use opaque public IDs. Internal sequential IDs and Cognito `sub` values are never exposed.
- Canonical pair ordering uses PostgreSQL `LEAST` and `GREATEST`. Locks are taken with `FOR NO KEY UPDATE` in ascending ID order.
- Messages have a unique `(sender, client_message_id)` key, so retries store at most one copy. Replaying the same body returns the stored message. A different body with the same key returns `409 IDEMPOTENCY_CONFLICT`.
- Migrations are additive and begin after V9. The applied migrations V1–V9 are unchanged.
- `seq` is assigned from a sequence. It is not commit-ordered across concurrent transactions. A reader can skip a lower `seq` that commits later. The polling catch-up can therefore miss a message in a narrow window. This is a known risk; see [Open items](#open-items).

## API contracts

Errors are RFC 9457 `ProblemDetail` with a top-level `code`. Page size defaults to 20 and the maximum is 50. Cursors are base64url-encoded UUIDs.

| Method and path | Purpose |
|---|---|
| `GET /api/v1/matches` | Cursor-paginated matches |
| `GET /api/v1/matches/{matchId}` | Participant-authorised details |
| `DELETE /api/v1/matches/{matchId}` | Unmatch |
| `GET /api/v1/conversations` | Participant conversation list with preview and unread count, ordered by public ID |
| `GET /api/v1/conversations/{id}/messages` | Newest-first keyset history, `seq` cursor |
| `POST /api/v1/conversations/{id}/messages` | Idempotent send with `{clientMessageId, body}` |
| `PUT /api/v1/conversations/{id}/read` | Advances the read cursor. Uses `GREATEST`, so it never moves backwards. |
| `GET /api/v1/notifications` | Cursor-paginated, own notifications only |
| `PUT /api/v1/notifications/{id}/read`, `PUT /api/v1/notifications/read-all` | Read state |
| `PUT` and `DELETE /api/v1/blocks/{profilePublicId}` | Block and unblock |
| `POST /api/v1/reports` | Report a profile or a visible message |
| `PUT` and `DELETE /api/v1/likes/{publicId}` | Like and unlike |

Non-participants and unknown IDs get consistent not-found responses.

## Web BFF

- The proxy policy is default-deny. Routes match on method plus an exact path, with a strict segment regex. Query parameters are validated per path. Unknown parameters are rejected.
- Disallowed paths return `404 not_found` before the CSRF check.
- Non-GET requests need a same-origin `Origin` and a JSON content type. Bodies are limited to 64 KB.
- Tokens stay in server-side cookies. Browser storage holds no Cognito tokens.

## Real-time delivery

Chosen method: **bounded polling.** The web thread and the Flutter controller both fetch the newest page every 5 seconds. On failure the delay doubles up to 60 seconds. Only unseen message IDs are merged.

Limits:
- Delivery depends on the poll interval, not push.
- The `seq` commit-ordering gap can delay or, in a narrow window, skip a message. Polling only re-reads the newest page, so a skipped message is recovered only if it is still within that page.
- Web polling is covered by unit tests only. It has not been checked in a browser.

SSE over the BFF is the next step within Phase 5. It requires a same-origin stream design and a `Last-Event-ID` catch-up. Neither exists yet.

## Notifications

Implemented as in-app records written in the same transaction as the match or message. Events: match created and new message. Reads are idempotent and scoped to the owner. Bodies are not included in notification payloads.

Not implemented: push delivery, email delivery, and a background processor with retry tracking. Add these behind an adapter when a provider and credentials are available.

## Verification (actual runs)

| Area | Command | Result |
|---|---|---|
| Backend | `backend\mvnw.cmd -B clean verify` (JDK 21, Docker, 4 min 36 s) | BUILD SUCCESS, 93 tests, 0 failures, 0 errors. Log: `files/backend-verify-phase5-final.log` |
| Flutter | `flutter analyze` | No issues |
| Flutter | `flutter test` | All 106 passed. Includes 5 new `messaging_controller_test.dart` cases: load, poll merge, session expiry, retry reuses `clientMessageId`, closed state |
| Web | `npx vitest run` | 121 passed across 12 files, including 10 new BFF route tests |
| Web | `npx tsc --noEmit`, `npm run lint` | Exit 0 |
| Web | `npm run build` | Compiled; prerender succeeds |

Not run: browser or device exercise, live Cognito or AWS, Figma comparison, concurrency stress tests against a running stack beyond the Testcontainers suite, and Terraform (no infrastructure changed).

## Infrastructure and deployment

No AWS provisioning, Terraform apply, DNS, production database change, or deployment was performed. Local and CI tests need no real AWS credentials.

## Open items

1. Block and report from inside a conversation. `MatchSummary` does not expose the other profile's public ID, which the block endpoint needs. The fix is to add `profilePublicId` to the match DTO with tests. This is pending a product decision.
2. `seq` commit-ordering gap. Mitigate with a commit-ordered cursor or document the bounded window, and add a concurrency test.
3. SSE streaming through the BFF, replacing or augmenting polling.
4. Push and email delivery adapters and a notification processor with retry tracking.
5. Browser and device verification of the messaging screens.
6. Reports have no review queue or moderation tooling. Phase 7 owns that.
