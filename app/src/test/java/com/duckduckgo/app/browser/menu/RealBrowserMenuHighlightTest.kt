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

package com.duckduckgo.app.browser.menu

import app.cash.turbine.test
import com.duckduckgo.common.utils.plugins.PluginPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealBrowserMenuHighlightTest {

    private val pluginAFlow = MutableStateFlow(false)
    private val pluginBFlow = MutableStateFlow(false)

    private val pluginA: BrowserMenuHighlightPlugin = mock {
        whenever(mock.compatibleModes).thenReturn(setOf(BrowserViewMode.Browser))
        whenever(mock.isHighlighted()).thenReturn(pluginAFlow)
    }

    private val pluginB: BrowserMenuHighlightPlugin = mock {
        whenever(mock.compatibleModes).thenReturn(BrowserViewMode.entries.toSet() - BrowserViewMode.CustomTab)
        whenever(mock.isHighlighted()).thenReturn(pluginBFlow)
    }

    @Test
    fun `when no plugins and mode is Browser then highlight is false`() = runTest {
        val testee = createTestee(emptyList())
        testee.shouldShowHighlightForMode(BrowserViewMode.Browser).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when browser-only plugin highlighted and mode is Browser then shouldShowHighlightForMode is true`() = runTest {
        val testee = createTestee(listOf(pluginA, pluginB))
        pluginAFlow.value = true
        testee.shouldShowHighlightForMode(BrowserViewMode.Browser).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when browser-only plugin highlighted and mode is NewTab then shouldShowHighlightForMode is false`() = runTest {
        val testee = createTestee(listOf(pluginA, pluginB))
        pluginAFlow.value = true
        testee.shouldShowHighlightForMode(BrowserViewMode.NewTab).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when all-modes plugin highlighted and mode is NewTab then shouldShowHighlightForMode is true`() = runTest {
        val testee = createTestee(listOf(pluginA, pluginB))
        pluginBFlow.value = true
        testee.shouldShowHighlightForMode(BrowserViewMode.NewTab).test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when all-modes plugin highlighted and mode is CustomTab then shouldShowHighlightForMode is false`() = runTest {
        val testee = createTestee(listOf(pluginA, pluginB))
        pluginBFlow.value = true
        testee.shouldShowHighlightForMode(BrowserViewMode.CustomTab).test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when plugin clears then highlight becomes false`() = runTest {
        val testee = createTestee(listOf(pluginA, pluginB))
        testee.shouldShowHighlightForMode(BrowserViewMode.Browser).test {
            assertFalse(awaitItem())
            pluginAFlow.value = true
            assertTrue(awaitItem())
            pluginAFlow.value = false
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createTestee(plugins: List<BrowserMenuHighlightPlugin>): RealBrowserMenuHighlight {
        val pluginPoint = object : PluginPoint<BrowserMenuHighlightPlugin> {
            override fun getPlugins(): Collection<BrowserMenuHighlightPlugin> = plugins
        }
        return RealBrowserMenuHighlight(
            plugins = pluginPoint,
        )
    }
}
