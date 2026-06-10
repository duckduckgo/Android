/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.accessibility

import app.cash.turbine.test
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.VoiceSearchPixelNames
import com.duckduckgo.voice.store.VoiceSearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.time.ExperimentalTime

@ExperimentalTime
class AccessibilitySettingsViewModelTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val dispatcherProvider = coroutineRule.testDispatcherProvider

    private val voiceSearchRepository: VoiceSearchRepository = mock()
    private val voiceSearchAvailability: VoiceSearchAvailability = mock()
    private val accessibilitySettings: AccessibilitySettingsDataStore = mock()
    private val pixel: Pixel = mock()
    private val testee = AccessibilitySettingsViewModel(
        accessibilitySettings,
        voiceSearchAvailability,
        voiceSearchRepository,
        pixel,
        dispatcherProvider,
    )

    @Test
    fun whenViewModelCreatedThenDefaultViewStateEmitted() = runTest {
        val viewState = AccessibilitySettingsViewModel.ViewState(
            overrideSystemFontSize = false,
            appFontSize = 100f,
            forceZoom = false,
            voiceSearchEnabled = false,
            showVoiceSearch = false,
        )
        testee.viewState().test {
            assertEquals(viewState, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenViewModelStartedThenViewStateEmitted() = runTest {
        val viewState = AccessibilitySettingsViewModel.ViewState(
            overrideSystemFontSize = true,
            appFontSize = 150f,
            forceZoom = true,
            voiceSearchEnabled = true,
            showVoiceSearch = true,
        )
        whenever(accessibilitySettings.overrideSystemFontSize).thenReturn(true)
        whenever(accessibilitySettings.appFontSize).thenReturn(150f)
        whenever(accessibilitySettings.forceZoom).thenReturn(true)
        whenever(voiceSearchAvailability.isVoiceSearchSupported).thenReturn(true)
        whenever(voiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)

        testee.start()

        testee.viewState().test {
            assertEquals(viewState, awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenForceZoomEnabledThenViewStateEmitted() = runTest {
        val viewState = defaultViewState()
        whenever(accessibilitySettings.forceZoom).thenReturn(true)

        testee.onForceZoomChanged(true)

        testee.viewState().test {
            assertEquals(viewState.copy(forceZoom = true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenForceZoomEnabledThenSettingsUpdated() = runTest {
        whenever(accessibilitySettings.forceZoom).thenReturn(true)

        testee.onForceZoomChanged(true)

        verify(accessibilitySettings).forceZoom = true
        verify(accessibilitySettings).forceZoom
    }

    @Test
    fun whenOverrideSystemFontSizeEnabledThenViewStateEmitted() = runTest {
        val viewState = defaultViewState()
        whenever(accessibilitySettings.overrideSystemFontSize).thenReturn(true)

        testee.onSystemFontSizeChanged(true)

        testee.viewState().test {
            assertEquals(viewState.copy(overrideSystemFontSize = true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenOverrideSystemFontSizeEnabledThenSettingsUpdated() = runTest {
        whenever(accessibilitySettings.overrideSystemFontSize).thenReturn(true)

        testee.onSystemFontSizeChanged(true)

        verify(accessibilitySettings).overrideSystemFontSize = true
        verify(accessibilitySettings).overrideSystemFontSize
    }

    @Test
    fun whenFontSizeChangedThenViewStateEmitted() = runTest {
        val viewState = defaultViewState()
        whenever(accessibilitySettings.appFontSize).thenReturn(150f)

        testee.onFontSizeChanged(150f)

        testee.viewState().test {
            assertEquals(viewState.copy(appFontSize = 150f), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFontSizeChangedThenSettingsUpdated() = runTest {
        whenever(accessibilitySettings.appFontSize).thenReturn(150f)

        testee.onFontSizeChanged(150f)

        verify(accessibilitySettings).appFontSize = 150f
        verify(accessibilitySettings).appFontSize
    }

    @Test
    fun whenVoiceSearchEnabledThenViewStateEmitted() = runTest {
        val viewState = defaultViewState()
        whenever(voiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)

        testee.onVoiceSearchChanged(true)

        testee.viewState().test {
            assertEquals(viewState.copy(voiceSearchEnabled = true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVoiceSearchEnabledThenSettingsUpdated() = runTest {
        testee.onVoiceSearchChanged(true)
        verify(voiceSearchRepository).setVoiceSearchUserEnabled(true)
    }

    @Test
    fun whenVoiceSearchDisabledThenSettingsUpdated() = runTest {
        testee.onVoiceSearchChanged(false)
        verify(voiceSearchRepository).setVoiceSearchUserEnabled(false)
    }

    @Test
    fun whenVoiceSearchEnabledThenFirePixel() = runTest {
        testee.onVoiceSearchChanged(true)
        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_ON)
    }

    @Test
    fun whenVoiceSearchDisabledThenFirePixel() = runTest {
        testee.onVoiceSearchChanged(false)
        verify(pixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_OFF)
    }

    private fun defaultViewState() = AccessibilitySettingsViewModel.ViewState()
}
