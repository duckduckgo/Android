# CLAUDE.md — DuckDuckGo Android Browser

DuckDuckGo Android is a privacy-focused browser built as a large multi-module Gradle project,
providing built-in search, tracker blocking, HTTPS enforcement and other privacy features.

**Versions** (SDK levels, Kotlin, Gradle, libraries) live in the build files — don't restate them here:
`min_sdk` / `target_sdk` / `compile_sdk` in `build.gradle`, `version.kotlin` in `versions.properties`,
and the Gradle version in `gradle/wrapper/gradle-wrapper.properties`.
**Build:** AGP via refreshVersions. DI is Anvil/Dagger2 today, with a migration to **Metro** in flight
(dual-build selected by the `ddg.di` Gradle property; see `build.gradle`).
**Toolchain:** Kotlin JVM target 17; building requires **JDK 21** (Metro compiler plugin).

Notable libraries: Room, Retrofit/OkHttp/Moshi, Coroutines, Jetpack Compose (selective), WorkManager,
`logcat` (Square), KSP for annotation processing. Versions are in `versions.properties` and the module
`build.gradle` files.

---

## Privacy invariants for anything leaving the device

This is a privacy browser; these hold for every outbound value — pixels, wide events, logs, crash
reports, debug output — not just telemetry you are deliberately designing.

- **No PII.** Never emails, names, account IDs, usernames or phone numbers, in a name or a value.
- **No URLs, domains or page titles.** Breakage reports are the one controlled exception, with explicit
  user consent.
- **No correlation IDs.** No session IDs, GUIDs or exact timestamps — anything that links events to one
  user session.
- **Bucket numeric values.** Exact durations, byte counts and item counts fingerprint users; send ranges.
- **Bounded enums over free-form strings.** High-cardinality unbounded strings are both a privacy risk
  and unanalysable.

Adding or changing a pixel or the pixel registry also requires privacy triage — see
`.claude/docs/pixels.md`.

---

## Read these when the situation applies

These files are not in context. Read the whole file before doing the work it covers — don't rely on
what you remember of it.

| Read | When |
|---|---|
| `.claude/docs/contributions.md` | **planning** a change that touches a `-api` surface or spans multiple modules — it needs an approved API Proposal and/or Tech Design, and that has to be raised before implementation, not at PR time. Also read it when naming a branch, writing a commit, or opening a PR |
| `.claude/docs/lateinit-hazards.md` | writing or reviewing any `lateinit var`, especially `@Inject lateinit var` in a View |
| `.claude/docs/plugin-system.md` | declaring a plugin point or contributing a plugin (`PluginPoint` / `ActivePluginPoint`) |
| `.claude/docs/pixels.md` | adding or changing pixel telemetry — including whether a pixel or a wide event is the right instrument |
| `.claude/docs/dagger-scopes.md` | an injection fails at runtime ("could not find dagger component"), or you're deciding which scope to pass to `@InjectWith` — has the component/subcomponent diagram and the `injectorFactoryMap` lookups |
| `.claude/docs/navigation.md` | adding a screen or navigating to one — `ActivityParams`, `@ContributeToActivityStarter`, deeplinks, and which `GlobalActivityStarter` overload to use |
| `.claude/docs/url-classification.md` | routing typed input to navigation vs search — use `QueryUrlPredictor`, not `UriString.isWebUrl()` |
| `.claude/docs/icons.md` | the change needs an icon the project doesn't have yet — it must be fetched from the internal Icons repository, never invented |

---

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
```

`jvm_tests` and `jvm_checks` resolve to `testPlayDebugUnitTest` in `:app` and `testDebugUnitTest` in
library modules. To run a single test class, use `--tests`:

```bash
./gradlew :my-feature-impl:testDebugUnitTest --tests "com.duckduckgo.my.feature.RealFooTest"
```

### Build Variants

| Dimension | Flavors |
|---|---|
| store | `internal`, `fdroid`, `play` |
| Build types | `debug`, `release`, `upload` |

### Proprietary Fonts

The app uses a proprietary DuckSans font from a private GitHub Packages repository. The build
conditionally swaps between the proprietary `ddg-proprietary-fonts` AAR and a local `:fonts` fallback
module (empty `<font-family/>` stubs) based on credential availability. See `build.gradle` for the
credential detection logic and `android-design-system/fonts/readme.md` for details.

---

## Code Formatting

- **Spotless** with ktlint for Kotlin; Google Java Format in AOSP style for Java
- Max line length: 150 characters
- Ratchet from `origin/develop` — only changed code is enforced

---

## Code Comments

Comments explain the **why** (intent, assumptions, non-obvious decisions), not the **what** the code
already shows.

Default to no comment: prefer self-documenting code (clear names, small functions), and add one only
where code alone can't carry the reasoning, and only if it still answers *"why was this done this
way?"* for someone reading it two years from now. `-api` module declarations may carry KDoc describing
the contract — that's documentation for consumers, not narration.

Never add:

- **narration** of trivial code
- **conversational / temporal residue**
- **process / plan references**

```kotlin
count++ // increment the counter -> narration of trivial code
val user = repo.load() // fixes the bug from the previous task -> temporal residue
val state = flow // changed from LiveData -> temporal residue
cache.clear() // step 3 of the plan -> process / plan reference
retryCount = 3 // per the Asana task -> process / plan reference
```

When reviewing a diff, flag any added comment that doesn't follow these rules.
