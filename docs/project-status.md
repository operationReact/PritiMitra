# Project Status

## Current delivery checkpoint (2026-10-28)

- PR #5 Phase 4, PR #6 Phase 5, and PR #8 Phase 6 are merged to main.
- Phase 7 moderation/audit tooling is merged via PR #10. It includes moderator-only decisions, append-only audit events, a 10-new-reports/account/rolling-hour limit, and suspension behavior that closes active matches, hides community posts, and cancels scheduled Blind Date pairings.
- Phase 7 account deletion and consent controls are merged via PR #16. Final-head Backend CI and Web CI passed. Live Cognito/S3 cleanup remains a controlled staging release gate.
- Phase 7 community/messaging limits are merged via PR #17: 10 community posts per account/hour, 30 new messages per account/minute and 300/hour, with transactional advisory locks and idempotent message retries preserved.
- Moderator audit-history review and CSV export are the current Phase 7 implementation item; it remains unmerged until final-head CI passes.
- Phase 8A responsive web shell and Phase 8B web authentication/onboarding are merged. Visual/browser acceptance and live Cognito flow verification remain outstanding.
- Phase 9 release readiness is active. Android release APK/App Bundle compilation and iOS no-code-sign release compilation are merged into Flutter CI; both jobs passed on the PR head. These checks compile only and do not sign or publish artifacts.
- The next Phase 9 slice is in draft PR #23: CodeQL analysis for backend/web and an opt-in k6 smoke test. Its target-selection safety tests passed on the latest branch head; the CodeQL analysis is still running. The real load test has not been run because an authorized staging endpoint has not been supplied.
- Remaining release gates: authorized staging Cognito/S3 checks, approved Terms/Privacy versions and HTTPS URLs, physical-device acceptance, accessibility and Figma comparison, runtime IAM verification, deletion-worker monitoring, cost/security review, load/restore tests, and deployment rehearsal.
- AWS/Terraform-state bootstrap PR #9 remains a separate draft. No AWS resources, DNS records or application deployment have been created by this coding work.