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

package com.duckduckgo.app.browser.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabRepository
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeDataProvider
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckchat.api.DuckAiSessionWideEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BrowserSessionTabObserverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val regularSelectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val fireSelectedTabFlow = MutableStateFlow<TabEntity?>(null)
    private val regularTabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(regularSelectedTabFlow)
    }
    private val fireTabRepository: TabRepository = mock<TabRepository>().also {
        whenever(it.flowSelectedTab).thenReturn(fireSelectedTabFlow)
    }
    private val tabRepositoryProvider = object : BrowserModeDataProvider<TabRepository> {
        override fun forMode(mode: BrowserMode): TabRepository = when (mode) {
            BrowserMode.REGULAR -> regularTabRepository
            BrowserMode.FIRE -> fireTabRepository
        }
    }
    private val currentModeFlow = MutableStateFlow(BrowserMode.REGULAR)
    private val browserModeStateHolder: BrowserModeStateHolder = mock<BrowserModeStateHolder>().also {
        whenever(it.currentMode).thenReturn(currentModeFlow)
    }
    private val duckAiSessionWideEvent: DuckAiSessionWideEvent = mock()

    private lateinit var testee: BrowserSessionTabObserver

    @Before
    fun setup() {
        testee = BrowserSessionTabObserver(
            tabRepositoryProvider = tabRepositoryProvider,
            browserModeStateHolder = browserModeStateHolder,
            duckAiSessionWideEvent = duckAiSessionWideEvent,
            appCoroutineScope = coroutineRule.testScope,
        )
    }

    private fun idle() = coroutineRule.testScope.testScheduler.advanceUntilIdle()

    @Test
    fun `when no tab is selected at start then it is reported as null tab and null url`() = runTest {
        idle()

        verify(duckAiSessionWideEvent).onSelectedTabChanged(null, null)
    }

    @Test
    fun `when a tab is selected then its tabId and url are reported as observed`() = runTest {
        regularSelectedTabFlow.value = TabEntity(tabId = "tab-1", url = "https://duck.ai/?chatID=chat-a")
        idle()

        verify(duckAiSessionWideEvent).onSelectedTabChanged("tab-1", "https://duck.ai/?chatID=chat-a")
    }

    @Test
    fun `every emission is reported, including ones that only change an unrelated field`() = runTest {
        regularSelectedTabFlow.value = TabEntity(tabId = "tab-1", url = "https://duck.ai/", title = "Duck.ai")
        idle()
        regularSelectedTabFlow.value = TabEntity(tabId = "tab-1", url = "https://duck.ai/", title = "Duck.ai 2")
        idle()

        verify(duckAiSessionWideEvent, times(2)).onSelectedTabChanged("tab-1", "https://duck.ai/")
    }

    @Test
    fun `opening the tab switcher without changing the selection reports nothing new`() = runTest {
        regularSelectedTabFlow.value = TabEntity(tabId = "tab-1", url = "https://duck.ai/")
        idle()
        verify(duckAiSessionWideEvent).onSelectedTabChanged("tab-1", "https://duck.ai/")

        // Opening/dismissing the tab switcher doesn't touch the selected-tab flow at all, so nothing
        // further is emitted — there's nothing to verify beyond the single call above.
        verifyNoMoreInteractions(duckAiSessionWideEvent)
    }

    @Test
    fun `when the browser mode switches then the other mode's selected tab is reported`() = runTest {
        regularSelectedTabFlow.value = TabEntity(tabId = "regular-tab", url = "https://duck.ai/")
        idle()

        currentModeFlow.value = BrowserMode.FIRE
        fireSelectedTabFlow.value = TabEntity(tabId = "fire-tab", url = "https://example.com")
        idle()

        verify(duckAiSessionWideEvent).onSelectedTabChanged("regular-tab", "https://duck.ai/")
        verify(duckAiSessionWideEvent).onSelectedTabChanged("fire-tab", "https://example.com")
    }
}
