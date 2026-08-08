---
name: release-task-verify
description: Bidirectional verification between a git release tag and the corresponding Asana release task. Use this whenever the user asks to "verify a release", "check the release notes", "validate release 5.x.y", "did all PRs make it into the release notes?", "check if the Asana release task is complete", "audit release 5.x.y", or provides an Asana release task URL and asks if it's correct. Also trigger when the user wants to confirm that every PR in a release is accounted for in Asana, or that every item in Asana release notes has a corresponding PR.
allowed-tools:
  - Bash(bash .claude/skills/release-task-verify/scripts/get_commits.sh *)
  - Bash(bash .claude/skills/release-task-verify/scripts/get_pr_details.sh *)
---

# release-task-verify

Bidirectional verification between git release tag and Asana release task.

**Two-way check:**
1. **Git -> Asana**: Every PR in git has its Asana task listed in release notes
2. **Asana -> Git**: Every task in release notes has a corresponding PR in git

Both directions must pass for a pass. Any mismatch in either direction is a failure.

---

## Prerequisites

| Tool | Purpose |
|------|---------|
| `git` | Compare tags and commits |
| `gh` | Fetch PR details |
| `asana_typeahead_search` | Find release task |
| `asana_get_task` | Get release task content |

**STOP immediately if Asana tools are not available.** Do not produce partial output.

---

## Input

Accepts either:
- Version number: `5.268.0`
- Release task URL: `https://app.asana.com/.../task/1213355988593578...`

**Invalid (stop with error):**
- Board URLs (`/project/` + `/board`)
- Project URLs without task GID

---

## Step 0: Verify Asana access

Confirm `asana_typeahead_search` and `asana_get_task` are available.

If not available, stop with:
```
## Release Verification: FAILED

**Error:** Asana tools not available. This skill requires the Asana MCP server.
```

---

## Step 1: Validate input and resolve version

**If version number:**
- `input_type` = "version"
- `version` = the provided version number
- `release_task_gid` = None (looked up in Step 3)

**If task URL:**
- `input_type` = "url"
- Extract GID from URL (last numeric segment)
- Call `asana_get_task` with GID and `opt_fields: "html_notes,name"`
- Extract version from task name (e.g., "Android Release 5.265.0" -> "5.265.0")
- `release_task_gid` = the extracted GID
- `release_task_html` = html_notes (already fetched, skip Step 3)

---

## Step 2: Run git script

Run this script, replacing `{VERSION}` with the version number:

```bash
bash .claude/skills/release-task-verify/scripts/get_commits.sh {VERSION}
```

If the output starts with `error:`, stop and report the error.

**Excluded commit patterns:**
- `Merge branch 'release/X.Y.Z'` — release branch merges
- `Updated * for new release` — version bumps
- `Merge pull request * from *release` — release PR merges

Extract:
- `version`, `prev_version`
- `commit_count`, `pr_count`
- `prs` — list of PR numbers
- `pr_to_hash` map — for each commit line `HASH Message (#PR)`, map PR -> first 10 chars of hash

**Validation:** `commit_count` must equal `pr_count`. If not, stop:
```
## Release Verification: FAILED

**Error:** commit_count ({N}) != pr_count ({M}). Some commits are missing PR references.
```

---

## Step 3: Get Asana release task

**Skip if `input_type` = "url"** (already fetched in Step 1)

Call `asana_typeahead_search` with query `Android Release {version}`, resource_type `task`.

Select the task whose name starts with exactly `Android Release {version}`. Ignore tasks with "Internal" in the name.

If no exact match, stop: "Release task not found in Asana"

Then call `asana_get_task` with `opt_fields: "html_notes,name"`.

---

## Step 4: Extract tasks from release notes HTML

From `html_notes`, find the `<ul>` immediately following "This release includes:".

Only extract from that `<ul>` block. Stop at `</ul>`. Ignore links in other sections (e.g., "Reminders:", "See also:").

For each `<li><a data-asana-gid="...">Task Name</a></li>`:
- GID from `data-asana-gid` attribute
- Name from tag text content

Result:
- `asana_tasks` = {gid: task_name}
- `asana_task_gids` = set of GIDs
- `task_count` = number of tasks

---

## Step 5: Get PR details (titles + Asana GIDs)

Run this script, replacing `{PRS}` with space-separated PR numbers from Step 2:

```bash
bash .claude/skills/release-task-verify/scripts/get_pr_details.sh {PRS}
```

This fetches title AND Asana GID for every PR in one pass.

Build:
- `pr_to_gid` = {pr_number: task_gid or ""}
- `pr_to_title` = {pr_number: title}

An empty GID means the PR is missing the `Task/Issue URL:` line from the PR template.

---

## Step 6: Cross-reference

**Missing PRs (Git -> Asana):**
```
missing_prs = []
for pr, gid in pr_to_gid:
    if gid is empty OR gid not in asana_task_gids:
        missing_prs.append(pr)

MISSING_PR_COUNT = len(missing_prs)
```

**Duplicate task links:**
```
gid_to_prs = {}
for pr, gid in pr_to_gid:
    if gid is not empty:
        gid_to_prs[gid].append(pr)

duplicates = [(gid, prs) for gid, prs in gid_to_prs if len(prs) > 1]
DUPLICATE_COUNT = len(duplicates)
```

Duplicates indicate PRs that copied the wrong Task/Issue URL.

**Orphaned tasks (Asana -> Git):**
```
orphaned_tasks = []
for gid, task_name in asana_tasks:
    if gid not in gid_to_prs:
        orphaned_tasks.append({gid, task_name})

ORPHANED_COUNT = len(orphaned_tasks)
```

---

## Step 7: Determine status

```
TOTAL_ISSUES = MISSING_PR_COUNT + ORPHANED_COUNT

STATUS = "PASSED" if TOTAL_ISSUES == 0 else "FAILED"
```

No exceptions. Internal/tooling PRs still count as missing.

---

## Step 8: Generate report

Use this format exactly:

```
## Release Verification: {VERSION} — {STATUS}

**Comparing:** {PREV_VERSION} -> {VERSION}

### Summary

|  | Count |
|---|---:|
| PRs in git | {PR_COUNT} |
| Tasks in release notes | {TASK_COUNT} |
| PRs missing from notes | {MISSING_PR_COUNT} |
| Tasks without PRs | {ORPHANED_COUNT} |

{MISSING_SECTION}

{ORPHANED_SECTION}

### Verified

{VERIFIED_LIST}
```

**{MISSING_SECTION}** — omit entirely if MISSING_PR_COUNT = 0, otherwise:
```
### PRs missing from release notes (Git -> Asana)

**{TITLE}**
- Commit: `{COMMIT_HASH}`
- GitHub: https://github.com/duckduckgo/Android/pull/{PR_NUMBER}
- Asana: {ASANA_LINK}

(repeat for each missing PR)
```

Where `{ASANA_LINK}` is `https://app.asana.com/0/0/{GID}/f` if a GID exists, otherwise `NO ASANA TASK`.

**{ORPHANED_SECTION}** — omit entirely if ORPHANED_COUNT = 0, otherwise:
```
### Tasks without PRs in git (Asana -> Git)

| Task | Asana Link |
|------|------------|
| {task_name} | https://app.asana.com/0/0/{gid}/f |
(repeat for each orphaned task)
```

**{HOW_TO_FIX_SECTION}** — omit entirely if TOTAL_ISSUES = 0, otherwise emit a `### How to fix` section.

For each issue, diagnose the likely cause and give exact steps. Use these patterns:

**Pattern A — Missing PR with empty GID + orphaned task with matching title/topic:**
These are almost certainly the same item. The PR just didn't fill in `Task/Issue URL:`.
```
### How to fix

**PR #{PR_NUMBER} — missing Task/Issue URL**
1. Open https://github.com/duckduckgo/Android/pull/{PR_NUMBER}
2. Click **Edit** on the PR description
3. Find the `Task/Issue URL:` line and set it to:
   ```
   Task/Issue URL: https://app.asana.com/0/0/{ORPHANED_GID}/f
   ```
4. Save the description
```

**Pattern B — Missing PR with a GID that exists but isn't in Asana release notes:**
The PR links to a real task, but that task wasn't added to the release notes.
```
### How to fix

**PR #{PR_NUMBER} — Asana task not in release notes**
The PR links to https://app.asana.com/0/0/{GID}/f but it's not listed under "This release includes:".
1. Add it to the release task manually in Asana under "This release includes:"
2. Add the Asana tag `android-release-{VERSION}` to the task (e.g. `android-release-5.273.0`)
```

**Pattern C — Orphaned task with no plausible matching PR:**
A task appears in release notes but no PR in git links to it.
```
### How to fix

**"{TASK_NAME}" — no PR found**
This task (https://app.asana.com/0/0/{GID}/f) is in the release notes but no PR references it.
Find the PR that delivered this work and add:
   ```
   Task/Issue URL: https://app.asana.com/0/0/{GID}/f
   ```
to its description.
```

When both a missing PR and an orphaned task exist, try to pair them by title similarity before applying Pattern A. If you can't confidently pair them, apply Pattern B and Pattern C separately.

**{VERIFIED_LIST}** — for each PR where `pr_to_gid[pr]` is in `asana_task_gids`:
```
**{TITLE}**
- Commit: `{COMMIT_HASH}`
- GitHub: https://github.com/duckduckgo/Android/pull/{PR_NUMBER}
- Asana: https://app.asana.com/0/0/{GID}/f

(repeat for each verified PR)
```
