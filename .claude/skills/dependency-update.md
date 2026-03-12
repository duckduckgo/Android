---
name: dependency-update
description: Use when asked to update Android dependencies. Guides the full refreshVersions workflow, Kotlin compatibility checks, E2E validation, and PR creation.
---

## Checklist

1. Fetch the canonical process task from Asana (GID: `1199899332680683`)
2. Run `./gradlew refreshVersions`
3. Review available updates — classify each as safe / Kotlin-check / defer (see `.cursor/rules/dependency-updates.mdc`)
4. For each Kotlin-first library: verify binary metadata version before bumping
5. Strip `##` comment lines from `versions.properties` (preserve `####` header block)
6. Commit `versions.properties` only — no other files should change
7. Trigger E2E Nightly workflow (ID: `223981529`) and monitor for failures
8. Update PR description with exact version bumps and deferred list
9. Update the Asana subtask (under GID `1202236475215890`) with results

Refer to `.cursor/rules/dependency-updates.mdc` for full details on each step.
