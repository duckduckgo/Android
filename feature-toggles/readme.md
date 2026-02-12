# Feature Toggles Guide

Feature toggles are Kotlin interfaces annotated with `@ContributesRemoteFeature`. Code generation wires each method to the remote config. See [`FeatureToggles.kt`](feature-toggles-api/src/main/java/com/duckduckgo/feature/toggles/api/FeatureToggles.kt) for the `Toggle` interface and annotations.

For the cross-platform overview of the remote config system, see the [Feature Flagging Guide](https://github.com/duckduckgo/privacy-configuration/blob/main/docs/feature-flagging-guide.md).

## Who can help?
- Aitor Viana

## Choosing a Default Value

Each toggle method has a `@Toggle.DefaultValue` annotation (`FALSE`, `TRUE`, or `INTERNAL`):

- **`FALSE`** -- for new or experimental features. Off until remote config enables it.
- **`TRUE`** -- for stable, shipped features. On by default; can be killed remotely.
- **`INTERNAL`** -- on only for internal builds.

**Changing a default on a shipped feature**: if flipping from `FALSE` to `TRUE`, set `minSupportedVersion` in the remote config to the version that includes the change. Otherwise older versions without the finished implementation will activate the feature when they can't reach the config.

## Pitfalls

- `featureName` in `@ContributesRemoteFeature` must exactly match the feature key in the remote config JSON.
- `self()` represents the parent feature; other methods are sub-features. The method name becomes the config key.
- `@Toggle.InternalAlwaysEnabled` overrides everything for internal builds -- don't use it on features that need proper remote config testing.

## Local Config Patching

Internal builds support locally patching the remote config via [JSON Patch](https://jsonpatch.com/) files. See [`privacy-config/privacy-config-internal/README.md`](../privacy-config/privacy-config-internal/README.md).

## Flag Cleanup

Remove stale flags with the Piranha CLI. See [`scripts/piranha/README.md`](../scripts/piranha/README.md).

## Related Documentation

- [Feature Flagging Guide (cross-platform)](https://github.com/duckduckgo/privacy-configuration/blob/main/docs/feature-flagging-guide.md)
- [Asana: Feature Toggles Framework](https://app.asana.com/0/1202561462274611/1203928902316231/f)
