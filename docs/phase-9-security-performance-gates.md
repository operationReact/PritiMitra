# Phase 9 — Security and performance gates

## Automated code scanning

The CodeQL workflow scans backend Java and web TypeScript/JavaScript with the `security-extended` query suite on relevant pull requests, pushes to `main`, and a weekly schedule. It publishes findings through GitHub code scanning; enable/resolve findings rather than disabling required security checks. Flutter/Dart is not covered by this CodeQL matrix and still relies on Flutter analysis/tests and focused review.

## Guarded API smoke load test

Run the `Non-production API smoke load test` workflow manually with `target_env=staging` and an HTTPS API origin, or `target_env=local` with a loopback URL. It:
- refuses to start without an explicit target environment and absolute base URL;
- blocks the PritiMitra production domains;
- restricts local testing to loopback hosts and requires HTTPS for staging;
- ramps only from 1 to 5 virtual users;
- checks `/actuator/health` latency and a fail-closed, unauthenticated request to `/api/v1/me`;
- sends no credentials and performs no account mutation.

This is a small baseline/availability smoke test, **not** a full load, capacity, soak, or authenticated journey test. Do not use it to infer production capacity. More intensive tests require an approved staging dataset, agreed traffic profile, quotas, and monitoring.

## Release gates still outstanding

- Run the smoke workflow against an authorized staging endpoint and record the actual result.
- Review CodeQL findings and dependency alerts on the latest default-branch head.
- Verify Cognito and private S3 deletion behavior and least-privilege runtime IAM in staging.
- Complete physical-device/browser acceptance, accessibility review, approved-Figma comparison, production legal configuration, backup/restore, incident-response tabletop, and deployment rehearsal.
- No Terraform apply, DNS change, production deployment, store upload, or paid cloud resource creation is included.
