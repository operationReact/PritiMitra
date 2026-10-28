# Phase 9 — Release readiness

## Current implementation slice: mobile release-build CI

The Flutter workflow now validates release compilation for:
- Web release build.
- Android release APK.
- Android Play Store App Bundle.
- iOS release compilation on a macOS runner using `--no-codesign`.

These are build gates only. They do not claim an Android/iOS signed release, physical-device acceptance, app-store upload, production configuration, or live identity/storage connectivity.

## Release gate matrix

| Area | Automated evidence | Remaining release gate |
|---|---|---|
| Backend and schema | Backend CI, Testcontainers PostgreSQL, Flyway migration tests | Authorized staging checks against the deployed runtime |
| Web | Unit tests, lint, production build, Playwright Chromium at desktop/mobile sizes | Accessibility review, approved-Figma comparison, authenticated staging flows |
| Flutter | Format/analyzer/tests and web build | Release APK and App Bundle CI; iOS no-code-sign build CI |
| Identity | JWT/account-state tests and fail-closed legal configuration tests | Live Cognito sign-up, verification, reset, refresh rotation, and deletion smoke tests |
| Private media | Photo validation/authorization tests | Verify target-environment S3 privacy and least-privilege runtime IAM |
| Legal | Automated rejection of draft policy configuration when approval is required | Owners must approve non-draft Terms/Privacy versions and valid HTTPS URLs |
| Trust and safety | Account deletion, consent history, report/moderation, and abuse-limit tests | Staging moderator/non-moderator verification, deletion retry telemetry, and incident-response tabletop |
| Operations | CI and documented runbooks | Cost/security review, backups/restore, load testing, deployment rehearsal, and monitoring ownership |

## Safety boundaries

- Do not run Terraform apply, create cloud resources, change DNS, or deploy from this CI change.
- Do not add production secrets or signing credentials to CI.
- The iOS job deliberately uses `--no-codesign`; it checks compilation only and is not an App Store deliverable.
- The Android package builds do not sign, upload, or publish to Google Play.
- A green build does not replace staging or device verification.

## Acceptance criteria for this slice

- Flutter formatting, static analysis, tests, and web release build pass.
- Android APK and App Bundle compile in CI.
- iOS release compilation succeeds without signing on macOS.
- Failures are surfaced as required CI checks; workflows must not suppress or ignore them.
