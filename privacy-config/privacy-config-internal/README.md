# Internal Privacy Config module
This module is only available in `internal` build variants of the app. It contains the implementation of some utility functions and dev tools to test, modify, and debug the remote privacy config integration.

## Local remote config patches
This module provides options to locally patch the remote config. This is useful for testing purposes, allowing to simulate different configurations without needing to deploy changes to the remote server.

For example, you can use this to ensure a build of your app always has a specific feature flag state, while otherwise using the production remote configuration.

> **Note:** Patches only work in `internal` build variants. Use `installInternalDebug` or `installInternalRelease` — patches are silently ignored in `play` and `fdroid` builds as we do not intend to package the config patching code into production builds as of now.

### How patches work

Patching uses the [JSON Patch format](https://jsonpatch.com/). Patches are applied at runtime after the remote config is fetched, overriding the actual values from the server.

### Writing a patch file

Example of a remote config looks like this:

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
        "someSubFeature": {
          "state": "enabled"
        }
      }
    }
  }
}
```

**Simple patch** — just override the state:

```json
[
  {
    "op": "replace",
    "path": "/features/myFeature/state",
    "value": "disabled"
  }
]
```

After applying, the `myFeature` entry becomes:

```json
"myFeature": {
  "state": "disabled",
  "hash": "49f683b023eea75d907b1c4f3eb3be09",
  ...
}
```

This is sufficient for clean installs.

To also patch a sub-feature (nested under `features`), extend the path:

```json
[
  {
    "op": "replace",
    "path": "/features/myFeature/features/someSubFeature/state",
    "value": "disabled"
  }
]
```

If you work across app runs where config is already cached, also remove the feature hash and bump the config version to force the app to re-process the config:

```json
[
  {
    "op": "replace",
    "path": "/features/myFeature/state",
    "value": "enabled"
  },
  {
    "op": "remove",
    "path": "/features/myFeature/hash"
  },
  {
    "op": "replace",
    "path": "/version",
    "value": "999999999999999"
  }
]
```

Use a version number higher than production (e.g. `999999999999999`) so the app treats it as a newer config and applies it. Without this, a cached config from a previous run may not be overridden.

### Verifying a patch was applied

**Build output** — during the Gradle build, the `copyConfigPatches` task prints which patches were detected and copied into the APK assets:

```
Using config patches from command line parameter: .maestro/my-feature/remote_config_patches/disable_something.json
Copied config patch to build assets: .../disable_something.json -> .../build/generated/assets/configPatches/disable_something.json
```

If a patch file path is wrong you'll see a warning instead:

```
Warning: Config patch file not found: /path/to/disable_something.json
```

**Logcat** — when the app fetches the remote config, the patch interceptor logs each step under the `DevPrivacyConfigPatchApiInterceptor` tag:

```
Applying 2 config patches: [disable_something.json, enable_other.json]
Successfully applied patch: disable_something.json
Successfully applied patch: enable_other.json
```

### Usage

There are two ways to apply patches, depending on whether you want to commit them (e.g. for Maestro UI tests) or keep them local (e.g. for personal development).

Both methods take a comma-separated list of paths to JSON patch files. The app applies all patches in the order specified.

> **Important:** Each patch file name must be unique, regardless of which directory it lives in.

#### Option 1: `local.properties` — for local development (not committed)

Create `privacy-config/privacy-config-internal/local.properties` (gitignored) with a `config_patches` property:

```
config_patches=privacy-config/privacy-config-internal/local-config-patches/my_patch.json
```

The `local-config-patches/` directory inside `privacy-config-internal` is also gitignored, so you can store your personal patch files there freely.

Patches defined this way are picked up automatically on every build, including when building via Android Studio.

#### Option 2: `-Pconfig_patches` flag — for local or committed patches

Pass patches as a Gradle property when building. This is the recommended approach for Maestro tests, where patches need to be committed to the repository and applied consistently in CI.

```bash
./gradlew installInternalRelease \
  -Pconfig_patches=.maestro/my-feature/remote_config_patches/disable_something.json
```

Store the patch files alongside the Maestro tests that need them, for example:

```
.maestro/
  my-feature/
    remote_config_patches/
      disable_something.json   ← committed patch file
    my_test.yaml               ← Maestro test that requires the patch
```

Then build and run the test:

```bash
./gradlew installInternalRelease \
  -Pconfig_patches=.maestro/my-feature/remote_config_patches/disable_something.json

maestro test .maestro/my-feature/my_test.yaml
```

Multiple patches can be combined with commas:

```bash
./gradlew installInternalRelease \
  -Pconfig_patches=.maestro/feature-a/remote_config_patches/patch_a.json,.maestro/feature-b/remote_config_patches/patch_b.json
```

The same flag works in GitHub Actions workflows via the `gradle_flags` input on `checkout-and-assemble`. Use `flavours: 'internal'` and reference `internal_apk_path` for the Maestro step:

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
