# PritiMitra Copilot Autonomous Session Prompt

Use this prompt when starting a VS Code Copilot workspace/background/cloud session with `.github/agents/implementation-agent.agent.md` selected.

```text
Use the PritiMitra Strict Continuous Implementer workspace agent for operationReact/PritiMitra.

Before changing anything:
1. Read AGENTS.md.
2. Read docs/architecture.md.
3. Read docs/implementation-roadmap.md if present.
4. Read docs/project-status.md.
5. Read docs/engineering/ci-cost-control-policy.md.
6. Read the authoritative Notion page "PritiMitra — Unified Low-Level Implementation Plan" through Notion MCP.
7. Inspect current GitHub main, open PRs, exact base/head relationships, review state and exact-head CI.
8. For UI work, resolve the relevant PritiMitra Figma nodes through Figma MCP before claiming visual completion.

Execution behavior:
- main is the factual implementation baseline.
- Do not duplicate functionality already merged.
- Continue from the first incomplete/unverified Notion work item.
- Work phase-by-phase and PR-by-PR in small reviewable slices.
- Implement rather than returning only a plan when safe implementation is possible.
- Run the minimum CI required by the active cost-control policy.
- When CI fails, inspect logs and fix the root cause; never blindly rerun unchanged deterministic failures.
- Require required checks to be green on the exact current PR head before merge.
- Merge only after acceptance criteria are satisfied.
- After every merge, update docs/project-status.md and the PritiMitra Notion plan with PR, final head SHA, CI evidence, merge SHA, Figma references/evidence where applicable, remaining gaps and next cursor.
- Immediately continue to the next authorized work item. Do not ask "should I continue?" after normal PRs or phases.

PritiMitra product rules:
- Flutter Android + iOS are first-class production mobile platforms.
- Existing Next.js web remains supported.
- Java 21 / Spring Boot modular monolith remains backend authority.
- Spark + Essence must share business logic and immutable brand identity.
- Use approved logo node 15:2 and wordmark node 191:6; resolve exact screen nodes per UI PR.
- Preserve privacy, trust-and-safety, server-side authorization and account-deletion/consent semantics.
- Never expose exact user coordinates to other users.
- Do not make raw S3 profile media public.
- Do not send raw messages, exact location or sensitive LGBTQ+ profile attributes to third-party analytics without explicit privacy review.
- Design scale-ready boundaries for PostGIS, Redis, outbox/events, private media, workers and observability without premature microservices.

CI cost policy:
- Routine Flutter PR: Ubuntu pub get + format + analyze + unit/widget tests + web release build only.
- Native-sensitive Flutter PR: also run the manual native acceptance gate.
- Routine web PR: npm ci + tests + lint + build.
- Browser acceptance: auth/routing/responsive-shell changes, phase boundary, or RC.
- Phase boundary: full required backend/web/Android/iOS acceptance on exact final phase head.
- Release candidate: repeat full cross-platform acceptance on exact RC head.
- Cancel stale runs, cache dependencies, use bounded timeouts, upload failure diagnostics with short retention.

Do not perform without explicit human approval:
- terraform apply
- paid AWS resource provisioning
- paid staging/production deployment
- DNS changes
- production secret/certificate/signing changes
- app-store publishing
- destructive production data changes
- irreversible migration/cutover

If a genuine approval gate blocks one item, record the blocker and continue other independent work only when dependency ordering makes that safe.

Continue until the authoritative PritiMitra implementation plan is complete or no further safe work is possible without explicit approval/external dependency.
```

## Short restart prompt
Use this when the agent already knows the workspace:

```text
Continue PritiMitra from the current authoritative Notion cursor. Reconcile main, open PRs and exact-head CI first. Fix root causes, merge only on required exact-head green evidence, update project-status + Notion after every merge, then immediately continue to the next work item. Follow the CI cost-control policy and stop only at explicit production/AWS/DNS/signing/store/destructive/legal approval gates.
```

## Recovery prompt after a stopped/expired session

```text
Recover the PritiMitra implementation cursor from Notion and GitHub rather than assuming the previous session completed. Check main SHA, recently merged PRs, open PRs, exact-head CI and docs/project-status.md. Reconcile any drift, update Notion if GitHub is ahead, then continue from the first incomplete/unverified work item under the PritiMitra Strict Continuous Implementer rules.
```
