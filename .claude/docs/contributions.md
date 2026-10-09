# Contributions

## Branch Naming

```
<purpose>/<git-username>/<feature-name>
```

- **purpose**: `feature`, `fix`, `refactor`, `chore`, `poc`, etc.
- **name**: the author's name or alias
- **feature-name**: kebab-case description

Examples: `feature/jsmith/duck-ai-url-routing`, `fix/jsmith/bookmark-crash`

## Commit Messages

Use the imperative mood in the subject line (e.g. "Add feature X", not "Added feature X"). Keep the subject under 72 characters. Add a blank line before the body if more context is needed. Reference the Asana task URL in the body.

## PR body

Read @.github/PULL_REQUEST_TEMPLATE.md and use it as the PR body exactly — do not skip or reorder sections, do not add content above the first line of the template.

Fill in the template sections as follows:
- **Task/Issue URL**: the Asana task URL
- **Description**: summarise what was changed and why
- **Steps to test**: reviewer-facing steps per scenario; specific enough that someone unfamiliar with the code can follow them
- **UI changes**: before/after screenshots or recordings; write `No UI changes` in both columns for pure logic/backend changes

## Tech Design

Larger or cross-cutting changes — work that spans multiple modules or APIs, introduces a new integration, or otherwise can't be captured by a single self-contained API proposal — should also be backed by a **tech design** document. The tech design covers the overall approach; the API proposals cover each public surface it introduces. A big feature often has one tech design and several API proposals.

**Surface this early.** When you first scope a change, judge whether it's cross-cutting like this. If it is, tell the user up front that a tech design will be needed, so it can be written and reviewed before implementation — don't wait until PR time to raise it.

**Before opening a PR for a change of this size, ask the user for the tech design link** and add it to the `Tech Design URL` field of the PR body — do not open the PR unless the tech design exists and is linked, or the user explicitly confirms that no tech design is needed. Small, self-contained changes don't need one; leave the field blank.

## API Proposals

Any change to a public API — a `.kt`/`.java` file under a `*-api` module's `src/main` — must be accompanied by an **approved API Proposal**. `-api` modules are the contract other modules depend on, so the team reviews the public surface before it ships.

**Surface this early.** When you first scope a change, check whether it will touch a `-api` public surface. If it will, tell the user up front that an approved API Proposal is required, so it can be started before implementation — don't wait until PR time to raise it.

**Before opening a PR, check whether the changes touch a public API** (any `.kt`/`.java` file under a `*-api` module's `src/main`). If they do, stop and explicitly ask the user for the API Proposal link(s) — do not open the PR until they provide them. The proposal should exist and be linked before the PR goes up. If the user confirms the change does not alter the public surface (e.g. KDoc, comments, tests, dependency bumps), record `None` instead.

An API Proposal is an Asana task in the [Android Proposals project](https://app.asana.com/1/137249556945/project/1212087397361015/task/1216523494440439).

**In the PR:** list the proposal link(s) in the `API Proposals` field of the PR body — one per line; a single PR may carry more than one. Use `None` for the no-public-API-change case described above.

## Creating PRs

This repository uses [GitHub stacked PRs](https://gh.io/stacks) (`gh stack`) for stacked work.

- `gh stack init` to start a stack on top of `develop` (or `gh stack init <branch>...` to stack existing branches)
- `gh stack add <branch>` to add a new branch on top of the current stack
- `gh stack submit` to push all branches and create or update their PRs — pass `--auto` in non-interactive environments
- `gh stack sync` / `gh stack rebase` to keep the stack up to date with the remote and `develop`

For a single PR that isn't part of a stack, push the branch and open the PR with `gh api repos/duckduckgo/Android/pulls --method POST` — do not use `gh pr create`, which fails with a Projects Classic deprecation warning.

