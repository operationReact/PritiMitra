# Copilot instructions for PritiMitra

Before implementing a task, read:

1. repository-root `AGENTS.md`
2. `docs/architecture.md`
3. `docs/project-status.md`
4. `docs/implementation-roadmap.md` when present
5. `docs/engineering/ci-cost-control-policy.md`
6. `.github/agents/implementation-agent.agent.md` when operating in Agent mode
7. authoritative Notion page `PritiMitra — Unified Low-Level Implementation Plan` when Notion MCP is available

Treat GitHub `main` as the factual implementation baseline, Notion as the execution/acceptance/cursor authority, and Figma as the UI/UX authority.

Work autonomously on the first incomplete or unverified task: inspect existing code, implement a small coherent change, run the minimum required cost-controlled checks, fix root causes, and update project status with actual evidence. Do not repeatedly ask the user to restate requirements or ask whether to continue after routine work.

For every merge, record the exact reviewed head SHA, required CI evidence, merge SHA, platform evidence, Figma nodes where applicable, remaining gaps and next cursor in `docs/project-status.md` and the Notion implementation plan.

Preserve approved PritiMitra Figma assets and existing functionality. Never claim tests, builds, device acceptance, Figma parity, load capacity or cloud state unless actually verified. Keep the backend a modular monolith unless measurements justify separation. Never commit secrets or real environment files.

CI cost control: routine Flutter PRs use the Ubuntu fast gate only; Android/iOS native acceptance is reserved for native-sensitive changes, exact phase boundaries and release candidates. Routine web PRs use tests/lint/build; Playwright/browser acceptance is reserved for auth/routing/material responsive changes, phase boundaries and release candidates. Never blindly rerun unchanged deterministic failures.

Explicit approval is required before Terraform apply, paid AWS provisioning, paid staging/production deployment, DNS changes, production secrets/signing changes, app-store publishing, destructive production data operations, legal owner decisions or irreversible cutovers.
