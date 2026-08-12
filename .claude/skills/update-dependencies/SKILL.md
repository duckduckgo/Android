---
name: update-dependencies
description: >
  Use this skill to update Android library dependencies via refreshVersions. Invoke when the user
  asks to update, bump, or refresh dependencies or a specific library version, asks what updates are
  available, or hands over a dependency-update task from the Asana backlog. Covers running
  refreshVersions, judging which bumps are safe, handling libraries blocked by the project's Kotlin
  version, running the E2E suite, and the PR/Asana conventions for the change.
---

# Updating dependencies

Dependencies are managed via `versions.properties` using the `refreshVersions` Gradle plugin (its
version is pinned in `settings.gradle`). The only file that should change in a dependency update PR is
`versions.properties`.

---

## Running refreshVersions

```bash
./gradlew refreshVersions
```

This populates `versions.properties` with `## # available=X.Y.Z` comment hints after each entry.
After deciding on versions, strip the noise:

```bash
sed -i '' '/^##/d' versions.properties
```

**IMPORTANT**: The `####` header block at the top of `versions.properties` must never be removed.
It is required by the refreshVersions plugin at build time. If missing, the build fails with:
`Unable to find the version of refreshVersions that generated the versions.properties file`

---

## Kotlin version compatibility check

This is the most important judgement when evaluating a library update.

Read the project's Kotlin version from `version.kotlin` in `versions.properties`; `languageVersion` is
pinned in the root `build.gradle`. Some libraries publish releases compiled with a newer Kotlin than
the project targets, which fails the build at KSP time:

```
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is <newer>, expected version is <project>.
```

A library needing a Kotlin newer than the project's is a blocker, not something to work around.

### What to do when a library requires a newer Kotlin version

**Do not auto-revert.** Instead, **ask the engineer**:

> "Library X update from A → B requires a newer Kotlin than the project currently targets.
> Do you want to:
> 1. Skip this update for now
> 2. Include it as a separate task to upgrade Kotlin first"

The engineer may decide to batch multiple blocked libraries into a Kotlin upgrade task,
or simply defer them. Either way, document the decision in the PR and Asana task.

Do not keep a list of blocked libraries here — it goes stale as soon as the project's Kotlin moves.
The live source of truth is a `# Cannot update … because it requires Kotlin …` comment on the entry in
`versions.properties`; check for one, and add one when you defer a library for this reason.

---

## Library Classification

### Generally safe to update
- AndroidX libraries (`androidx.*`) — backward compatible, Java/Kotlin mixed
- Pure Java libraries (`zxing`, `org.json`, `robolectric`, `desugar_jdk_libs`)

### Needs Kotlin version check
- Any Kotlin-first library (Square, Cash App, JetBrains, etc.)
- Check whether the artifact's `.kotlin_module` metadata is newer than the project's Kotlin version

### Defer — requires dedicated migration
- Kotlin itself
- AGP (Android Gradle Plugin)
- Room
- Dagger / Anvil
- Coil
- Compose compiler
- `kotlinx.collections.immutable`
- RxJava (`rxjava2.rxjava`, `rxjava2.rxandroid`) — kept at current versions intentionally

---

## Testing

Use the E2E Nightly Full Suite GitHub Actions workflow to validate updates:

- **Workflow ID**: `223981529`
- **Workflow name**: "End to End Tests - Full Suite (Nightly)"

```bash
gh workflow run 223981529 --repo duckduckgo/Android --ref <branch>
gh run list --repo duckduckgo/Android --workflow=223981529 --limit=3
```

The workflow uploads internal + release APKs to Maestro Cloud and runs UI test suites.
Runtime ~1h30m. Uses `appId: com.duckduckgo.mobile.android` (release build).

---

## PR Conventions

- Only `versions.properties` should differ from `origin/develop`
- PR description must list exact version bumps (e.g. `androidx.appcompat 1.7.0 → 1.7.1`)
- Explicitly list deferred libraries and the reason (Kotlin 2.x, migration required, etc.)
- Link to the Asana task and the E2E workflow run

## Asana

Before starting a dependency update, read the canonical process task:
- **Task**: https://app.asana.com/1/137249556945/project/1202552961248957/task/1199899332680683?focus=true
- Use the Asana MCP to fetch task GID `1199899332680683` for the full checklist and process notes

Each dep update task should be a subtask of **[Doc] Update dependencies** (GID: `1202236475215890`).
Document updated libraries, deferred libraries, and Kotlin version blockers in the task notes.
