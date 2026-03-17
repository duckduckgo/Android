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
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.TestScope
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
    private val showOnAppLaunchOptionHandler: ShowOnAppLaunchOptionHandler = mock()
    private val idleReturnToggle: Toggle = mock()
    private val showOnAppLaunchToggle: Toggle = mock()
    private val testScope = TestScope()

    private lateinit var testee: FirstScreenHandlerImpl

    @Before
    fun setup() {
        whenever(androidBrowserConfigFeature.showNTPAfterIdleReturn()).thenReturn(idleReturnToggle)
        whenever(showOnAppLaunchFeature.self()).thenReturn(showOnAppLaunchToggle)

        testee = FirstScreenHandlerImpl(
            androidBrowserConfigFeature = androidBrowserConfigFeature,
            showOnAppLaunchFeature = showOnAppLaunchFeature,
            settingsDataStore = settingsDataStore,
            showOnAppLaunchOptionHandler = showOnAppLaunchOptionHandler,
            appCoroutineScope = testScope,
        )
    }

    // --- Fresh launch tests (ShowOnAppLaunch behavior) ---

    @Test
    fun whenFreshLaunchAndShowOnAppLaunchEnabledThenDelegates() = runTest {
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenFreshLaunchAndShowOnAppLaunchDisabledThenDoesNothing() = runTest {
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenFreshLaunchThenDoesNotCheckIdleReturn() = runTest {
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(idleReturnToggle, never()).isEnabled()
    }

    // --- Cold start tests (idle return behavior) ---

    @Test
    fun whenColdStartAndIdleReturnEnabledAndElapsedExceedsTimeoutThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenColdStartAndIdleReturnEnabledAndElapsedUnderTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(fiveMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenColdStartAndIdleReturnEnabledAndNoTimestampThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenColdStartAndIdleReturnEnabledAndElapsedExactlyEqualsTimeoutThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val exactlyThirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(exactlyThirtyMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenColdStartAndIdleReturnEnabledAndSettingsNullThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(null)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenColdStartAndIdleReturnEnabledAndSettingsMalformedThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("not json")

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenColdStartAndIdleReturnEnabledAndTimeoutMinutesMissingThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"otherKey": 30}""")

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenColdStartAndIdleReturnDisabledThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenColdStartThenDoesNotCheckShowOnAppLaunch() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchToggle, never()).isEnabled()
    }

    // --- onClose ---

    @Test
    fun whenOnCloseThenWritesTimestamp() {
        testee.onClose()

        verify(settingsDataStore).lastSessionBackgroundTimestamp = org.mockito.kotlin.any()
    }
}
