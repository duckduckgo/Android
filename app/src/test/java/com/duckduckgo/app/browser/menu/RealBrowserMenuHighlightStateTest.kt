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

    private val highlightPopupMenuFlow = MutableStateFlow(false)

    private val hasNewDownloadFlow = MutableStateFlow(false)

    private lateinit var testee: RealBrowserMenuHighlightState

    @Before
    fun setUp() {
        whenever(additionalDefaultBrowserPrompts.highlightPopupMenu).thenReturn(highlightPopupMenuFlow)
        whenever(downloadMenuStateProvider.hasNewDownloadFlow).thenReturn(hasNewDownloadFlow)

        testee = RealBrowserMenuHighlightState(
            additionalDefaultBrowserPrompts = additionalDefaultBrowserPrompts,
            downloadMenuStateProvider = downloadMenuStateProvider,
            appCoroutineScope = coroutineTestRule.testScope,
            dispatcherProvider = coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun `when both sources are false then shouldHighlight is false`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when default browser highlight is true then shouldHighlight is true`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())

            highlightPopupMenuFlow.value = true
            assertTrue(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when new download is true then shouldHighlight is true`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())

            hasNewDownloadFlow.value = true
            assertTrue(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when both sources are true then shouldHighlight is true`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())

            highlightPopupMenuFlow.value = true
            assertTrue(awaitItem())

            hasNewDownloadFlow.value = true
            // still true, no new emission expected (same value)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when default browser clears but download is still active then shouldHighlight remains true`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())

            highlightPopupMenuFlow.value = true
            hasNewDownloadFlow.value = true
            assertTrue(awaitItem())

            highlightPopupMenuFlow.value = false
            // still true because hasNewDownload is true
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when download clears but default browser is still active then shouldHighlight remains true`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())

            highlightPopupMenuFlow.value = true
            hasNewDownloadFlow.value = true
            assertTrue(awaitItem())

            hasNewDownloadFlow.value = false
            // still true because highlightPopupMenu is true
            expectNoEvents()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when both sources clear then shouldHighlight becomes false`() = runTest {
        testee.shouldHighlight.test {
            assertFalse(awaitItem())

            highlightPopupMenuFlow.value = true
            hasNewDownloadFlow.value = true
            assertTrue(awaitItem())

            highlightPopupMenuFlow.value = false
            hasNewDownloadFlow.value = false
            assertFalse(awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
