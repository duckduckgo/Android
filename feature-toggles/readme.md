# Feature Toggles Guide

This guide covers how the remote feature toggling framework works and how toggles connect to the remote privacy configuration.

For the cross-platform overview of the remote config system, see the [Feature Flagging Guide](https://github.com/duckduckgo/privacy-configuration/blob/main/docs/feature-flagging-guide.md) in the privacy-configuration repo.

## Who can help you better understand this feature?
- Aitor Viana

## Architecture

Feature toggles are defined as Kotlin interfaces annotated with `@ContributesRemoteFeature`. At build time, code generation creates the implementation that wires each toggle method to the remote config feature/sub-feature.

At runtime, `FeatureToggles` resolves the toggle value by evaluating: remote state > rollout threshold > `minSupportedVersion` > target matching > default value.

## Defining a Feature Toggle

Each remotely-controlled feature is an interface with toggle methods:

```kotlin
@ContributesRemoteFeature(
    scope = AppScope::class,
    featureName = "aiChat",
)
interface DuckChatFeature {
    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun self(): Toggle

    @Toggle.DefaultValue(DefaultFeatureValue.FALSE)
    fun someSubFeature(): Toggle
}
```

- `featureName` must match the feature key in the remote config JSON.
- `self()` represents the parent feature state.
- Other methods represent sub-features. The method name becomes the sub-feature key (e.g., `someSubFeature` maps to the config key `someSubFeature`).

## Annotations

| Annotation | Purpose |
|---|---|
| `@Toggle.DefaultValue(FALSE)` | Fallback when remote config has no state for this toggle. |
| `@Toggle.DefaultValue(TRUE)` | Failsafe/kill-switch: on by default, can be disabled remotely. |
| `@Toggle.DefaultValue(INTERNAL)` | On only for internal builds. |
| `@Toggle.InternalAlwaysEnabled` | Always enabled on internal builds regardless of remote state. |
| `@Toggle.Experiment` | Marks the toggle as an experiment with cohort assignment. |

## Choosing a Default Value

- **`FALSE`** -- for new or experimental features. The feature stays off until explicitly enabled via remote config.
- **`TRUE`** -- for stable/shipped features where you want a remote kill-switch. The feature is on by default; if remote config is unreachable, users keep the expected behaviour.
- **`INTERNAL`** -- on only for internal builds. Useful for features under active development.

## Checking a Toggle at Runtime

```kotlin
// Simple check
if (duckChatFeature.self().isEnabled()) {
    // feature is enabled
}

// Reactive observation
duckChatFeature.someSubFeature().enabled()
    .distinctUntilChanged()
    .collect { enabled ->
        // react to state changes
    }

// Reading feature settings
val settings = duckChatFeature.self().getSettings()
```

## Optional Configuration

`@ContributesRemoteFeature` supports additional parameters:

| Parameter | Purpose |
|---|---|
| `toggleStore` | Custom `Toggle.Store` for multi-process or custom persistence. |
| `settingsStore` | Custom store for feature settings. |
| `boundType` | Bind the generated implementation to a different interface type. |

## Local Config Patching (Internal Builds)

Internal builds support locally patching the remote config using [JSON Patch](https://jsonpatch.com/) files. This is useful for testing different feature states without deploying config changes.

See `privacy-config/privacy-config-internal/README.md` for details.

## Flag Cleanup

Stale feature flags should be removed once fully launched. The `scripts/piranha/` directory provides a CLI tool for cleaning up dead flags from Kotlin code.

```sh
python scripts/piranha/clean.py -r . -c scripts/piranha/configurations/ -f flagName:true
```

See `scripts/piranha/README.md` for full usage.

## File Locations

| What | Path |
|---|---|
| Toggle API | `feature-toggles/feature-toggles-api/` |
| Toggle implementation | `feature-toggles/feature-toggles-impl/` |
| Internal dev tools | `feature-toggles/feature-toggles-internal/` |
| Feature interfaces | Per-module (e.g., `duckchat/duckchat-impl/.../DuckChatFeature.kt`) |
| Local config patching | `privacy-config/privacy-config-internal/` |
| Flag cleanup tool | `scripts/piranha/` |

## Related Documentation

- [Feature Flagging Guide (cross-platform)](https://github.com/duckduckgo/privacy-configuration/blob/main/docs/feature-flagging-guide.md)
- [Asana: Feature Toggles Framework](https://app.asana.com/0/1202561462274611/1203928902316231/f)
- [Local Config Patching](privacy-config/privacy-config-internal/README.md)
- [Flag Cleanup Tool](scripts/piranha/README.md)
