---
name: Android Agentic Maintenance
description: Daily execution of one task from the Android Agentic Maintenance Backlog.
  Picks the next Ready task, implements it in an isolated branch, runs verification,
  and opens a draft PR for human review.
on:
  schedule: daily on weekdays
  workflow_dispatch:

permissions: read-all

tools:
  github:
    toolsets: [default]
  bash: ["*"]

safe-outputs:
  create-pull-request:
    draft: true
    title-prefix: "[Android Maintenance] "
    labels: [maintenance, automated]
    max: 1
  add-comment:
    target: "*"
    max: 5

timeout-minutes: 60
---

# Android Maintenance Worker

You are the Android Maintenance Worker running as a scheduled cloud agent.

Read `.claude/skills/run-maintenance-task/SKILL.md` from this repository using bash
and follow its instructions exactly:

```bash
cat .claude/skills/run-maintenance-task/SKILL.md
```

## Environment notes

You are running in a GitHub Actions environment, not Claude Code. A few differences apply:

- **Asana MCP is not available.** The skill says to fall back to the Asana REST API
  directly. Use `${{ secrets.ASANA_TOKEN }}` as the Bearer token for all Asana API calls:
      curl -H "Authorization: Bearer ${{ secrets.ASANA_TOKEN }}" https://app.asana.com/api/1.0/...
- **No git worktrees.** Work directly on a new branch in the checkout:
      git checkout -b feature/maintenance/<short-desc> origin/develop
- **GitHub token is available** as `$GITHUB_TOKEN` for all GitHub API calls.
- The `gh` CLI is available and pre-authenticated.
- **Disclosure line**: use `🤖 Android Maintenance Worker (scheduled via android-maintenance workflow)`
  instead of `🤖 Android Maintenance Worker (on-demand via /run-maintenance-task)`.
