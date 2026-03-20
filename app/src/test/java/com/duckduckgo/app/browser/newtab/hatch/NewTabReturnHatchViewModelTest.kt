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

package com.duckduckgo.app.browser.newtab.hatch

import app.cash.turbine.test
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class NewTabReturnHatchViewModelTest {

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private val mockTabRepository: TabRepository = mock()
    private val mockAndroidBrowserConfigFeature: AndroidBrowserConfigFeature = mock()
    private val mockToggle: Toggle = mock()
    private val lastAccessedTabFlow = MutableStateFlow<TabEntity?>(null)

    private lateinit var testee: NewTabReturnHatchViewModel

    @Before
    fun setup() {
        whenever(mockTabRepository.flowLastAccessedTab).thenReturn(lastAccessedTabFlow)
        whenever(mockAndroidBrowserConfigFeature.showNTPAfterIdleReturn()).thenReturn(mockToggle)
        whenever(mockToggle.isEnabled()).thenReturn(true)

        testee = NewTabReturnHatchViewModel(
            tabRepository = mockTabRepository,
            dispatchers = coroutinesTestRule.testDispatcherProvider,
            androidBrowserConfigFeature = mockAndroidBrowserConfigFeature,
        )
    }

    @Test
    fun whenLastAccessedTabExistsThenViewStateShowsTab() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        lastAccessedTabFlow.emit(tab)

        testee.viewState.test {
            val state = awaitItem()
            assertTrue(state.shouldShow)
            assertEquals("Example", state.tabTitle)
            assertEquals("https://example.com", state.url)
            assertEquals("tab1", state.tabId)
            assertEquals("tab1", state.currentTabId)
        }
    }

    @Test
    fun whenNoLastAccessedTabThenViewStateHidesHatch() = runTest {
        lastAccessedTabFlow.emit(null)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShow)
        }
    }

    @Test
    fun whenLastAccessedTabChangesThenViewStateUpdates() = runTest {
        val tab1 = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")
        val tab2 = TabEntity(tabId = "tab2", url = "https://other.com", title = "Other")

        testee.viewState.test {
            skipItems(1) // initial state

            lastAccessedTabFlow.emit(tab1)
            assertEquals("tab1", awaitItem().tabId)

            lastAccessedTabFlow.emit(tab2)
            assertEquals("tab2", awaitItem().tabId)
        }
    }

    @Test
    fun whenFeatureDisabledThenViewStateHidesHatch() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(false)
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        lastAccessedTabFlow.emit(tab)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShow)
        }
    }

    @Test
    fun whenFeatureDisabledAndNoTabThenViewStateHidesHatch() = runTest {
        whenever(mockToggle.isEnabled()).thenReturn(false)

        lastAccessedTabFlow.emit(null)

        testee.viewState.test {
            val state = awaitItem()
            assertFalse(state.shouldShow)
        }
    }

    @Test
    fun whenOnHatchPressedThenSelectsCurrentTab() = runTest {
        val tab = TabEntity(tabId = "tab1", url = "https://example.com", title = "Example")

        lastAccessedTabFlow.emit(tab)

        testee.viewState.test {
            awaitItem() // wait for state to settle
        }

        testee.onHatchPressed()

        verify(mockTabRepository).select("tab1")
    }

    @Test
    fun whenOnHatchPressedWithNoTabThenSelectsEmptyTabId() = runTest {
        lastAccessedTabFlow.emit(null)

        testee.viewState.test {
            awaitItem()
        }

        testee.onHatchPressed()

        verify(mockTabRepository).select("")
    }
}
