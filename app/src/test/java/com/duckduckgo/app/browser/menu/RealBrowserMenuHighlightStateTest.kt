/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.menu

import app.cash.turbine.test
import com.duckduckgo.app.browser.defaultbrowsing.prompts.AdditionalDefaultBrowserPrompts
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealBrowserMenuHighlightStateTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    private val additionalDefaultBrowserPrompts: AdditionalDefaultBrowserPrompts = mock()

    private val downloadMenuStateProvider: DownloadMenuStateProvider = mock()

    private val browserMenuDisplayRepository: BrowserMenuDisplayRepository = mock()

    private val highlightPopupMenuFlow = MutableStateFlow(false)

    private val hasNewDownloadFlow = MutableStateFlow(false)

    private val browserMenuStateFlow = MutableStateFlow(BrowserMenuDisplayState(hasOption = false, isEnabled = true))

    private lateinit var testee: RealBrowserMenuHighlightState

    @Before
    fun setUp() {
        whenever(additionalDefaultBrowserPrompts.highlightPopupMenu).thenReturn(highlightPopupMenuFlow)
        whenever(downloadMenuStateProvider.hasNewDownloadFlow).thenReturn(hasNewDownloadFlow)
        whenever(browserMenuDisplayRepository.browserMenuState).thenReturn(browserMenuStateFlow)

        testee = RealBrowserMenuHighlightState(
            additionalDefaultBrowserPrompts = additionalDefaultBrowserPrompts,
            downloadMenuStateProvider = downloadMenuStateProvider,
            browserMenuDisplayRepository = browserMenuDisplayRepository,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when both sources are false then highlightState has both false`() = runTest {
        testee.highlightState.test {
            val state = awaitItem()
            assertFalse(state.defaultBrowserHighlight)
            assertFalse(state.downloadHighlight)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when default browser highlight is true then defaultBrowserHighlight is true`() = runTest {
        testee.highlightState.test {
            awaitItem() // initial

            highlightPopupMenuFlow.value = true
            val state = awaitItem()
            assertTrue(state.defaultBrowserHighlight)
            assertFalse(state.downloadHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when new download is true and bottom sheet menu enabled then downloadHighlight is true`() = runTest {
        testee.highlightState.test {
            awaitItem() // initial

            hasNewDownloadFlow.value = true
            val state = awaitItem()
            assertFalse(state.defaultBrowserHighlight)
            assertTrue(state.downloadHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when new download is true but bottom sheet menu disabled then downloadHighlight is false`() = runTest {
        browserMenuStateFlow.value = BrowserMenuDisplayState(hasOption = false, isEnabled = false)

        testee = RealBrowserMenuHighlightState(
            additionalDefaultBrowserPrompts = additionalDefaultBrowserPrompts,
            downloadMenuStateProvider = downloadMenuStateProvider,
            browserMenuDisplayRepository = browserMenuDisplayRepository,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )

        testee.highlightState.test {
            val initial = awaitItem()
            assertFalse(initial.downloadHighlight)

            hasNewDownloadFlow.value = true
            // still false because bottom sheet menu is not enabled
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when both sources are true then both highlights are true`() = runTest {
        testee.highlightState.test {
            awaitItem() // initial

            highlightPopupMenuFlow.value = true
            val afterDefault = awaitItem()
            assertTrue(afterDefault.defaultBrowserHighlight)

            hasNewDownloadFlow.value = true
            val afterBoth = awaitItem()
            assertTrue(afterBoth.defaultBrowserHighlight)
            assertTrue(afterBoth.downloadHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when default browser clears but download is still active then downloadHighlight remains true`() = runTest {
        testee.highlightState.test {
            awaitItem() // initial

            highlightPopupMenuFlow.value = true
            awaitItem() // intermediate: (true, false)

            hasNewDownloadFlow.value = true
            val bothTrue = awaitItem()
            assertTrue(bothTrue.defaultBrowserHighlight)
            assertTrue(bothTrue.downloadHighlight)

            highlightPopupMenuFlow.value = false
            val afterClear = awaitItem()
            assertFalse(afterClear.defaultBrowserHighlight)
            assertTrue(afterClear.downloadHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when download clears but default browser is still active then defaultBrowserHighlight remains true`() = runTest {
        testee.highlightState.test {
            awaitItem() // initial

            highlightPopupMenuFlow.value = true
            awaitItem() // intermediate: (true, false)

            hasNewDownloadFlow.value = true
            val bothTrue = awaitItem()
            assertTrue(bothTrue.defaultBrowserHighlight)
            assertTrue(bothTrue.downloadHighlight)

            hasNewDownloadFlow.value = false
            val afterClear = awaitItem()
            assertTrue(afterClear.defaultBrowserHighlight)
            assertFalse(afterClear.downloadHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when both sources clear then both highlights become false`() = runTest {
        testee.highlightState.test {
            awaitItem() // initial

            highlightPopupMenuFlow.value = true
            awaitItem() // intermediate: (true, false)

            hasNewDownloadFlow.value = true
            val bothTrue = awaitItem()
            assertTrue(bothTrue.defaultBrowserHighlight)
            assertTrue(bothTrue.downloadHighlight)

            highlightPopupMenuFlow.value = false
            awaitItem() // intermediate: (false, true)

            hasNewDownloadFlow.value = false
            val afterClear = awaitItem()
            assertFalse(afterClear.defaultBrowserHighlight)
            assertFalse(afterClear.downloadHighlight)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
