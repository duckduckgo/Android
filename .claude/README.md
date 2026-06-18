# AI Config — DuckDuckGo Android

This directory contains configuration for AI agents working in this repo — the skills they can run and the rules they must follow. Skills and rules are mirrored between `.claude/` and `.cursor/` via symlinks; `.claude/` is the source of truth.

---

## Skills

Skills are reusable workflows an agent can run. Invoke them by describing what you want in natural language — the agent picks the right one automatically — or explicitly with `/skill-name`.

| Skill | What it does | How to trigger |
|---|---|---|
| `check-translations-pr` | Checks whether a translations PR covers all supported languages and reports which are missing | "Are translations complete for PR #123?" / "Which languages are missing?" / "Are we good to ship?" on a PR with string changes |
| `review-public-api` | Reviews a `-api` module proposal against the team's API design heuristics and gives structured feedback | "Review my API proposal" / paste a proposal inline / give an Asana task URL for a proposal |
| `run-maintenance-task` | Executes a task from the Android Agentic Maintenance Backlog end-to-end: branches, implements, tests, and opens a draft PR | Give an Asana task URL from the maintenance backlog and say "run this" |
| `scope-maintenance-task` | Interactively scopes a maintenance idea into a properly formatted backlog task an agent can execute without ambiguity | "I want to add something to the maintenance backlog" / "Help me scope a maintenance task" |

---

## Rules

Rules encode the team's conventions so agents don't need them explained each time. All rules are always loaded in Claude Code. In Cursor, `architecture.mdc` is injected into every context window (`alwaysApply: true`); the rest are loaded on demand when the agent determines they're relevant.

| Rule file | What it covers |
|---|---|
| `architecture.mdc` | Module structure (`-api`/`-impl` split), DI with Anvil/Metro, plugin system, compile-time dependency rules |
| `android-design-system.mdc` | ADS components — buttons, text, inputs, switches, list items, dialogs, bottom sheets, colors, spacing, lint rules |
| `contributions.mdc` | Branch naming convention, commit message style, how to fill in the PR template |
| `maestro-ui-tests.mdc` | Maestro test setup, directory organization, tags, running locally and in CI |
| `pixels.mdc` | What pixels are, privacy requirements, pixel types, naming conventions |
| `pixel-definitions.mdc` | Creating and maintaining pixel definition JSON files for pixels and wide events |
| `wide-events.mdc` | Wide event API (`WideEventClient`), `FlowStatus`, `CleanupPolicy`, multi-step flow patterns |
| `dependency-updates.mdc` | How to safely update dependencies using `refreshVersions`, which files to touch |
