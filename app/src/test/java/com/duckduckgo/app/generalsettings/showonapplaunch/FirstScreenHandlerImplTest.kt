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

    // --- Idle return enabled (covers both fresh and non-fresh launches) ---

    @Test
    fun whenIdleReturnEnabledAndElapsedExceedsTimeoutThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndFreshLaunchAndElapsedExceedsTimeoutThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedUnderTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(fiveMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndFreshLaunchAndElapsedUnderTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(fiveMinutesAgo)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndNoTimestampThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(0L)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndElapsedExactlyEqualsTimeoutThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val exactlyThirtyMinutesAgo = System.currentTimeMillis() - (30 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(exactlyThirtyMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsNullThenUsesDefaultTimeout() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(null)
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsNullAndUnderDefaultTimeoutThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn(null)
        val thirtySecondsAgo = System.currentTimeMillis() - (30 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtySecondsAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnEnabledAndSettingsMalformedThenUsesDefaultTimeout() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("not json")
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledAndTimeoutMinutesMissingThenUsesDefaultTimeout() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"otherKey": 30}""")
        val thirtyOneMinutesAgo = System.currentTimeMillis() - (31 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(thirtyOneMinutesAgo)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnEnabledThenDoesNotCheckShowOnAppLaunch() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(true)
        whenever(idleReturnToggle.getSettings()).thenReturn("""{"timeoutMinutes": 30}""")
        val fiveMinutesAgo = System.currentTimeMillis() - (5 * 60 * 1000)
        whenever(settingsDataStore.lastSessionBackgroundTimestamp).thenReturn(fiveMinutesAgo)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchToggle, never()).isEnabled()
    }

    // --- Idle return disabled (legacy ShowOnAppLaunch behavior) ---

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchEnabledThenDelegates() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(true)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verify(showOnAppLaunchOptionHandler).handleAppLaunchOption()
    }

    @Test
    fun whenIdleReturnDisabledAndFreshLaunchAndShowOnAppLaunchDisabledThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)
        whenever(showOnAppLaunchToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = true)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
    }

    @Test
    fun whenIdleReturnDisabledAndNotFreshLaunchThenDoesNothing() = runTest {
        whenever(idleReturnToggle.isEnabled()).thenReturn(false)

        testee.onOpen(isFreshLaunch = false)
        testScope.testScheduler.advanceUntilIdle()

        verifyNoInteractions(showOnAppLaunchOptionHandler)
        verify(showOnAppLaunchToggle, never()).isEnabled()
    }

    // --- onClose ---

    @Test
    fun whenOnCloseThenWritesTimestamp() {
        testee.onClose()

        verify(settingsDataStore).lastSessionBackgroundTimestamp = org.mockito.kotlin.any()
    }
}
