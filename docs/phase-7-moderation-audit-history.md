# Phase 7 — Moderation audit history

## Scope

Provide moderator-only review and CSV export for the existing append-only `safety_audit_event` stream. This is an operations and accountability feature; it does not replace application/security logs or incident response procedures.

## Data returned

Each audit row contains event ID, timestamp, event type, report public ID, target type/public ID, moderator note, and a SHA-256 actor reference. The raw Cognito subject stored for accountability is never returned to the browser or included in CSV. The endpoint does not join or export report details, profile bios, message bodies, email addresses, or other unnecessary target content.

## Filters and boundaries

- Filter by supported event type or all event types.
- Filter by start/end timestamp, with a default 30-day window and a maximum 90-day range.
- Cursor-paginated JSON review, up to 50 rows per page.
- CSV export is capped at 2,000 rows; larger exports must narrow the time window or event filter.
- Only the exact Cognito group `PritiMitraModerators` may read or export audit history. Browser visibility is not an authorization boundary.
- CSV cells are quoted and formula-dangerous prefixes are neutralized. Export responses use `Cache-Control: no-store`.

## Release gates

Configure the moderator group only for authorized staff. Complete browser acceptance with a staging moderator and a non-moderator account, verify that the non-moderator receives 403, and run an export with representative notes to confirm spreadsheet-safe handling. No AWS provisioning or production deployment is part of this code change.
