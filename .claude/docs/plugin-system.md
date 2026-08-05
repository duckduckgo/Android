# Plugin System

Two kinds of plugin points exist. Pick based on whether you need remote feature flag control.

## `@ContributesPluginPoint` — basic

`PluginPoint<T>` — Dagger multibinding under the hood. Returns all registered plugins, no runtime filtering.

```kotlin
// Declare (in -api or -impl module)
@ContributesPluginPoint(AppScope::class)
interface MyPlugin { fun doThing() }

// Contribute
@ContributesMultibinding(AppScope::class)
class MyPluginImpl @Inject constructor() : MyPlugin

// Contribute with explicit ordering (lower value = higher priority)
@ContributesMultibinding(AppScope::class)
@PriorityKey(100)
class MyPluginImpl @Inject constructor() : MyPlugin

// Consume
class Foo @Inject constructor(private val plugins: PluginPoint<MyPlugin>)
// plugins.getPlugins() → all plugins, in priority order if @PriorityKey is used
```

## `@ContributesActivePluginPoint` — with remote feature flags + codegen

`ActivePluginPoint<T>` — wraps a regular plugin point with two levels of feature-flag gating. The annotation processor generates all the boilerplate: a remote feature for the plugin point itself, a remote feature per plugin, a `MultiProcessStore`, and a wrapper that applies both guards at runtime.

**Plugin point must be declared on a private interface** (the codegen is the only consumer):
```kotlin
// The plugin interface must extend ActivePlugin
interface MyPlugin : ActivePlugin { fun doThing() }

// Declared with a private trigger interface (in -impl)
@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    featureName = "pluginPointMyPlugin",  // required, must start with "pluginPoint"
)
private interface MyPluginPointTrigger
```

**Contribute a plugin:**
```kotlin
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
    featureName = "pluginMyPluginImpl",            // required, must start with "plugin" (not "pluginPoint")
    parentFeatureName = "pluginPointMyPlugin",     // required, must match an existing plugin point's featureName
)
class MyPluginImpl @Inject constructor() : MyPlugin {
    // isActive() is generated — backed by its own remote feature flag
}
```

**Consume:**
```kotlin
class Foo @Inject constructor(private val plugins: ActivePluginPoint<MyPlugin>)
// plugins.getPlugins() → only plugins whose feature flag is enabled AND isActive() == true
```

**How the gating works at runtime:**
1. If the plugin point's own `self()` toggle is OFF → `emptyList()` immediately
2. Otherwise, filter each plugin by its individual `pluginXxx()` toggle (via `isActive()`)

**Naming conventions** (enforced at compile time):

| Parameter | Prefix | Example |
|---|---|---|
| `@ContributesActivePluginPoint.featureName` | `pluginPoint` | `"pluginPointMyPlugin"` |
| `@ContributesActivePlugin.featureName` | `plugin` (not `pluginPoint`) | `"pluginMyPluginImpl"` |
| `@ContributesActivePlugin.parentFeatureName` | `pluginPoint` | `"pluginPointMyPlugin"` |

- `featureName` and `parentFeatureName` are **required** — blank or missing values fail the build.
- `parentFeatureName` must match an existing `@ContributesActivePluginPoint`'s `featureName`. This is validated at compile time across modules (including sibling modules that don't depend on each other) via a sentinel/deferred-marker mechanism. A typo in `parentFeatureName` will fail the build.

All flags default to `TRUE` so newly contributed plugins are on by default and can be killed remotely.
