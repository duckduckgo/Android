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
