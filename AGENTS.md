# AGENTS.md — DuckDuckGo Android Browser

## Available Agents

Agents in `.claude/agents/` are invoked automatically when their trigger conditions match.
Always delegate to the agent — do not apply its instructions inline.

### `ddg-api-proposal-reviewer`

**Command:** `/review-public-api <url-or-code>`

Use this when someone asks for a review of a DuckDuckGo Android public API proposal.

Triggers:
- An Asana task URL that is confirmed to be an API proposal (title contains "API Proposal", or the task is in the API Proposals board, or the description proposes changes to a `-api` module)
- Pasted Kotlin code from or intended for a `-api` module, with a design question
- A file containing a proposal, with a review request

Do **not** trigger on general Asana URLs paired with "review", impl-only changes, or general Kotlin questions.

When this agent returns output, relay it to the user verbatim and in full — do not summarize, shorten, or paraphrase.

## Detailed Rules

The following rule files contain detailed guidance for specific topics. Read them before working in those areas — do not rely on summaries here.

| File | Covers |
|---|---|
| `.cursor/rules/architecture.mdc` | Module structure, dependency injection, plugin system, ViewModels, URL classification, testing, git workflow |
| `.cursor/rules/android-design-system.mdc` | ADS components, buttons, text, inputs, switches, list items, dialogs, bottom sheets, colors, spacing, lint rules |
| `.cursor/rules/maestro-ui-tests.mdc` | Maestro test setup, organization, tags, running locally and in CI |
| `.cursor/rules/wide-events.mdc` | Wide event API, FlowStatus, CleanupPolicy, implementation patterns |
| `.cursor/rules/dependency-updates.mdc` | How to safely update Android library dependencies |
| `.cursor/rules/contributions.mdc` | Branch naming, commit messages, PR creation workflow |

---

## Project Overview

DuckDuckGo Android is a privacy-focused browser with 100+ Gradle modules. The app provides built-in search, tracker blocking, HTTPS enforcement, and other privacy features.

**SDK targets:** minSdk 26, targetSdk 35, compileSdk 35
**Language:** Kotlin (1.9.24), Java 17 (JVM toolchain)
**Build:** Gradle 7.6, AGP via refreshVersions, Anvil (Dagger2)

---
## Build System

### Module Discovery

`settings.gradle` auto-discovers modules up to 2 levels deep — any directory containing `build.gradle` is included automatically. No manual `include` is needed.

## Build & Test Commands

```bash
# Unit tests (all modules)
./gradlew jvm_tests

# Unit tests for a single module
./gradlew :my-feature-impl:testDebugUnitTest

# Code quality (spotless + lint + unit tests)
./gradlew jvm_checks

# Lint only
./gradlew lint_check

# Code formatting check / fix
./gradlew spotlessCheck
./gradlew spotlessApply

# Install app
./gradlew installInternalRelease    # internal build (more testing features)
./gradlew installPlayRelease        # play store build

# Maestro UI tests (requires app installed on device)
maestro test .maestro/autofill/1_autofill_shown_in_overflow.yaml   # single test
maestro test .maestro/autofill                                      # all in directory
maestro test .maestro --include-tags releaseTest                    # by tag
```
Note: `jvm_tests` and `jvm_checks` resolve to `testPlayDebugUnitTest` in `:app` and `testDebugUnitTest` in library modules. To run a single test class, use `--tests`:
```bash
./gradlew :my-feature-impl:testDebugUnitTest --tests "com.duckduckgo.my.feature.RealFooTest"
```

### Build Variants

| Dimension | Flavors |
|---|---|
| store | `internal`, `fdroid`, `play` |
| Build types | `debug`, `release`, `upload` |

---

## Module Architecture

Every feature follows an `-api` / `-impl` split:

```
my-feature/
  my-feature-api/       ← interfaces, data classes, no implementation
  my-feature-impl/      ← implementation, UI, DI bindings
```

Key rules enforced at build time (`build.gradle`):
- `-api` modules cannot use Anvil, depend on Dagger (except `:feature-toggles-api`, `:settings-api`), depend on other `-api` modules (except `:feature-toggles-api`, `:navigation-api`, `:js-messaging-api`), or depend on `:di`
- Only `:app` can depend on `-impl` modules — features communicate through `-api` only
- `-internal` modules must use `internalImplementation` configuration
- No KAPT anywhere except `:app` — use KSP
- No `strings.xml` outside `:app` — use `strings-<feature>.xml`
- Android tests only allowed in: `app`, `sync-lib`, `httpsupgrade-impl`, `pir-impl`, `feature-toggles-impl`
- No module can depend on `:app`

`settings.gradle` auto-discovers modules up to 2 levels deep — any directory with `build.gradle` is included. New `-impl` modules must be added to `app/build.gradle` to enter the Dagger graph.

## Dependency Injection (Anvil / Dagger)

Scopes: `AppScope` (app lifetime), `ActivityScope` (single activity), `FragmentScope` (viewmodels/fragments).

Use `@SingleInstanceIn(AppScope::class)` — never `@Singleton` (lint enforces this).

```kotlin
// Binding
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealFoo @Inject constructor(...) : Foo

// ViewModel
@ContributesViewModel(FragmentScope::class)
class FooViewModel @Inject constructor(...) : ViewModel()

// Plugin (multibinding)
@ContributesMultibinding(AppScope::class)
class MyPlugin @Inject constructor() : SomePlugin

// Remote feature flag
@ContributesRemoteFeature(scope = AppScope::class, featureName = "myFeature")
interface MyFeature : Feature {
    @Toggle.DefaultValue(DefaultFeatureValue.INTERNAL)
    fun myToggle(): Toggle
}
```

Activity context: inject `@ActivityContext Context` at `ActivityScope` via DI, don't pass Context through interfaces.

App coroutine scope: `@AppCoroutineScope private val appScope: CoroutineScope`.

## Plugin System

Two kinds of plugin points:

- **`PluginPoint<T>`** (`@ContributesPluginPoint`): basic multibinding, returns all registered plugins. Use `@PriorityKey(n)` for ordering (lower = higher priority).
- **`ActivePluginPoint<T>`** (`@ContributesActivePluginPoint`): wraps plugins with remote feature flag gating. Plugins extend `ActivePlugin`, declared via a private trigger interface. Generated flags default to TRUE.

## Compile-Time Dependency Rules

These rules are enforced in the root `build.gradle`. Violations fail the build.

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

---

## Code Formatting

- **Spotless** with ktlint (0.50.0) for Kotlin
- Google Java Format (1.22.0) in AOSP style for Java
- Max line length: 150 characters
- Ratchet from `origin/develop` (only enforces formatting on changed code)

---

## Custom Lint Rules

The `lint-rules` module enforces at compile time:
- No hardcoded coroutine dispatchers (inject via DI)
- No `@Singleton` (use `@SingleInstanceIn`)
- No direct lifecycle observers (use ViewModel)
- No manual Retrofit creation (use DI)
- No raw `Button`, `TextView`, `Switch` in XML (use ADS components)
- No `@color/` references in XML (use `?attr/daxColor*` theme attributes)
- No `AlertDialog` / raw `BottomSheetDialog` (use ADS builders)

---

## Key Dependencies

| Category | Library |
|---|---|
| DI | Dagger2 + Square Anvil |
| Database | Room (with RxJava2 support) |
| Network | Retrofit2 + OkHttp3 + Moshi |
| Async | Kotlin Coroutines |
| UI | Jetpack Compose (selective), Material Design |
| Scheduling | WorkManager |
| Logging | logcat (Square) |
| Annotation Processing | KSP |

---

## Cursor Cloud specific instructions

### Prerequisites (already installed in the VM snapshot)

- JDK 17 (`openjdk-17-jdk`) — required by Gradle's `jvmToolchain(17)`
- JDK 21 — used to run the Gradle daemon itself
- Android SDK at `/opt/android-sdk` with: platform 35, build-tools 35.0.0, NDK 21.4.7075529, CMake 3.22.1
- Environment variables persisted in `~/.bashrc`: `ANDROID_HOME`, `ANDROID_SDK_ROOT`, `JAVA_HOME`

### Environment variables

These must be set for Gradle to find the Android SDK (already in `~/.bashrc`):

```
ANDROID_HOME=/opt/android-sdk
ANDROID_SDK_ROOT=/opt/android-sdk
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

### Git submodules

Run `git submodule update --init --recursive` after cloning or pulling. The update script handles this.

### Key commands

All standard build/test commands are in the **Build & Test Commands** section above. Quick reference:

- **Format check:** `./gradlew spotlessCheck`
- **Format fix:** `./gradlew spotlessApply`
- **Single module tests:** `./gradlew :module-name:testDebugUnitTest`
- **Build debug APK:** `./gradlew assembleInternalDebug`
- **All unit tests:** `./gradlew jvm_tests`

### Gotchas

- The project requires JDK 17 as a toolchain but runs Gradle with JDK 21. Both must be installed. If only JDK 21 is present, Gradle fails with "Cannot find a Java installation matching this tasks requirements: {languageVersion=17}".
- The Gradle configuration cache is used. If environment changes cause stale cache errors, delete `.gradle/configuration-cache` and retry.
- Spotless ratchets from `origin/develop` — it only enforces formatting on changed files relative to the develop branch.
- Two modules (`httpsupgrade-impl`, `anrs-impl`) use NDK 21.4.7075529 for C++ native code via CMake. Without the NDK, building these modules (or the full app) will fail.
- No Docker, databases, or external services are needed. Room/SQLCipher databases are on-device only.
- There is no Android emulator in the Cloud VM — Maestro UI tests and `installInternalRelease` require a connected device/emulator and cannot run here. Unit tests (`jvm_tests`) run on JVM and work fully.
