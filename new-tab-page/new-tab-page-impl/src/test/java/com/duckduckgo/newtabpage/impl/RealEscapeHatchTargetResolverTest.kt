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

package com.duckduckgo.newtabpage.impl

import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.browsermode.api.FireModeAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDateTime

class RealEscapeHatchTargetResolverTest {

    private val regularRepo: TabRepository = mock()
    private val fireRepo: TabRepository = mock()
    private val modeFlow = MutableStateFlow(BrowserMode.REGULAR)
    private val stateHolder: BrowserModeStateHolder = mock { on { currentMode } doReturn modeFlow }
    private val fireModeAvailability: FireModeAvailability = mock { on { isAvailable() } doReturn true }

    private val provider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository =
            if (mode == BrowserMode.FIRE) fireRepo else regularRepo
    }

    private val testee = RealEscapeHatchTargetResolver(stateHolder, provider, fireModeAvailability)

    private fun tab(id: String, accessed: LocalDateTime?) =
        TabEntity(tabId = id, url = "https://$id.com", title = id, lastAccessTime = accessed)

    @Test
    fun whenFireModeThenReturnsNull() = runTest {
        modeFlow.value = BrowserMode.FIRE

        assertNull(testee.resolve())
    }

    @Test
    fun whenFireTabMoreRecentThenReturnsFireTarget() = runTest {
        whenever(regularRepo.getLastAccessedTab()).thenReturn(tab("reg", LocalDateTime.of(2026, 6, 1, 9, 0)))
        whenever(fireRepo.getLastAccessedTab()).thenReturn(tab("fire", LocalDateTime.of(2026, 6, 1, 10, 0)))

        val target = testee.resolve()

        assertEquals("fire", target?.tabId)
        assertEquals(BrowserMode.FIRE, target?.mode)
    }

    @Test
    fun whenRegularTabMoreRecentThenReturnsRegularTarget() = runTest {
        whenever(regularRepo.getLastAccessedTab()).thenReturn(tab("reg", LocalDateTime.of(2026, 6, 1, 11, 0)))
        whenever(fireRepo.getLastAccessedTab()).thenReturn(tab("fire", LocalDateTime.of(2026, 6, 1, 10, 0)))

        val target = testee.resolve()

        assertEquals("reg", target?.tabId)
        assertEquals(BrowserMode.REGULAR, target?.mode)
    }

    @Test
    fun whenOnlyFireTabThenReturnsFireTarget() = runTest {
        whenever(regularRepo.getLastAccessedTab()).thenReturn(null)
        whenever(fireRepo.getLastAccessedTab()).thenReturn(tab("fire", LocalDateTime.of(2026, 6, 1, 10, 0)))

        val target = testee.resolve()

        assertEquals("fire", target?.tabId)
        assertEquals(BrowserMode.FIRE, target?.mode)
    }

    @Test
    fun whenOnlyRegularTabThenReturnsRegularTarget() = runTest {
        whenever(regularRepo.getLastAccessedTab()).thenReturn(tab("reg", LocalDateTime.of(2026, 6, 1, 9, 0)))
        whenever(fireRepo.getLastAccessedTab()).thenReturn(null)

        val target = testee.resolve()

        assertEquals("reg", target?.tabId)
        assertEquals(BrowserMode.REGULAR, target?.mode)
    }

    @Test
    fun whenNoTabsThenReturnsNull() = runTest {
        whenever(regularRepo.getLastAccessedTab()).thenReturn(null)
        whenever(fireRepo.getLastAccessedTab()).thenReturn(null)

        assertNull(testee.resolve())
    }

    @Test
    fun whenFireModeUnavailableThenFireTabIgnoredEvenIfMoreRecent() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        whenever(regularRepo.getLastAccessedTab()).thenReturn(tab("reg", LocalDateTime.of(2026, 6, 1, 9, 0)))
        whenever(fireRepo.getLastAccessedTab()).thenReturn(tab("fire", LocalDateTime.of(2026, 6, 1, 10, 0)))

        val target = testee.resolve()

        assertEquals("reg", target?.tabId)
        assertEquals(BrowserMode.REGULAR, target?.mode)
        verify(fireRepo, never()).getLastAccessedTab()
    }

    @Test
    fun whenFireModeUnavailableAndOnlyFireTabThenReturnsNull() = runTest {
        whenever(fireModeAvailability.isAvailable()).thenReturn(false)
        whenever(regularRepo.getLastAccessedTab()).thenReturn(null)
        whenever(fireRepo.getLastAccessedTab()).thenReturn(tab("fire", LocalDateTime.of(2026, 6, 1, 10, 0)))

        assertNull(testee.resolve())
    }
}
