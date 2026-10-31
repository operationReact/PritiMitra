# PritiMitra CI Cost-Control and Evidence Policy

## Purpose
Reduce GitHub Actions cost and feedback time without weakening final Android, iOS, web, backend, security or release evidence.

This policy follows a simple rule: cheap focused checks run frequently; expensive platform acceptance runs when the changed surface or phase boundary actually requires it.

## Principles
1. Exact-head evidence only.
2. No unchanged reruns of deterministic failures.
3. Cancel superseded runs.
4. Prefer Ubuntu for routine CI.
5. Reserve macOS/iOS and heavy browser/native acceptance for meaningful gates.
6. Keep dependency caches enabled.
7. Set bounded timeouts.
8. Upload heavy artifacts mainly on failure and retain briefly.
9. Phase completion and release candidates always require the full evidence defined by the implementation plan.

## Gate matrix

| Change type | Routine PR gate | Extra gate | Phase/RC gate |
| --- | --- | --- | --- |
| Backend Java/API | Ubuntu Maven verify + config validation | targeted DB/security/integration checks | real integration acceptance as defined by phase |
| Flutter Dart/widget-only | Ubuntu pub get + changed-file format + analyze + tests + web release | none | Android + iOS + web acceptance |
| Flutter native-sensitive | Fast Flutter gate | manual native Android/iOS acceptance on exact head | full platform acceptance |
| Next.js ordinary UI/code | Ubuntu npm ci + tests + lint + build | none | browser acceptance |
| Web auth/routing/responsive shell | Fast web gate | Playwright/browser acceptance | browser acceptance repeated on final phase head |
| Terraform | fmt/validate/plan-without-apply as safe | security/static validation | apply only after explicit approval |

## Native-sensitive definition
Native validation is required when changes include any of:
- `mobile/android/**`
- `mobile/ios/**`
- Android/iOS permissions, entitlements or capabilities
- signing/build project settings
- Flutter native plugins or versions
- `pubspec.yaml` or `pubspec.lock` changes that affect native dependencies
- integration/native harness changes

## Normal Flutter PR
Required commands/concepts:
```bash
flutter pub get
# Format-check only Dart files changed by the PR/commit range.
dart format --output=none --set-exit-if-changed <changed-dart-files>
flutter analyze
flutter test
flutter build web --release
```

The repository contains historical Dart files that predate the strict formatter gate, so routine governance changes must not reformat unrelated production files just to make CI green. Newly changed Dart files must be formatted, while full-repository formatting can be handled as an explicit cleanup PR if desired.

Target: Ubuntu-only, bounded timeout, stale-run cancellation. Do not compile APK/AAB or use macOS for routine PRs.

## Native acceptance workflow
Manual or policy-triggered only. Required for native-sensitive PRs, final phase heads, and release candidates.

Android evidence:
- dependency resolution
- release APK compile
- release App Bundle compile when store readiness is relevant
- emulator/integration acceptance when required by phase

IOS evidence:
- dependency resolution
- release compile without signing in CI
- simulator/integration acceptance when required by phase
- signing/store checks remain explicit release gates

## Normal web PR
Required:
```bash
npm ci
npm test
npm run lint
npm run build
```

Playwright is reserved for auth/routing/session changes, material responsive shell changes, full UI phase closure, or RC evidence.

## Backend PR
Required:
- Docker Compose/config validation when relevant
- Java 21 setup with Maven dependency cache
- `./mvnw -B verify`
- Testcontainers/PostgreSQL integration already defined by repository tests

Add targeted jobs only when a change justifies them. Avoid duplicate Maven builds in multiple workflows.

## Workflow controls
Every routine workflow should use:
```yaml
concurrency:
  group: <workflow>-${{ github.event.pull_request.number || github.ref }}
  cancel-in-progress: true
```

Use conservative job timeouts. Default targets:
- routine Flutter: 8 minutes
- routine web: 10 minutes
- backend: 12 minutes
- browser acceptance: 15 minutes
- native build/acceptance: 25 minutes unless measured evidence needs more

Do not increase timeouts to hide deterministic hangs.

## Rerun policy
Never rerun an unchanged deterministic failure.

Allowed unchanged rerun examples:
- GitHub-hosted runner infrastructure outage
- temporary registry/package service outage
- documented external rate limit/network failure unrelated to repo code

Before rerunning, record why the failure is considered transient.

## Artifact policy
- Upload traces/screenshots/logs primarily on failure.
- Default retention: 3-7 days.
- Do not retain normal build artifacts unless required by release evidence.
- Never upload secrets, production auth tokens, private user data or raw sensitive test fixtures.

## Branch/PR policy
`main` is the integration authority unless the Notion implementation plan explicitly changes it.

A PR may merge only when:
- required exact-head checks are green;
- the changed surface's gate matrix is satisfied;
- required visual/Figma evidence is recorded for UI work;
- no approval-gated production/cloud operation is embedded;
- required Notion/project-status updates are ready.

## Phase boundary
The final phase head must run the complete acceptance required by that phase. Cost optimization is not permission to skip Android/iOS/web/native evidence at the phase boundary.

## Release candidate
Repeat full required platform acceptance on the exact RC head. Do not reuse stale phase-head evidence after RC changes.

## AWS/Terraform cost safety
Allowed without approval:
- Terraform code changes
- `terraform fmt`
- `terraform validate`
- backend-disabled init
- static/security checks

Explicit approval required before:
- `terraform apply`
- paid AWS resource creation
- deployment into paid staging/production runtime
- DNS changes
- production secrets/certificates/signing changes

## Evidence recorded after merge
Record in Notion and `docs/project-status.md`:
- PR number/URL
- final head SHA
- required workflow names/run IDs
- merge SHA
- platform evidence actually obtained
- Figma nodes/evidence for UI changes
- deferred phase-boundary evidence
- remaining gaps/blockers
- next cursor
