# Phase 7 — Trust and safety

## Status

Initial moderation/review tooling is in PR #10. This is a first implementation slice, not a production-readiness claim. AWS provisioning and DNS changes are explicitly out of scope.

## Implemented in this slice

- Private, cursor-paginated review queue for user_report records with status filters.
- Server-side moderator authorization based on the verified Cognito cognito:groups claim containing the exact group PritiMitraModerators.
- Audited decisions: dismiss a report, hide a reported community post, hide a reported profile from discovery, or suspend the reported account.
- Suspension requires a moderator note. Decisions are serialized by locking the report row and only pending reports can be decided once.
- Additive Flyway V11 migration for report review metadata and an append-only safety_audit_event table. Audit events do not duplicate report details or message bodies.
- Web moderation view at /moderation. The page is a presentation layer; backend group authorization remains the security boundary.
- Web BFF uses exact path, HTTP-method, query-parameter, and identifier validation for moderation routes.

## Moderator access

Cognito access tokens must carry cognito:groups containing PritiMitraModerators. The backend deliberately checks the group on every moderation request. Non-moderator access returns 403 MODERATOR_REQUIRED; a hidden or hard-to-find URL is not an authorization control.

No AWS/Cognito group was created by this PR. Until a trusted administrator configures the group and assigns authorized staff in the target user pool, moderation calls remain unavailable. Do not add the group to ordinary users.

## Decision behaviour

| Decision | Allowed report target | Result |
|---|---|---|
| DISMISS | Any | Marks the report DISMISSED; records an audit event. |
| HIDE_POST | COMMUNITY_POST | Changes a published or hidden post to HIDDEN; report becomes ACTIONED. |
| HIDE_PROFILE | PROFILE | Sets discovery visibility to false; report becomes ACTIONED. |
| SUSPEND_ACCOUNT | Any report with a remaining account | Sets the reported account to SUSPENDED; a non-empty moderator note is mandatory. |

Reports cannot be reviewed twice. Review notes are limited to 500 characters. Existing report evidence is kept; this slice does not delete a user, message, profile, or report record. A suspension is a high-impact action and must be used only by authorized staff under an established internal policy.

## Remaining Phase 7 work

- User-requested account deletion with Cognito identity deletion and durable privacy-safe audit.
- Clear controls for withdrawing optional consents while preserving required legal consent semantics.
- Rate limits/abuse controls for reporting, community posting and messaging, backed by tests.
- Staff audit-history review/export and documented incident/escalation procedures.
- End-to-end authorized Cognito group, browser and mobile checks. No live cloud verification is claimed.
