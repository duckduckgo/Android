# Android Architecture

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

- `my-feature-impl` depends on `my-feature-api`
- `my-feature-impl` depends on other features' `-api` modules only — never their `-impl`
- `settings.gradle` auto-discovers modules 2 levels deep — no manual `include` needed
- New `-impl` modules must be added to `app/build.gradle` to enter the Dagger graph
- UI resources (layouts, drawables, strings) live inside the `-impl` module, not a separate UI module
- String resource files are named by feature: `strings-my-feature.xml` (not `strings.xml`)

### Compile-time dependency rules

Enforced in the root `build.gradle`. Violations fail the build.

| Rule | Detail |
|---|---|
| API modules cannot use Anvil | No `com.squareup.anvil` plugin in `-api` modules |
| API modules cannot depend on Dagger | Except `:feature-toggles-api` and `:settings-api` |
| API modules cannot depend on other APIs | Except `:feature-toggles-api`, `:navigation-api`, `:js-messaging-api` |
| API modules cannot depend on `:di` | DI wiring belongs in `-impl` |
| Only `:app` depends on `-impl` modules | Features communicate through `-api` only |
| `-internal` modules use `internalImplementation` | Excluded from non-internal builds (Play, F-Droid) |
| No KAPT anywhere (except `:app`) | Use KSP for annotation processing |
| No `strings.xml` outside `:app` | Use `strings-<feature>.xml` instead |
| Android tests restricted | Only in: `app`, `sync-lib`, `httpsupgrade-impl`, `pir-impl`, `feature-toggles-impl` |
| No module can depend on `:app` | App is the composition root |

### Conventions enforced by lint

The `lint-rules` module fails the build on these (UI/design-system rules are covered separately):

- Hardcoded coroutine dispatchers — inject them instead
- `@Singleton` — use `@SingleInstanceIn`
- Extending `Fragment` directly — use `DuckDuckGoFragment`
- `lifecycleScope` inside a Fragment — use `viewLifecycleOwner.lifecycleScope`
- Registering lifecycle observers directly — drive state from a ViewModel
- `retrofit.create()` — get the service from DI
- `NonCancellable` when launching a coroutine
- `postValue()` on a `SingleLiveEvent`
- Underscores in a `RemoteFeature` name
- Raw `WebViewCompat` APIs — use `WebViewCompatWrapper`; post messages via `PostMessageWrapperPlugin`
- `RobolectricTestRunner` in `@RunWith`

---

## Dependency Injection (Anvil / Dagger)

### Scopes

| Scope | Use for |
|---|---|
| `AppScope` | Singletons that live for the app lifetime |
| `ActivityScope` | Things scoped to a single Activity (gets activity context) |
| `FragmentScope` | ViewModels and things scoped to a Fragment |
| `ViewScope` | Custom views that need injected dependencies |
| `ReceiverScope` | `BroadcastReceiver` implementations |
| `ServiceScope` | `Service` implementations |
| `VpnScope` | VPN-process-specific services and receivers |

Use `@SingleInstanceIn(AppScope::class)` — **not** `@Singleton` (javax). `@Singleton` conflicts with AppComponent's scope.

### Common annotations

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

### What each scope can reach

- A Fragment or View (`FragmentScope`, `ViewScope`) parents to `ActivityComponent`, so it can access
  `ActivityScope` bindings such as `@ActivityContext Context`.
- A Receiver or Service (`ReceiverScope`, `ServiceScope`) parents directly to `AppComponent`, so it can
  only access `AppScope` bindings — there is no `@ActivityContext` available.
- `VpnScope` also parents to `AppComponent` and is only for the VPN secondary process.

For the component diagram and for diagnosing "could not find dagger component" crashes, read
`.claude/docs/dagger-scopes.md`.

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

## UI Patterns

### Activity and Fragment base classes

Activities extend `DuckDuckGoActivity`, fragments extend `DuckDuckGoFragment` (lint enforces the
latter). These are not optional conveniences: they are the `DaggerActivity`/`DaggerFragment`
subclasses carrying
`@HasMemberInjections`, so a screen that extends `AppCompatActivity` or `Fragment` directly gets no
member injection and no `viewModelFactory`. `DuckDuckGoActivity` additionally applies the stored
theme, listens for theme changes, and handles edge-to-edge setup.

Override `applyFireTheme` to `true` only on activities whose look should follow the fire-mode theme;
those must inject `BrowserMode` to compute it.

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

In a Fragment, collect on `viewLifecycleOwner.lifecycleScope`, never the Fragment's own
`lifecycleScope` — the latter outlives the view and leaks collectors across view recreation.

---

## Navigation

Navigate between activities with `GlobalActivityStarter` (from `navigation-api`). Never construct or
start an Intent directly — the starter keeps navigation decoupled across modules.

**Do not** use `startIntent()` + `launcher.launch(intent)` — use `startForResult()` instead.
`startIntent()` returns a nullable `Intent` and `launch(null)` crashes.

Registering a screen, the overloads and deeplink support: `.claude/docs/navigation.md`.

Deciding whether typed input is a URL or a search query: `.claude/docs/url-classification.md`
(`UriString.isWebUrl()` is not the answer).

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
- `RobolectricTestRunner` is banned in `@RunWith` (lint)

UI tests are Maestro flows under `.maestro/`, grouped by feature area; the conventions for writing and
running them are in `.claude/rules/maestro-ui-tests.md`.
