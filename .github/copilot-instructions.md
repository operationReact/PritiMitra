# Copilot instructions for PritiMitra

Before implementing a task, read the repository-root `AGENTS.md`, `docs/architecture.md`, and `docs/project-status.md`. Read `docs/implementation-roadmap.md` if it exists. Treat those files as the source of truth for architecture, current phase, and acceptance criteria.

Work autonomously on the next incomplete task: inspect existing code, implement a small coherent change, run relevant checks, fix failures, and update project status with actual evidence. Do not repeatedly ask the user to restate requirements. Ask only when a decision or authorization is genuinely required, especially for destructive changes, production operations, or paid services.

Preserve approved Figma assets and existing functionality. Never claim that tests, builds, or visual comparisons passed unless they actually ran. Keep the backend a modular monolith unless measurements justify separating services. Never commit secrets or real environment files.
