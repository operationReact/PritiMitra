# Phase 7 — Community and messaging rate limits

## Limits

- Community posting: a maximum of 10 persisted posts per account in a rolling hour, across all community groups. Posts subsequently removed by the author or hidden by moderation still count toward the limit.
- Messaging: a maximum of 30 new messages per account in a rolling minute and 300 per rolling hour, across all conversations.
- The API returns HTTP 429 with `COMMUNITY_POST_RATE_LIMITED` or `MESSAGE_RATE_LIMITED` once a limit has been reached.

## Concurrency and idempotency

Each limit uses a transaction-scoped PostgreSQL advisory lock keyed by internal user ID and action. The count and insert share the same database transaction, so concurrent requests for one account cannot all pass against the same pre-insert count. Posting and sending use separate action keys, so unrelated actions and different accounts do not share a global lock.

Message sends first check the existing `clientMessageId`. A retry that exactly replays a previously stored message returns the stored message and does not consume another rate-limit slot; reusing the same client ID with different content remains an idempotency conflict.

## Query performance and data minimization

Community post limits query the existing `community_post_author_idx`. Migration V13 adds `conversation_message_sender_created_idx` to support sender-scoped rolling-window message counts. No new per-action event table or additional behavioral history is introduced; rate-limit counts are derived from the existing persisted content timestamps.

## Verification

Backend integration tests cover the 10-post rolling-hour boundary and the 30-message-per-minute boundary, including idempotent message retry at the limit. The broader API suite verifies existing community membership, content moderation, block/unmatch serialization, and message history behavior remains intact.

The current numeric thresholds are server-side policy constants and should be revisited against observed abuse, legitimate usage and operational metrics after launch. This code does not deploy infrastructure or claim live device/cloud validation.
