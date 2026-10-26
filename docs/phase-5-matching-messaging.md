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
- **Message ordering.** `seq` is an identity value allocated at insert time, so on its own it is not commit-ordered. Each send takes `FOR UPDATE` on the conversation row before it allocates `seq`, and holds that lock until commit or rollback. Sends to the same conversation are serialised. Sends to different conversations do not block each other. Lock order is match and conversation rows only, and unmatch and block never wait on the conversation row, so the two paths cannot deadlock. Because commit order therefore matches `seq` order, a reader that has seen `seq N` can rely on every lower `seq` being committed.
- `created_at` is set with `clock_timestamp()` at insert, not `now()`. The `now()` default is the transaction start time and would not reflect commit-order activity. No migration was needed.
- **Known limitation.** Under read-committed isolation, a block that commits while a send for the same pair is already in flight can let that one send through. Later sends are refused. The lock does not cover block writes, which take the `app_user` and `profile_match` rows.

## API contracts

Errors are RFC 9457 `ProblemDetail` with a top-level `code`. Page size defaults to 20 and the maximum is 50. Cursors are base64url-encoded UUIDs.

| Method and path | Purpose |
|---|---|
| `GET /api/v1/matches` | Cursor-paginated matches |
| `GET /api/v1/matches/{matchId}` | Participant-authorised details |
| `DELETE /api/v1/matches/{matchId}` | Unmatch |
| `GET /api/v1/conversations` | Participant conversation list with preview, unread count and the other profile's public ID. Ordered by latest message time, descending, with empty conversations last. Ties break on conversation public ID. Keyset cursor encodes both fields. |
| `GET /api/v1/conversations/{id}` | Conversation detail: status and other profile's public ID. Participant only. |
| `GET /api/v1/conversations/{id}/messages` | Newest-first keyset history, `seq` cursor. Response includes `latestCursor`. |
| `GET /api/v1/conversations/{id}/messages/newer?cursor=&size=` | Catch-up: messages with `seq` greater than the cursor, ascending, default 50. Returns `items`, `cursor` and `hasMore`. |
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

Chosen method: **bounded polling with a seq catch-up cursor.** The web thread and the Flutter controller poll `messages/newer` every 5 seconds from their last accepted cursor. The cursor starts at `latestCursor` from the newest history page. Each accepted page advances the cursor to the last message it returned. If `hasMore` is true, the client drains further pages straight away. On failure the delay doubles up to 60 seconds, and the cursor stays where it was. Messages are merged by message ID, so retries and overlapping pages never duplicate a message. Polling stops when the screen is closed. A new poll is not started while one is in flight.

The cursor is an opaque base64url value that encodes the conversation public ID and `seq`. It is validated against the conversation authorisation check. An invalid cursor returns `400 INVALID_CURSOR`. Because of the ordering rule above, a client that has accepted `seq N` will not miss a lower `seq` that commits later.

Limits:
- Delivery depends on the poll interval, not push. The 5-second and 60-second values are the current defaults and need product confirmation.
- Web polling is covered by unit tests only. It has not been checked in a browser.

SSE over the BFF is the next step within Phase 5. It requires a same-origin stream design and a `Last-Event-ID` catch-up. Neither exists yet.

## Notifications

Implemented as in-app records written in the same transaction as the match or message. Events: match created and new message. Reads are idempotent and scoped to the owner. Bodies are not included in notification payloads. A new-message notification is created once per stored message, so a retried send does not add another one. The notification list is paged on demand. No client polls it, so the `seq` cursor there does not need the catch-up treatment.

Blocking from a conversation uses the same `PUT /api/v1/blocks/{profilePublicId}` endpoint as discovery. The client closes the thread only after the server confirms.

Not implemented: push delivery, email delivery, and a background processor with retry tracking. Add these behind an adapter when a provider and credentials are available.

## Verification (actual runs)

| Area | Command | Result |
|---|---|---|
| Backend | `backend\mvnw.cmd -B clean verify` (JDK 21, Docker/Testcontainers) | BUILD SUCCESS, 101 tests, 0 failures, 0 errors. Includes 8 new `MessageDeliveryApiTests` (catch-up across pages, retry stored once, cursor scoping, cross-user 404, concurrent writers, activity sort, block-from-conversation, notification dedupe without body) |
| Flutter | `dart format` on changed files | 0 files changed after formatting |
| Flutter | `flutter analyze` | No issues found |
| Flutter | `flutter test` | All 112 passed. Includes 6 new `messaging_controller_test.dart` cases: multi-page catch-up with cursor advance, failed poll keeps cursor, overlap dedupe, block success, block failure, closed detail |
| Web | `npx vitest run` | 130 passed (includes new BFF allowlist tests for detail and `messages/newer`, and parser tests for `latestCursor`, catch-up and detail) |
| Web | `npx tsc --noEmit`, `npm run lint` | Exit 0 |
| Web | `npm run build` | Exit 0 |

Hot reload: no Flutter app was connected to the DTD instance, so no hot reload was run. Flutter behaviour is verified by the test suite above.

Not run: browser or device exercise, live Cognito or AWS, Figma comparison, Terraform (no infrastructure changed).


## Infrastructure and deployment

No AWS provisioning, Terraform apply, DNS, production database change, or deployment was performed. Local and CI tests need no real AWS credentials.

## Open items

1. Read-committed block race. A block that commits while a send for the same pair is in flight can let that one message through. Closing it fully needs the block path to take the conversation row lock, or a serialisable check. Not done in this pass.
2. SSE streaming through the BFF, replacing or augmenting polling.
3. Push and email delivery adapters and a notification processor with retry tracking.
4. Browser and device verification of the messaging screens.
5. Reports have no review queue or moderation tooling. Phase 7 owns that.
6. Polling intervals (5 s, 60 s backoff) need product confirmation.
