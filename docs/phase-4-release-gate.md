# Phase 4 release gate

Scope: profiles and privacy, profile photos, discovery and pagination, directional likes, Flutter and web integration, and the photo storage infrastructure. Branch `feature/phase-4-profiles-discovery`, draft PR #5.

Verdict on the current head: **BLOCKED.** Automated checks pass. Live integration, runtime IAM, device, and visual checks are not done, and the gender and interest matching decision is open.

Results below were executed on the head recorded in section A. Do not reuse them for a newer commit.

## A. Automated CI (GitHub Actions)

Workflows: `.github/workflows/backend-ci.yml`, `web-ci.yml`, `flutter-ci.yml`. No Terraform workflow exists.

| Workflow | Status on the verified head | Result |
|---|---|---|
| Backend CI | PASS | Success on `14d7077` |
| Web frontend CI | PASS | Success on `14d7077` |
| Flutter mobile CI | PASS | Success on `14d7077` |
| Terraform | NOT RUN | No workflow. Local validation only (section B). |

Check the status for the current head with `gh run list --branch feature/phase-4-profiles-discovery --limit 6`. Confirm that `headSha` equals the PR head.

## B. Automated regression tests (local)

Run from the repository root unless noted. Java 21 and Docker are required for the backend (Testcontainers Postgres 17).

| Suite | Command | Result on verified head |
|---|---|---|
| Backend | `cd backend; .\mvnw.cmd -B verify` | PASS. 66 tests, 0 failures, 0 errors. Includes `ProfileDiscoveryApiTests` 17, `PhotoApiTests` 14, `ProfilePhotoMigrationTests` 3, `PhotoImageProcessorTests` 4. |
| Flutter analysis | `cd mobile; flutter analyze` | PASS. No issues. |
| Flutter tests | `cd mobile; flutter test` | PASS. 91 passed. |
| Web tests | `cd web; npm test` | PASS. 99 passed. |
| Web types | `cd web; npx tsc --noEmit` | PASS. Exit 0. |
| Web lint | `cd web; npm run lint` | PASS. Exit 0. |
| Web build | `cd web; npm run build` | PASS. Exit 0. |
| Terraform format | `cd infra\terraform; terraform fmt -check -recursive` | PASS. Exit 0. |
| Terraform init | `terraform init -backend=false -input=false` | PASS. Exit 0. |
| Terraform validate | `terraform validate` | PASS. Exit 0. The first attempt hit a provider plugin start timeout. The retry passed. |
| Terraform tests | `terraform test` | PASS. 6 passed, 0 failed. Mocked provider only. |

## C. Development-environment integration tests (NOT RUN)

There are no live integration tests in the repository. None were added in this pass, because they cannot be run without authorized resources. Add them only after a development environment is authorized.

Prerequisites:
- An authorized development AWS account with a profile-photo bucket and a runtime role (section E).
- A Cognito user pool and a test user with a complete onboarding profile.
- Environment variables for the backend's Cognito issuer and S3 bucket. Do not commit them.

Acceptance steps, to be run once per environment:

1. Sign in through Cognito and call `GET /api/v1/profile`. Expect the authenticated profile.
2. Update the profile and toggle discoverability. Confirm the change in the response.
3. Confirm that a discoverable, eligible account appears in `GET /api/v1/discovery` and that the viewer does not.
4. Page through discovery with `cursor` and `size`. Confirm no duplicates and that a malformed cursor returns 400.
5. Like and unlike a profile. Confirm the state persists across requests and that a repeated unlike succeeds.
6. Call a photo endpoint with a second user's token. Expect 404 for another user's photo ID.
7. Upload a valid JPEG and PNG. Confirm S3 persistence with `aws s3api head-object`, then list and retrieve the photo, then delete it.
8. Upload an invalid content type, an oversized file, and corrupt image bytes. Expect 400 or 413 without stack traces.
9. Remove the test users and photos.

Live tests must be opt-in and skipped by default, so the standard local suite never needs AWS credentials. Report them as passed only after a real run.

## D. Web and mobile authenticated workflows (NOT RUN)

Web (needs a running backend and a Cognito session):
- Profile load, update, and discoverability toggle.
- Discovery: initial load, load more, empty state, error state, retry.
- Like and unlike, and outgoing likes after refresh.
- Photo upload: progress, errors, display, delete confirmation.
- Expired session or 401: the user is returned to sign-in, and no token is exposed in the browser.

Flutter:
- Profile load, update, and discoverability toggle.
- Discovery scrolling and pagination.
- Like, unlike, and outgoing likes.
- Photo picker, upload progress, upload failure, retry, and deletion.
- Empty and error states, including session expiry.

Platform notes:
- Android: no photo-picker permission is configured in `mobile/android`. The system picker is expected to work on current Android versions, but this has not been tested.
- iOS: `NSPhotoLibraryUsageDescription` is present in `mobile/ios/Runner/Info.plist`. Whether `image_picker` returns HEIC is unverified. The server accepts only JPEG and PNG, so a HEIC selection fails validation. Confirm the user-facing message on device.

Device status: **BLOCKED / Device-UI verification.** No Android device, iOS device, or simulator was available. Windows, Chrome, and Edge only.

## E. Runtime IAM and S3 access (BLOCKED, Infrastructure)

What is established from the repository:
- `infra/terraform/photos.tf` declares the private profile-photo bucket, its ownership controls, public access block, encryption, lifecycle, and TLS-only policy.
- `infra/terraform/github-oidc.tf` declares only the GitHub deploy role.
- No compute resource (ECS, EC2, Lambda, App Runner, EKS) and no Dockerfile are declared. The backend's runtime platform is not defined.

Therefore the backend runtime IAM role cannot be identified, and none was created.

Required when the runtime is defined:
- Attach a role to the runtime. Grant only:
  - `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject`
  - Resource: `arn:aws:s3:::<profile-photo-bucket>/profiles/*`
- The application writes keys as `profiles/<userId>/<uuid>` (`PhotoService`), so the prefix matches.
- Do not grant `s3:ListBucket`, `s3:*`, ACL actions, or access to other buckets.

Close this blocker by declaring the runtime service and its role in Terraform, with a mocked-provider test asserting the resource scope.

## F. Migration V6 preflight (documented, not run against a database)

Table and column (`V5__profile_photos.sql`): `profile_photo.content_type`, `varchar(32)`. V6 replaces its CHECK constraint with `IN ('image/jpeg', 'image/png')`.

Read-only preflight, to run against each shared database before V6:

```sql
-- Count rows V6 would reject. Read only.
SELECT content_type, count(*) AS rows_blocking_v6
FROM profile_photo
WHERE content_type NOT IN ('image/jpeg', 'image/png')
GROUP BY content_type;
```

Interpretation:
- No rows returned: V6 can apply. It still needs its own deployment check.
- Any rows returned: V6 fails by design. Do not delete, rewrite, or convert them silently. Review the affected users, decide with the product owner whether to remove or re-process them, and document the decision before applying V6.

Status: documented here. Executed against a local clean-schema database by `ProfilePhotoMigrationTests` (which exercises the same constraint logic). **Not executed against any shared or production database.**

## G. Visual verification (NOT RUN)

Screens: profile, discovery, likes, photo management (web and Flutter).

Figma frames were not identified from repository documentation, so no node IDs are given here. Parity is not claimed. Blocked on: the Figma frame links for these screens, and a browser or device session for capture.

## H. Gender and interest matching (Product Decision Required)

Working filters: eligibility (ACTIVE, onboarded, discoverable, not self), age window, and connection-goal filter. These are enforced in SQL (`DiscoveryService`).

Not enforced: gender identity, pronouns, sexual orientation, and interests. Gender identity is free text, so reliable matching is not possible.

**Decision required:** does Phase 4 acceptance include matching by gender or interest?
- If no: keep them out of the filter contract, and keep the UI from implying they are enforced.
- If yes: define a closed, enumerated gender and preference model, plus a migration to map existing free text. Do not infer identity from free text.

This decision does not block the eligibility, privacy, and pagination gates. It does block any acceptance criterion that names gender or interest matching.

## I. Go/no-go checklist

| # | Gate | Status | Owner category | Next action / evidence to close |
|---|---|---|---|---|
| 1 | Backend CI on head | PASS | — | Re-check `gh run list` for the final head |
| 2 | Web CI on head | PASS | — | Same |
| 3 | Flutter CI on head | PASS | — | Same |
| 4 | Backend regression (66) | PASS | — | Rerun on final head |
| 5 | Flutter analyze and test (91) | PASS | — | Rerun on final head |
| 6 | Web test, tsc, lint, build | PASS | — | Rerun on final head |
| 7 | Terraform fmt, init, validate, test | PASS | — | Rerun on final head |
| 8 | Terraform CI workflow | NOT RUN | Code | Optional: add a CI job that runs section B Terraform commands |
| 9 | Live Cognito and S3 integration | NOT RUN | Integration Testing | Authorized dev environment; add opt-in tests per section C |
| 10 | Runtime IAM role and S3 scope | BLOCKED | Infrastructure | Declare runtime service and role per section E |
| 11 | V6 preflight on shared DB | NOT RUN | Integration Testing | Run section F query on each shared database |
| 12 | Web authenticated flows | NOT RUN | Integration Testing | Run section D against a dev backend and Cognito session |
| 13 | Flutter authenticated flows | NOT RUN | Device/UI Verification | Run section D on a device with the dev backend |
| 14 | Android device | BLOCKED | Device/UI Verification | Android device or emulator, plus the section D flow |
| 15 | iOS device | BLOCKED | Device/UI Verification | iOS device or simulator, plus HEIC check |
| 16 | Visual parity with Figma | BLOCKED | Device/UI Verification | Frame links and a capture session |
| 17 | Gender and interest matching | BLOCKED | Product Decision | Decision per section H |

Phase 4 must not be merged on automated tests alone. Gates 9 through 17 remain open.
