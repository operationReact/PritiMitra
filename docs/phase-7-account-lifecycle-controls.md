# Phase 7 — Account lifecycle and consent controls

## Scope

This slice adds self-service account deletion and append-only optional consent controls to the existing Cognito + Spring Boot + PostgreSQL architecture. It does not provision or modify AWS infrastructure.

## Account deletion lifecycle

- `DELETE /api/v1/account` accepts an authenticated deletion request and returns HTTP 202 while cleanup is pending.
- The account is immediately marked `DELETION_PENDING`; regular product APIs remain unavailable while the request is pending.
- A scheduled worker retries pending work. It deletes the Cognito identity, deletes private profile-photo objects, removes the account's relational data, removes the account's personal consent rows under a transaction-local database guard, and removes authored community post content from feeds while retaining a removed placeholder for related safety reports.
- Completed requests clear the stored Cognito subject and retain only a SHA-256 digest, request/completion timestamps, status, and append-only lifecycle audit events. The digest is a pseudonymous anti-reprovisioning tombstone, not a claim that the digest is impossible to correlate.
- The tombstone prevents an otherwise unexpired JWT from causing the deleted account to be provisioned again.
- External cleanup failures are not reported as completed. The row stays pending for retry; logs omit the Cognito subject, photo keys, and raw exception details.

### Operational requirements

The scheduled cleanup runs every 30 seconds by default and processes up to 20 queued requests per pass. Monitor pending requests and generic cleanup warnings. The runtime identity needs least-privilege permission to delete users from the configured Cognito user pool and delete objects only from the private profile-photo bucket. These permissions and live behavior must be verified in the target environment; CI mocks do not prove AWS connectivity or IAM correctness.

## Consent controls

- `GET /api/v1/account/consents` returns the current effective optional community-nickname consent state.
- `PUT /api/v1/account/consents/community-nickname` records a new append-only acceptance or withdrawal only when the effective state changes. Retries of the same state do not create duplicate history.
- The browser reaches these endpoints only through the existing exact-route allow-listed BFF. Required Terms of Service and Privacy Notice consent is not represented as an optional toggle; users can request account deletion to withdraw the required consent needed to keep the account active.
- Community-nickname display behavior is not yet enabled. The UI states this explicitly; the stored preference is groundwork for that later feature.

## Verification and release gates

Automated tests cover: default consent state, grant/withdraw history, queued deletion, Cognito invocation, relational cleanup, community post removal, append-only audit evidence, and rejection of account re-provisioning after deletion. Web tests cover the proxy allow-list and the account UI build.

Before production release, run a controlled staging smoke test against the configured Cognito pool and private object storage, verify the deletion worker's permissions and retry telemetry, complete browser/mobile acceptance, and obtain approval for production Terms and Privacy versions/URLs. No live Cognito, S3, DNS, or production deployment is claimed by this code change.
