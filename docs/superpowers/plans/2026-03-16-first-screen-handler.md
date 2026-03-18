# FirstScreenHandler Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `FirstScreenHandler` that opens a New Tab Page when the user returns to the app after an idle timeout, delegating to the existing `ShowOnAppLaunchOptionHandler` otherwise.

**Architecture:** `FirstScreenHandler` wraps `ShowOnAppLaunchOptionHandler` as the single entry point from `BrowserViewModel`. It implements `BrowserLifecycleObserver` to record a timestamp on `onClose()`, then checks elapsed time against a server-configured timeout on `handleFirstScreen()`. The timeout is configured via a new `showNTPAfterIdleReturn` toggle on `AndroidBrowserConfigFeature`.

**Tech Stack:** Kotlin, Anvil/Dagger DI, SharedPreferences, Remote Feature Flags (Toggle API), JUnit4

**Spec:** `docs/superpowers/specs/2026-03-16-first-screen-handler-design.md`

---

## File Structure

| File | Action | Responsibility |
|---|---|---|
| `app/src/main/java/com/duckduckgo/app/pixels/remoteconfig/AndroidBrowserConfigFeature.kt` | Modify | Add `showNTPAfterIdleReturn()` toggle |
| `app/src/main/java/com/duckduckgo/app/settings/db/SettingsDataStore.kt` | Modify | Add `lastSessionBackgroundTimestamp` property (interface + impl + key) |
| `app/src/main/java/com/duckduckgo/app/generalsettings/showonapplaunch/FirstScreenHandler.kt` | Create | Interface + `FirstScreenHandlerImpl` |
| `app/src/main/java/com/duckduckgo/app/browser/BrowserViewModel.kt` | Modify | Replace `showOnAppLaunchOptionHandler` + `showOnAppLaunchFeature` with `firstScreenHandler` |
| `app/src/test/java/com/duckduckgo/app/generalsettings/showonapplaunch/FirstScreenHandlerImplTest.kt` | Create | Tests for all branches |

---

## Chunk 1: Infrastructure

### Task 1: Add `showNTPAfterIdleReturn` toggle to AndroidBrowserConfigFeature

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/pixels/remoteconfig/AndroidBrowserConfigFeature.kt`

- [ ] **Step 1: Add the toggle**

Add to `AndroidBrowserConfigFeature` interface, after the existing `omnibarAnimation()` toggle:

```kotlin
/**
 * @return `true` when the remote config has the global "showNTPAfterIdleReturn" androidBrowserConfig
 * sub-feature flag enabled
 * If the remote feature is not present defaults to `false`
 */
@Toggle.DefaultValue(DefaultFeatureValue.FALSE)
fun showNTPAfterIdleReturn(): Toggle
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:compilePlayDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/pixels/remoteconfig/AndroidBrowserConfigFeature.kt
git commit -m "Add showNTPAfterIdleReturn toggle to AndroidBrowserConfigFeature"
```

---

### Task 2: Add `lastSessionBackgroundTimestamp` to SettingsDataStore

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/settings/db/SettingsDataStore.kt`

- [ ] **Step 1: Add property to the `SettingsDataStore` interface**

Add after `appBackgroundedTimestamp` (line 83):

```kotlin
var lastSessionBackgroundTimestamp: Long
```

- [ ] **Step 2: Add implementation to `SettingsSharedPreferences`**

Add after the `appBackgroundedTimestamp` implementation (after line 223):

```kotlin
override var lastSessionBackgroundTimestamp: Long
    get() = preferences.getLong(KEY_LAST_SESSION_BACKGROUND_TIMESTAMP, 0)
    set(value) = preferences.edit(commit = true) { putLong(KEY_LAST_SESSION_BACKGROUND_TIMESTAMP, value) }
```

- [ ] **Step 3: Add the key constant**

Add to the `companion object` (after `KEY_APP_BACKGROUNDED_TIMESTAMP` on line 345):

```kotlin
const val KEY_LAST_SESSION_BACKGROUND_TIMESTAMP = "LAST_SESSION_BACKGROUND_TIMESTAMP"
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:compilePlayDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/settings/db/SettingsDataStore.kt
git commit -m "Add lastSessionBackgroundTimestamp to SettingsDataStore"
```

---

## Chunk 2: FirstScreenHandler Implementation (TDD)

### Task 3: Write FirstScreenHandler tests

**Files:**
- Create: `app/src/test/java/com/duckduckgo/app/generalsettings/showonapplaunch/FirstScreenHandlerImplTest.kt`

- [ ] **Step 1: Write all test cases**

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FirstScreenHandlerImplTest {

    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature = mock()
    private val settingsDataStore: SettingsDataStore = mock()
    private val tabRepository: TabRepository = mock()
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler = mock()
    private val idleReturnToggle: Toggle = mock()
    private val showOnAppLaunchToggle: Toggle = mock()

    private lateinit var testee: FirstScreenHandlerImpl

    @Before
    fun setup() {
        whenever(androidBrowserConfigFeature.showNTPAfterIdleReturn()).thenReturn(idleReturnToggle)
        whenever(showOnAppLaunchFeature.self()).thenReturn(showOnAppLaunchToggle)

        testee = FirstScreenHandlerImpl(
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            showOnAppLaunchFeature = showOnAppLaunchFeature,
            settingsDataStore = settingsDataStore,
            tabRepository = tabRepository,
            showOnAppLaunchOptionHandler = showOnAppLaunchOptionHandler,
        )
    }

    @Test
    fun whenIdleReturnDisabledAndShowOnAppLaunchEnabledThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.handleFirstScreen()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
        verify(tabRepository, never()).add()
    }

    @Test
    fun whenIdleReturnDisabledAndShowOnAppLaunchDisabledThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(false)

        testee.handleFirstScreen()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
        verify(tabRepository, never()).add()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutThenAddsNewTab() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.handleFirstScreen()

        verify(tabRepository).add()
        verify(showOnAppLaunchOptionHandler, never()).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedUnderTimeoutThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(fiveMinutesAgo)

        testee.handleFirstScreen()

        verify(tabRepository, never()).add()
        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndNoTimestampThenAddsNewTab() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.handleFirstScreen()

        verify(tabRepository).add()
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsNullThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(null)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.handleFirstScreen()

        verify(tabRepository, never()).add()
        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsMalformedThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("not json")
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.handleFirstScreen()

        verify(tabRepository, never()).add()
        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndTimeoutMinutesMissingThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"otherKey": 30}""")
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.handleFirstScreen()

        verify(tabRepository, never()).add()
        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedExactlyEqualsTimeoutThenAddsNewTab() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val exactlyThirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(exactlyThirtyMinutesAgo)

        testee.handleFirstScreen()

        verify(tabRepository).add()
        verify(showOnAppLaunchOptionHandler, never()).handleAppLaunchOption()
    }

    @Test
    fun whenOnCloseThenWritesTimestamp() {
        testee.onClose()

        verify(settingsDataStore).lastSessionBackgroundTimestamp = org.mockito.kotlin.any()
    }
}
```

- [ ] **Step 2: Verify tests fail (class doesn't exist yet)**

Run: `./gradlew :app:compilePlayDebugUnitTestKotlin`
Expected: FAIL — `FirstScreenHandlerImpl` not found

---

### Task 4: Implement FirstScreenHandler

**Files:**
- Create: `app/src/main/java/com/duckduckgo/app/generalsettings/showonapplaunch/FirstScreenHandler.kt`

- [ ] **Step 1: Write the implementation**

```kotlin
/*
 * Copyright (c) 2026 DuckDuckGo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.duckduckgo.app.generalsettings.showonapplaunch

import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browser.api.BrowserLifecycleObserver
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import org.json.JSONObject

interface FirstScreenHandler {
    suspend fun handleFirstScreen()
}

@ContributesBinding(AppScope::class)
@ContributesMultibinding(
    scope = AppScope::class,
    boundType = BrowserLifecycleObserver::class,
)
@SingleInstanceIn(AppScope::class)
class FirstScreenHandlerImpl @Inject constructor(
    private val androidBrowserConfigFeature: AndroidBrowserConfigFeature,
    private val showOnAppLaunchFeature: ShowOnAppLaunchFeature,
    private val settingsDataStore: SettingsDataStore,
    private val tabRepository: TabRepository,
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
) : FirstScreenHandler, BrowserLifecycleObserver {

    override suspend fun handleFirstScreen() {
        if (androidBrowserConfigFeature.showNTPAfterIdleReturn().isEnabled()) {
            val timeoutMs = getTimeoutMs()
            if (timeoutMs != null) {
                val lastBackgrounded = settingsDataStore.lastSessionBackgroundTimestamp
                val elapsed = System.currentTimeMillis() - lastBackgrounded
                if (lastBackgrounded == 0L || elapsed >= timeoutMs) {
                    tabRepository.add()
                    return
                }
            }
        }

        if (showOnAppLaunchFeature.self().isEnabled()) {
            showOnAppLaunchOptionHandler.handleAppLaunchOption()
        }
    }

    override fun onClose() {
        settingsDataStore.lastSessionBackgroundTimestamp = System.currentTimeMillis()
    }

    private fun getTimeoutMs(): Long? {
        val settings = androidBrowserConfigFeature.showNTPAfterIdleReturn().getSettings() ?: return null
        return runCatching {
            JSONObject(settings).getLong("timeoutMinutes") * 60 * 1000
        }.getOrNull()
    }
}
```

- [ ] **Step 2: Run tests**

Run: `./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.generalsettings.showonapplaunch.FirstScreenHandlerImplTest"`
Expected: All 10 tests PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/generalsettings/showonapplaunch/FirstScreenHandler.kt
git add app/src/test/java/com/duckduckgo/app/generalsettings/showonapplaunch/FirstScreenHandlerImplTest.kt
git commit -m "Add FirstScreenHandler with idle return timeout"
```

---

## Chunk 3: Wire into BrowserViewModel

### Task 5: Replace ShowOnAppLaunchOptionHandler with FirstScreenHandler in BrowserViewModel

**Files:**
- Modify: `app/src/main/java/com/duckduckgo/app/browser/BrowserViewModel.kt`

- [ ] **Step 1: Replace constructor parameters**

In the `BrowserViewModel` constructor, replace:
```kotlin
private val showOnAppLaunchFeature: ShowOnAppLaunchFeature,
private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler,
```
with:
```kotlin
private val firstScreenHandler: FirstScreenHandler,
```

Note: `showOnAppLaunchOptionHandler` is NOT used elsewhere in `BrowserViewModel` — the `handleResolvedUrlStorage()` usage is in `BrowserTabViewModel` (a separate class with its own injection). Both `showOnAppLaunchFeature` and `showOnAppLaunchOptionHandler` can be fully removed from `BrowserViewModel`.

- [ ] **Step 2: Update handleShowOnAppLaunchOption()**

Replace:
```kotlin
fun handleShowOnAppLaunchOption() {
    if (showOnAppLaunchFeature.self().isEnabled()) {
        viewModelScope.launch {
            showOnAppLaunchOptionHandler.handleAppLaunchOption()
        }
    }
}
```
with:
```kotlin
fun handleShowOnAppLaunchOption() {
    viewModelScope.launch {
        firstScreenHandler.handleFirstScreen()
    }
}
```

- [ ] **Step 3: Remove unused imports**

Remove `ShowOnAppLaunchFeature` import if no longer used elsewhere in the file. Keep `ShowOnAppLaunchOptionHandler` import if it's still used for `handleResolvedUrlStorage`.

- [ ] **Step 4: Run compilation**

Run: `./gradlew :app:compilePlayDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run BrowserViewModel tests**

Run: `./gradlew :app:testPlayDebugUnitTest --tests "com.duckduckgo.app.browser.BrowserViewModelTest"`

This will initially fail. Update `BrowserViewModelTest`:
- Remove `showOnAppLaunchFeature` and `showOnAppLaunchOptionHandler` mocks from the test constructor
- Add `private val firstScreenHandler: FirstScreenHandler = mock()` and pass it to the constructor
- Update existing tests that verify `showOnAppLaunchOptionHandler.handleAppLaunchOption()` to instead verify `firstScreenHandler.handleFirstScreen()`
- Remove tests that check the `showOnAppLaunchFeature.self().isEnabled()` gate (that logic now lives in `FirstScreenHandler` and is tested in `FirstScreenHandlerImplTest`)

Expected after updates: PASS

- [ ] **Step 6: Run full unit test suite**

Run: `./gradlew :app:testPlayDebugUnitTest`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Run spotless**

Run: `./gradlew spotlessApply`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/duckduckgo/app/browser/BrowserViewModel.kt
# Also add any test files that were updated
git commit -m "Wire FirstScreenHandler into BrowserViewModel"
```

---

## Verification

- [ ] **Run full checks:** `./gradlew jvm_checks`
- [ ] **Verify feature flag disabled path:** With `showNTPAfterIdleReturn` disabled, app launch behavior should be identical to current behavior
- [ ] **Verify feature flag enabled path:** With `showNTPAfterIdleReturn` enabled and a timeout configured, returning to app after timeout should show a new tab
