---
name: release-board-status
description: Combined release preview and board audit for Android. Shows what's in the next release cut, what's pending after LGC, and whether the "Waiting for Release" Asana board is accurate. Use whenever the user asks "what's in the next release?", "what will be released?", "preview release", "check the board", "any stale tasks?", "clean up the release board", "what PRs will be cut?", "what's pending after LGC?", or wants a combined release/board status view.
allowed-tools:
  Bash(bash .claude/skills/release-board-status/scripts/commits-to-release.sh),
  Bash(bash .claude/skills/release-board-status/scripts/pending-commits.sh),
  Bash(bash .claude/skills/release-board-status/scripts/audit-task.sh:*),
  Bash(bash .claude/skills/release-board-status/scripts/check-lgc.sh:*),
  Bash(bash .claude/skills/release-board-status/scripts/extract-gids.sh:*),
---

Show the full release picture: what's in the next cut, what's pending, and whether the Asana "Waiting for Release" board is accurate.

**Requires:** Asana MCP (`asana_get_tasks`, `asana_get_task`)

---

## STEP 1: GET RELEASE CONTEXT AND BOARD TASKS

Confirm the Asana MCP tools are available. If not, stop: "This skill requires the Asana MCP server."

Run all three in parallel:

**Release context:**
```bash
bash .claude/skills/release-board-status/scripts/commits-to-release.sh
```
Extract: `lgc_tag`, `latest_release`, `prs` block (PRs in this cut).

**Pending commits (after LGC):**
```bash
bash .claude/skills/release-board-status/scripts/pending-commits.sh
```
Extract: `pending_prs` block.

**Board tasks in "Waiting for Release":**
Call `asana_get_tasks` with:
- `section`: `614649293008196`
- `opt_fields`: `gid,name`
- `limit`: 100

---

## STEP 2: AUDIT BOARD TASKS + EXTRACT GIDS FOR PENDING PRS

Run in the same turn (all in parallel as separate tool calls — do NOT combine with `&`):

For each board task GID:
```bash
bash .claude/skills/release-board-status/scripts/audit-task.sh {GID}
```

Output format per task:
- `result:NO_PR_FOUND`
- `pr:N state:OPEN`
- `pr:N state:CLOSED`
- `pr:N state:MERGED released_in:5.X.Y` — stale
- `pr:N state:MERGED released_in:PENDING commit:HASH` — genuinely waiting
- `pr:N state:MERGED released_in:COMMIT_NOT_FOUND` — merged but commit not found in local history; treat as needing triage, not as already shipped

Also run (one call, all pending PR numbers):
```bash
bash .claude/skills/release-board-status/scripts/extract-gids.sh {PR1} {PR2} ...
```

---

## STEP 3: LOOK UP NAMES AND CHECK LGC

Run in the same turn (all in parallel as separate tool calls):

For each pending PR with a GID: call `asana_get_task` with `opt_fields: "name"`.
For each pending PR without a GID: `gh pr view {N} --json title --jq '.title'`

If any board tasks returned `released_in:PENDING commit:HASH`, check all at once:
```bash
bash .claude/skills/release-board-status/scripts/check-lgc.sh {lgc_tag} {commit1} {commit2} ...
```
Output: `{commit}:IN_LGC` or `{commit}:AFTER_LGC`.

---

## STEP 4: REPORT

```
## Release Board Status

**LGC:** {lgc_tag}
**Last release:** {latest_release}

---

### In this cut ({count})

PRs between {latest_release} and LGC — shipping in the next release.

**{Task name or PR title}**
- PR: https://github.com/duckduckgo/Android/pull/{N}
- Asana: https://app.asana.com/0/0/{GID}/f

(If commits block is empty: "Nothing in this cut — {latest_release} is the current release.")

---

### Waiting for release ({count})

Board tasks that are genuinely pending.

**{Task name}**
- PR: https://github.com/duckduckgo/Android/pull/{N}
- Asana: https://app.asana.com/0/0/{GID}/f
- Status: In next cut | Not included in latest LGC

---

### Not on board ({count})

PRs merged after LGC that are not represented on the "Waiting for Release" board.

**{Task name or PR title}**
- PR: https://github.com/duckduckgo/Android/pull/{N}
- Asana: https://app.asana.com/0/0/{GID}/f  (or "⚠️ No Asana task")

---

### Board correctness

Always show this section. Use a one-line summary followed by detail only if there are issues:

✅ Board looks clean — {count} tasks checked, all accounted for.

Or, if there are issues, replace the summary line and list them:

⚠️ {count} issue(s) found:

#### Already shipped — stale ({count})
These should be moved to Done.

**{Task name}**
- PR: https://github.com/duckduckgo/Android/pull/{N}
- Asana: https://app.asana.com/0/0/{GID}/f
- Released in: {version}

#### PR not merged ({count})

**{Task name}**
- PR: https://github.com/duckduckgo/Android/pull/{N} (OPEN)
- Asana: https://app.asana.com/0/0/{GID}/f

#### Needs triage ({count})

**{Task name}**
- PR: https://github.com/duckduckgo/Android/pull/{N} (CLOSED) / No PR found
- Asana: https://app.asana.com/0/0/{GID}/f
```

Omit subsections with 0 items. Keep output clean — no extra commentary.

**"Not on board" logic:** a pending PR is "not on board" if none of the board task GIDs match the GID extracted from that PR's description.
