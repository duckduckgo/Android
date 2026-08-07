---
paths:
  - ".maestro/**"
---
# Running Maestro UI Tests

## Prerequisites

UI tests need a `release` build type. Use the `play` flavour by default, or `internal` when the test
needs the extra testing functionality (including remote config patches — see below).

## Setup

Tests live in `.maestro/`, grouped into a directory per feature area. List that directory to see the
current areas rather than assuming a fixed set.

## Shared flows
Inside `.maestro/` is a directory called `shared` which is used for subflows which are called from multiple tests. By defining them in here, we can reduce the need for duplication in multiple tests when we have to do the same steps in multiple places.

Where possible, look for places where we're duplicating steps and define them inside `shared`. An example of running a shared flow: 

`- runFlow: ../shared/skip_all_onboarding.yaml`

Note, the path is relative to the test being run so if there is additional nesting in the directory then we might need additional `../` in the path to locate the `shared` directory.


### Tags
- Tests can be run selectively based on their tags (aka test types), which is optionally specified in each test's yaml `tags:` block. Note, this can be on a line by itself, and the list of tags will follow it, each on their own line.
- A test can have 0, 1 or more tags defined for it. Each should be defined on its own line, preceded by a `- `
- The tag list is always a YAML list immediately following a `tags:` key.

## Running the Tests
Maestro tests can be run locally or on Maestro Cloud.

### Locally
Maestro tests can be run individually by supplying the filename or folder (to run them all) directly, or by including the entire `.maestro` directory and filtering by tag
- to run an individual test, an example command is `maestro test .maestro/autofill/1_autofill_shown_in_overflow.yaml`
- to run all tests in a directory, an example command is `maestro test .maestro/autofill`  

#### Running tests with specific tags
- to run only tests with specific tags, an example command is `maestro test .maestro --include-tags releaseTest,adClickTest` where this would run `releaseTest` and `adClickTest` but no other types
- the flag is `--include-tags`; `--tag` and `--tags` are not valid Maestro flags

## Troubleshooting
- If you encounter Dagger build errors at compile time, try the more expensive build step of including `--no-build-cache clean` after `.gradlew` and before the other build commands.

## Maestro Development Guidelines

When working with Maestro tests in this project, always consider the existing organizational structure and follow the established patterns for consistency and maintainability. 

### File Structure

- All Maestro test files use `.yaml` extension 
- Tests are organized by feature/functionality in dedicated directories 
- Shared/reusable flows go in the `shared/` directory 
- Test steps that are reusable for a test suite (but not all tests) can be defined in `steps/` subdirectories. This can also be used for supporting JavaScript files for a test. 

### Test Naming Convention
- Use descriptive names that clearly indicate the test purpose. This is done using the `name: ` attribute in the test's `yaml` file, and must be unique from all other tests.
- Include the feature name as a suffix for the test names.
- Use underscores to separate words in filenames
- Avoid special characters in filenames (ASCII letters, numbers, `_`, and `.` are all acceptable)

### Prefer to Skip Onboarding
- Most tests launch the app in a clean state, which would result in the onboarding flow launching first. Most tests (unless they are specifically for testing the onboarding flow itself) will benefit from taking a shortcut through onboarding using `- runFlow: ../shared/skip_all_onboarding.yaml`

### Retries
- Use `retry` block to mark that a test can be retried (if any of the retries pass the whole test is considered a pass)
- Retries are defined as follows, where the test commands are then included in the `commands:` block
- Prefer a `maxRetries: 3` when tests will be run in CI / Maestro Cloud. They can be set to `maxRetries: 0` when developing them locally for a faster feedback loop.

```
- retry:
    maxRetries: 3
    commands:
```

### Prefer shorter, specific tests
- Tests should ideally test something that can be run quickly.
- Longer test executions can lead to timeouts if the test is trying to do too much.
- The more a test is doing, the harder it can be debug if it fails.

## Remote Config Patches

Maestro tests might require specific remote config feature flag states to test different code branches. Use the config patch mechanism to override flags at build time. See [`privacy-config/privacy-config-internal/README.md`](../../privacy-config/privacy-config-internal/README.md) for full details on how patches work, how to write them, and how to verify they were applied.

Key points for Maestro tests:
- Patches only work in `internal` builds — always use `installInternalRelease` when running tests that need patches
- Store patch files alongside the tests that need them, under a `remote_config_patches/` subdirectory:
  ```
  .maestro/
    my-feature/
      remote_config_patches/
        disable_something.json
      my_test.yaml
  ```
- Apply patches at build time using `-Pconfig_patches`:
  ```bash
  ./gradlew installInternalRelease \
    -Pconfig_patches=.maestro/my-feature/remote_config_patches/disable_something.json

  maestro test .maestro/my-feature/my_test.yaml
  ```
- In CI, pass the flag via `gradle_flags` on `checkout-and-assemble` with `flavours: 'internal'` — see the README for the full workflow snippet

## Source Patches

Remote config patches and the test seeder both apply at runtime, so neither can set a flag that is
read during app launch — config has not landed yet, and any `AppScope` class can read a toggle as
soon as it is constructed. For those flags, change the default at compile time instead: keep a Git
patch in the repo and apply it when CI prepares the build.

- Store patches under a `source_patches/` subdirectory next to the tests that need them:
  ```
  .maestro/
    onboarding/
      source_patches/
        config_driven_dialogs_enabled.patch
      onboarding.yaml
  ```
- Generate with minimal context so the hunk is pinned by the declaration it targets rather than by
  its neighbours: `git diff -U1 -- <file>`. Drop the `index` line — it only enables `--3way`, and a
  patch that needs a merge to apply should fail instead.
- Start the file with a plain-text header explaining what it flips, which workflow job applies it,
  and what to do when it stops applying. `git apply` ignores everything before the first
  `diff --git` line.
- Apply in CI through the `source_patches` input on `checkout-and-assemble` (newline-separated for
  more than one). A patch that does not apply fails the build; it is never skipped.
- Locally, `git apply <patch>` before building, and `git apply -R <patch>` after.

A source patch is written against the current default, so it always builds the arm production does
not ship. When the default flips, the patch stops applying and CI fails — update it to flip the
other way, or delete it together with the job that uses it. The `E2E Source Patches` job in
`ci.yml` runs that check on every PR so the breakage surfaces before the nightly.

Patching source is the heaviest option: it needs a dedicated build, and the whole suite runs against
one arm. Reach for it only when the flag is genuinely read too early for a config patch.
