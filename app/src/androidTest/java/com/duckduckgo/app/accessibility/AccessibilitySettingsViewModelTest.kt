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
import com.duckduckgo.app.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import com.duckduckgo.app.accessibility.data.AccessibilitySettingsDataStore
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.time.ExperimentalTime

@ExperimentalCoroutinesApi
@ExperimentalTime
class AccessibilitySettingsViewModelTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val accessibilitySettings: AccessibilitySettingsDataStore = mock()
    private val testee = AccessibilitySettingsViewModel(accessibilitySettings)

    @Test
    fun whenViewModelCreatedThenDefaultViewStateEmitted() = runTest {
        val viewState = AccessibilitySettingsViewModel.ViewState(
            overrideSystemFontSize = false,
            appFontSize = 100f,
            forceZoom = false
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
            forceZoom = true
        )
        whenever(accessibilitySettings.overrideSystemFontSize).thenReturn(true)
        whenever(accessibilitySettings.appFontSize).thenReturn(150f)
        whenever(accessibilitySettings.forceZoom).thenReturn(true)

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

    private fun defaultViewState() = AccessibilitySettingsViewModel.ViewState()

}
