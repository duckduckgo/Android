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

package com.duckduckgo.app.tabs.model

import com.duckduckgo.browsermode.api.BrowserMode.FIRE
import com.duckduckgo.browsermode.api.BrowserMode.REGULAR
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.FireModeAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RealAggregateTabProviderTest {

    private val regularTabs = MutableStateFlow(listOf(TabEntity(tabId = "r1", position = 0)))
    private val fireTabs = MutableStateFlow(listOf(TabEntity(tabId = "f1", position = 0)))

    private val regularRepo: TabRepository = mock()
    private val fireRepo: TabRepository = mock()
    private val tabRepositoryProvider: BrowserModeDataProvider<TabRepository> = mock()
    private val fireAvailability: FireModeAvailability = mock()

    private val testee by lazy { RealAggregateTabProvider(tabRepositoryProvider, fireAvailability) }

    @Before
    fun setup() {
        whenever(regularRepo.flowTabs).thenReturn(regularTabs)
        whenever(fireRepo.flowTabs).thenReturn(fireTabs)
        whenever(tabRepositoryProvider.forMode(REGULAR)).thenReturn(regularRepo)
        whenever(tabRepositoryProvider.forMode(FIRE)).thenReturn(fireRepo)
        whenever(fireAvailability.isAvailable()).thenReturn(true)
    }

    @Test
    fun whenAllModesRequestedThenEmitsConcatenationOfRegularAndFire() = runTest {
        val tabs = testee.observe(setOf(REGULAR, FIRE)).first()

        assertEquals(setOf("r1", "f1"), tabs.map { it.tabId }.toSet())
    }

    @Test
    fun whenDefaultArgsThenAggregatesAcrossAllBrowserModes() = runTest {
        val tabs = testee.observe().first()

        assertEquals(setOf("r1", "f1"), tabs.map { it.tabId }.toSet())
    }

    @Test
    fun whenOnlyRegularRequestedThenEmitsRegularOnly() = runTest {
        val tabs = testee.observe(setOf(REGULAR)).first()

        assertEquals(listOf("r1"), tabs.map { it.tabId })
    }

    @Test
    fun whenOnlyFireRequestedThenEmitsFireOnly() = runTest {
        val tabs = testee.observe(setOf(FIRE)).first()

        assertEquals(listOf("f1"), tabs.map { it.tabId })
    }

    @Test
    fun whenFireUnavailableThenFireModeContributesNothing() = runTest {
        whenever(fireAvailability.isAvailable()).thenReturn(false)

        val tabs = testee.observe(setOf(REGULAR, FIRE)).first()

        assertEquals(listOf("r1"), tabs.map { it.tabId })
    }

    @Test
    fun whenFireUnavailableAndOnlyFireRequestedThenEmitsEmpty() = runTest {
        whenever(fireAvailability.isAvailable()).thenReturn(false)

        val tabs = testee.observe(setOf(FIRE)).first()

        assertEquals(emptyList<String>(), tabs.map { it.tabId })
    }

    @Test
    fun whenEmptyModeSetThenEmitsEmpty() = runTest {
        val tabs = testee.observe(emptySet()).first()

        assertEquals(emptyList<String>(), tabs.map { it.tabId })
    }
}
