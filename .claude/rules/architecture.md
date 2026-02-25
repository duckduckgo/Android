# Android Architecture Rules

## Core Principle: Decoupling Over Everything

Features communicate through `-api` modules only. An `-impl` module must never depend on another `-impl` module. If two features need to interact, one exposes an interface in its `-api`, the other injects it. This is non-negotiable — it prevents circular dependencies and keeps the Dagger graph clean.

---

## Module Structure

Every feature follows the `-api` / `-impl` split:

```
my-feature/
  my-feature-api/       ← interfaces, data classes, no implementation
  my-feature-impl/      ← implementation, UI, DI bindings
```

Rules:
- `my-feature-impl` depends on `my-feature-api`
- `my-feature-impl` depends on other features' `-api` modules only — never their `-impl`
- `settings.gradle` auto-discovers modules 2 levels deep — no manual `include` needed
- New `-impl` modules must be added to `app/build.gradle` to enter the Dagger graph
- UI resources (layouts, drawables, strings) live inside the `-impl` module, not a separate UI module
- String resource files are named by feature: `strings-my-feature.xml` (not `strings.xml`)

---

## Dependency Injection (Anvil / Dagger)

### Scopes

| Scope | Use for |
|---|---|
| `AppScope` | Singletons that live for the app lifetime |
| `ActivityScope` | Things scoped to a single Activity (gets activity context) |
| `FragmentScope` | ViewModels and things scoped to a Fragment |

Use `@SingleInstanceIn(AppScope::class)` — **not** `@Singleton` (javax). `@Singleton` conflicts with AppComponent's scope.

### Common Annotations

```kotlin
// Singleton binding
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealFoo @Inject constructor(...) : Foo

// Override an existing binding (higher rank wins)
@ContributesBinding(AppScope::class, rank = 1)

// ViewModel
@ContributesViewModel(FragmentScope::class)
class FooViewModel @Inject constructor(...) : ViewModel()

// Plugin contribution (multibinding)
@ContributesMultibinding(AppScope::class)
class MyPlugin @Inject constructor(...) : SomePlugin

// Remote feature flag
@ContributesRemoteFeature(scope = AppScope::class, featureName = "myFeature")
interface MyFeature : Feature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun myToggle(): Toggle
}
```

`DefaultFeatureValue.INTERNAL` = enabled only in internal/debug builds.

### Activity Context

`@ActivityContext Context` and `AppCompatActivity` are provided at `ActivityScope` via `DaggerActivityScopedModule`. Inject them with:

```kotlin
@ContributesBinding(ActivityScope::class)
class RealFoo @Inject constructor(
    @ActivityContext private val context: Context,
) : Foo
```

Never pass `Context` as a parameter through an interface if DI can provide it at the right scope.

### App Coroutine Scope

```kotlin
@AppCoroutineScope private val appScope: CoroutineScope
```

---

## Plugin System

Two kinds of plugin points exist. Pick based on whether you need remote feature flag control.

### `@ContributesPluginPoint` — basic

`PluginPoint<T>` — Dagger multibinding under the hood. Returns all registered plugins, no runtime filtering.

```kotlin
// Declare (in -api or -impl module)
@ContributesPluginPoint(AppScope::class)
interface MyPlugin { fun doThing() }

// Contribute
@ContributesMultibinding(AppScope::class)
class MyPluginImpl @Inject constructor() : MyPlugin

// Consume
class Foo @Inject constructor(private val plugins: PluginPoint<MyPlugin>)
// plugins.getPlugins() → all plugins, always
```

### `@ContributesActivePluginPoint` — with remote feature flags + codegen

`ActivePluginPoint<T>` — wraps a regular plugin point with two levels of feature-flag gating. The annotation processor generates all the boilerplate: a remote feature for the plugin point itself, a remote feature per plugin, a `MultiProcessStore`, and a wrapper that applies both guards at runtime.

**Plugin point must be declared on a private interface** (the codegen is the only consumer):
```kotlin
// The plugin interface must extend ActivePlugin
interface MyPlugin : ActivePlugin { fun doThing() }

// Declared with a private trigger interface (in -impl)
@ContributesActivePluginPoint(
    scope = AppScope::class,
    boundType = MyPlugin::class,
)
private interface MyPluginPointTrigger
```

**Contribute a plugin:**
```kotlin
@ContributesActivePlugin(
    scope = AppScope::class,
    boundType = MyPlugin::class,
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

**Feature flag naming convention** (generated, useful to know for remote config):
- Plugin point flag: `pluginPoint${InterfaceName}` e.g. `pluginPointMyPlugin`
- Per-plugin flag: a sub-toggle on the same feature, `pluginMyPluginImpl`

All flags default to `TRUE` so newly contributed plugins are on by default and can be killed remotely.

### Notable active plugin points in the browser
- `JsInjectorPlugin` — hooks into `onPageStarted` / `onPageFinished` on the browser WebView
- `ContentScopeJsMessageHandlersPlugin` — handles JS→native messages via content scope scripts

---

## UI Patterns

### ViewModels

Commands are emitted via a `Channel<Command>`:
```kotlin
private val _commands = Channel<Command>(Channel.BUFFERED)
val commands: Flow<Command> = _commands.receiveAsFlow()
```

State is `StateFlow` derived via `combine` + `stateIn`.

### Coroutine Jobs

Prefer `ConflatedJob` over a raw `Job` variable or a `Map<Key, Job>` when you need to cancel-and-replace a running job:
```kotlin
private var dwellJob by ConflatedJob()
dwellJob = scope.launch { /* cancels previous */ }
```

---

## Logging

```kotlin
import logcat.logcat   // correct
// NOT: import com.squareup.logcat.logcat
```

---

## Testing

- JUnit4 (`@Test`, not JUnit5/Jupiter)
- Assertions: `org.junit.Assert.*`
- Mocking: `org.mockito.kotlin.mock()` + `whenever()`
- Coroutines: `CoroutineTestRule` + `runTest { }`
- Test files mirror the class: `RealFoo.kt` → `RealFooTest.kt`
- No coroutine test setup needed for pure logic classes
