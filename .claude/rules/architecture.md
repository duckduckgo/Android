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

### Scope hierarchy

The scope hierarchy determines what dependencies each scope can access, where its factory lookup happens, and what `HasDaggerInjector` handles injection. It is encoded implicitly in the Anvil-generated `_SubComponent` files — this diagram is the source of truth:

```
DuckDuckGoApplication [HasDaggerInjector]
└── AppComponent [AppScope]
    │   Factory map contains: ActivityComponent.Factory, ReceiverSubComponent factories,
    │                         ServiceSubComponent factories, VpnScope factories
    │
    ├── ActivityComponent [ActivityScope]  ← subcomponent of AppComponent
    │   │   Factory map contains: FragmentSubComponent factories, ViewSubComponent factories
    │   │   Provided bindings: @ActivityContext Context, AppCompatActivity
    │   │
    │   ├── EachFragment_SubComponent [FragmentScope]  ← subcomponent of ActivityComponent
    │   └── EachView_SubComponent [ViewScope]          ← subcomponent of ActivityComponent
    │
    ├── EachReceiver_SubComponent [ReceiverScope]  ← subcomponent of AppComponent
    └── EachService_SubComponent [ServiceScope]    ← subcomponent of AppComponent
```

**What this means in practice:**

- `FragmentScope` and `ViewScope` subcomponents parent to `ActivityComponent`. Their factory lookup goes through `DaggerActivity.injectorFactoryMap`. A Fragment or View can access `ActivityScope` bindings (e.g. `@ActivityContext Context`).
- `ReceiverScope` and `ServiceScope` subcomponents parent directly to `AppComponent`. Their factory lookup goes through `DuckDuckGoApplication.injectorFactoryMap`. They can only access `AppScope` bindings — there is no `@ActivityContext` available.
- `VpnScope` types also parent to `AppComponent` and are only for the VPN secondary process.
- The parent scope follows from the scope passed to `@InjectWith`; Anvil generates the `ParentComponent` and its `@ContributesTo`. You never set it manually.

**Debugging "could not find dagger component" crashes:**

- Crash in a Fragment/View injection → check `DaggerActivity.injectorFactoryMap` — the class is missing `@InjectWith(FragmentScope::class)` or `@InjectWith(ViewScope::class)`.
- Crash in a Receiver/Service injection → check `DuckDuckGoApplication.injectorFactoryMap` — the class is missing `@InjectWith(ReceiverScope::class)` or `@InjectWith(ServiceScope::class)`.

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

Use `GlobalActivityStarter` (from `navigation-api`) to navigate between activities. Never construct or start an Intent directly — the starter keeps navigation decoupled across modules.

### Registering a screen

Define an `ActivityParams` type and annotate the activity. Anvil codegen generates the mapper automatically.

```kotlin
// In the feature's -api module
data class MyScreenParams(val id: String) : GlobalActivityStarter.ActivityParams

// In the feature's -impl module
@ContributeToActivityStarter(MyScreenParams::class)
class MyActivity : DuckDuckGoActivity() {
    private val params by lazy { intent.getActivityParams(MyScreenParams::class.java) }
}
```

Use `screenName` to opt into deeplink support:
```kotlin
@ContributeToActivityStarter(MyScreenParams::class, screenName = "myScreen")
```

### Starting a screen — choose the right overload

For `ActivityParams`:

| Situation | Use |
|---|---|
| Fire and forget | `globalActivityStarter.start(context, params)` |
| Need a result back | `globalActivityStarter.startForResult(context, params, launcher)` |
| Need the raw `Intent` (e.g. `PendingIntent` for a notification) | `globalActivityStarter.startIntent(context, params)` |

Each takes an optional trailing `options: Bundle?`, and each has a `DeeplinkActivityParams`
counterpart for deeplink entry points.

**Do not** use `startIntent()` + `launcher.launch(intent)` — use `startForResult()` instead. The `startIntent()` path returns a nullable `Intent` and calling `launch(null)` will crash.

`FLAG_ACTIVITY_NEW_TASK` is added automatically when `context` is not an `Activity` (Services, broadcast receivers, JS message handlers, etc.) — do not add it manually.

---

## URL vs. Search Classification

Use `QueryUrlPredictor` (from `browser-api`) to decide whether a string is a navigable URL or a search query. Do **not** use `UriString.isWebUrl()` for this — it uses a regex that is too permissive (e.g. `bbc.comcomcomcom` passes).

```kotlin
private fun isNavigate(query: String): Boolean =
    if (queryUrlPredictor.isReady()) {
        queryUrlPredictor.classify(query) is Decision.Navigate
    } else {
        UriString.isWebUrl(query)  // fallback while native lib initialises
    }
```

- `Decision` is a sealed interface with `Navigate(url)` and `Search(query)` — both are data classes.
- `isReady()` is false briefly at startup while the native library loads; always guard with a `UriString.isWebUrl` fallback.
- `QueryUrlPredictor` lives in `browser-api` and is injectable at `AppScope`. `Decision` is exported transitively via `browser-api`'s `api` dep on `url-predictor-android`.

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
