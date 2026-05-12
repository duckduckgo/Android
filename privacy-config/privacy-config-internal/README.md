# Internal Privacy Config module

Internal-only module with dev tools for testing the remote privacy config integration, including the ability to locally patch feature flag values without deploying changes to the server.

> **Note:** Patches only work in `internal` builds (`installInternalDebug` / `installInternalRelease`). They are silently ignored in `play` and `fdroid` builds.

## Local remote config patches

Patches are JSON files in [JSON Patch format](https://jsonpatch.com/) applied at runtime after the remote config is fetched, overriding values from the server. Multiple patches are applied in the order listed — if two patches modify the same path, the last one wins; a failing patch is skipped without aborting the rest.

> **Important:** Each patch file name must be unique, regardless of which directory it lives in.

### Quick start

1. Create a patch file (see [Writing a patch file](#writing-a-patch-file)):

```json
[
  { "op": "replace", "path": "/features/myFeature/state", "value": "disabled" }
]
```

2. Add the path to `privacy-config/privacy-config-internal/local.properties` (gitignored):

```
config_patches=privacy-config/privacy-config-internal/local-config-patches/my_patch.json
```

3. Build and install — the patch is applied automatically on every build.

The `local-config-patches/` directory is also gitignored, so you can store your personal patch files there freely.

### Writing a patch file

The remote config structure looks like this:

```json
{
  "version": 1773161624296,
  "features": {
    "myFeature": {
      "state": "enabled",
      "hash": "49f683b023eea75d907b1c4f3eb3be09",
      "exceptions": [],
      "settings": { ... },
      "features": {
        "someSubFeature": { "state": "enabled" }
      }
    },
    ...
  },
  ...
}
```

**Override a feature state:**

```json
[{ "op": "replace", "path": "/features/myFeature/state", "value": "disabled" }]
```

**Override a sub-feature:**

```json
[{ "op": "replace", "path": "/features/myFeature/features/someSubFeature/state", "value": "disabled" }]
```

**Add a sub-feature that doesn't exist yet** (`add` instead of `replace`):

```json
[{ "op": "add", "path": "/features/myFeature/features/newSubFeature", "value": { "state": "enabled" } }]
```

For more complex operations, refer to [JSON Patch format](https://jsonpatch.com/).

#### Working with cached config

The patches above work on a clean install. If the app has already cached the config from a previous run, also remove the feature hash and bump the version number to force re-processing:

```json
[
  { "op": "replace", "path": "/features/myFeature/state", "value": "enabled" },
  { "op": "remove",  "path": "/features/myFeature/hash" },
  { "op": "replace", "path": "/version", "value": "90000000000001" }
]
```

Use a version number higher than production so the app treats it as newer. Increment it each time you modify the patch without clearing the app cache (e.g. `90000000000001` → `90000000000002`).

> **Note:** Remote config loads asynchronously. Even after applying a patch, you may need to restart the app a second time — depending on how your feature reads the flag and whether it reacts to config changes at runtime.

### Verifying a patch was applied

**Build output** — the `copyConfigPatches` Gradle task prints which patches were detected and copied into the APK assets:

```
Using config patches from command line parameter: .maestro/my-feature/remote_config_patches/disable_something.json
Copied config patch to build assets: .../disable_something.json -> .../build/generated/assets/configPatches/disable_something.json
```

If a patch file path is wrong:

```
Warning: Config patch file not found: /path/to/disable_something.json
```

**Logcat** — search for the `DevPrivacyConfigPatchApiInterceptor` tag to monitor the behavior in the runtime:

```
Applying 2 config patches: [disable_something.json, enable_other.json]
Successfully applied patch: disable_something.json
Successfully applied patch: enable_other.json
Failed to apply patch disable_something.json: Missing field "newSubFeature"
```

### Using the `-Pconfig_patches` build flag

An alternative to `local.properties` is passing patches directly as a Gradle build flag. This works for local builds too, and is the right choice when patch files need to be committed — for automated tests.

```bash
./gradlew installInternalRelease \
  -Pconfig_patches=path/to/my_patch.json

# Multiple patches — comma-separated
./gradlew installInternalRelease \
  -Pconfig_patches=path/to/patch_a.json,path/to/patch_b.json
```

#### Automated tests (Maestro / Espresso)

Store patch files alongside the tests that need them and pass them via the flag when building:

```
.maestro/
  my-feature/
    remote_config_patches/
      disable_something.json   ← committed patch file
    my_test.yaml               ← Maestro test that requires the patch
```

Build and run:

```bash
./gradlew installInternalRelease \
  -Pconfig_patches=.maestro/my-feature/remote_config_patches/disable_something.json

maestro test .maestro/my-feature/my_test.yaml
```

In GitHub Actions, pass the flag via `gradle_flags` on `checkout-and-assemble`:

```yaml
- name: Assemble APKs
  id: assemble
  uses: ./.github/actions/checkout-and-assemble
  with:
    flavours: 'internal'
    release_properties: ${{ secrets.FAKE_RELEASE_PROPERTIES }}
    release_key: ${{ secrets.FAKE_RELEASE_KEY }}
    gradle_encryption_key: ${{ secrets.GRADLE_ENCRYPTION_KEY }}
    develocity_access_key: ${{ secrets.DEVELOCITY_ACCESS_KEY }}
    gradle_flags: '-Pconfig_patches=.maestro/my-feature/remote_config_patches/disable_something.json'

- name: Run Maestro Tests
  uses: ./.github/actions/maestro-cloud-asana-reporter
  with:
    maestro_app_file: ${{ steps.assemble.outputs.internal_apk_path }}
    # ... other options
```

### PR review workflow

If your PR introduces or modifies a feature flag, consider including patch file contents in the PR description so reviewers can verify each state without having to manually enable/disable flags.

Ask reviewers to:

1. Create `privacy-config/privacy-config-internal/local-config-patches/review_patch.json`
2. Add to `privacy-config/privacy-config-internal/local.properties`:
   ```
   config_patches=privacy-config/privacy-config-internal/local-config-patches/review_patch.json
   ```
3. Execute verification steps.
4. Once done, delete or clear `review_patch.json`/`local.properties`, and reinstall to avoid it affecting unrelated work.

For each verification step, provide the content to paste into `review_patch.json` and reinstall the app. Note that the version number must be incremented with each step so the app re-processes the config.

Example steps in a PR description:

> **Step 1 — verify feature enabled**
>
> Paste into `review_patch.json` and reinstall:
> ```json
> [
>   { "op": "replace", "path": "/features/myFeature/state", "value": "enabled" },
>   { "op": "remove",  "path": "/features/myFeature/hash" },
>   { "op": "replace", "path": "/version", "value": "90000000000001" }
> ]
> ```

> **Step 2 — verify feature disabled**
>
> Paste into `review_patch.json` and reinstall:
> ```json
> [
>   { "op": "replace", "path": "/features/myFeature/state", "value": "disabled" },
>   { "op": "remove",  "path": "/features/myFeature/hash" },
>   { "op": "replace", "path": "/version", "value": "90000000000002" }
> ]
> ```

---

## Patch examples

### Disable a feature (clean install)

Patch:
```json
[{ "op": "replace", "path": "/features/myFeature/state", "value": "disabled" }]
```

Before:
```json
{
  "version": 1773161624296,
  "features": {
    "myFeature": { "state": "enabled", "hash": "abc123", "exceptions": [], "settings": {} }
  }
}
```

After:
```json
{
  "version": 1773161624296,
  "features": {
    "myFeature": { "state": "disabled", "hash": "abc123", "exceptions": [], "settings": {} }
  }
}
```

---

### Disable a feature (cached config)

Patch:
```json
[
  { "op": "replace", "path": "/features/myFeature/state", "value": "disabled" },
  { "op": "remove",  "path": "/features/myFeature/hash" },
  { "op": "replace", "path": "/version", "value": "90000000000001" }
]
```

Before:
```json
{
  "version": 1773161624296,
  "features": {
    "myFeature": { "state": "enabled", "hash": "abc123", "exceptions": [], "settings": {} }
  }
}
```

After:
```json
{
  "version": 90000000000001,
  "features": {
    "myFeature": { "state": "disabled", "exceptions": [], "settings": {} }
  }
}
```

---

### Disable a sub-feature (cached config)

Patch:
```json
[
  { "op": "replace", "path": "/features/myFeature/features/someSubFeature/state", "value": "disabled" },
  { "op": "remove",  "path": "/features/myFeature/hash" },
  { "op": "replace", "path": "/version", "value": "90000000000001" }
]
```

Before:
```json
{
  "version": 1773161624296,
  "features": {
    "myFeature": {
      "state": "enabled",
      "hash": "abc123",
      "exceptions": [],
      "settings": {},
      "features": {
        "someSubFeature": { "state": "enabled" }
      }
    }
  }
}
```

After:
```json
{
  "version": 90000000000001,
  "features": {
    "myFeature": {
      "state": "enabled",
      "exceptions": [],
      "settings": {},
      "features": {
        "someSubFeature": { "state": "disabled" }
      }
    }
  }
}
```

---

### Add a sub-feature that doesn't exist in the config

Patch:
```json
[
  { "op": "add", "path": "/features/myFeature/features/newSubFeature", "value": { "state": "enabled" } },
  { "op": "remove", "path": "/features/myFeature/hash" },
  { "op": "replace", "path": "/version", "value": "90000000000001" }
]
```

Before:
```json
{
  "version": 1773161624296,
  "features": {
    "myFeature": { "state": "enabled", "hash": "abc123", "exceptions": [], "settings": {}, "features": {} }
  }
}
```

After:
```json
{
  "version": 90000000000001,
  "features": {
    "myFeature": { "state": "enabled", "exceptions": [], "settings": {}, "features": { "newSubFeature": { "state": "enabled" } } }
  }
}
```
