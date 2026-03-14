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

package com.duckduckgo.app.tabs.ui

import android.net.Uri
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.GRID
import com.duckduckgo.app.tabs.model.TabSwitcherData.LayoutType.LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.DUCK_AI_GRID
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.DUCK_AI_LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.GRID_TAB
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.LIST_TAB
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.SELECTABLE_DUCK_AI_GRID
import com.duckduckgo.app.tabs.ui.TabSwitcherAdapter.TabSwitcherViewHolder.Companion.SELECTABLE_DUCK_AI_LIST
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.DuckAiTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.duckchat.api.DuckChat
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class TabSwitcherAdapterTest {

    private val itemClickListener: TabSwitcherListener = mock()
    private val webViewPreviewPersister: WebViewPreviewPersister = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val faviconManager: FaviconManager = mock()
    private val dispatchers: DispatcherProvider = mock()
    private val trackerCountAnimator: TrackerCountAnimator = mock()
    private val duckChat: DuckChat = mock()

    private lateinit var adapter: TabSwitcherAdapter

    @Before
    fun setup() {
        adapter = TabSwitcherAdapter(
            itemClickListener = itemClickListener,
            webViewPreviewPersister = webViewPreviewPersister,
            lifecycleOwner = lifecycleOwner,
            faviconManager = faviconManager,
            dispatchers = dispatchers,
            trackerCountAnimator = trackerCountAnimator,
            duckChat = duckChat,
        )
    }

    // --- DuckAiTab routing ---

    @Test
    fun `DuckAiTab in grid mode returns DUCK_AI_GRID`() {
        val entity = TabEntity("1", url = "https://duck.ai/chat", position = 0)
        adapter.updateData(listOf(DuckAiTab(entity, isActive = false)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(DUCK_AI_GRID, adapter.getItemViewType(0))
    }

    @Test
    fun `DuckAiTab in list mode returns DUCK_AI_LIST`() {
        val entity = TabEntity("1", url = "https://duck.ai/chat", position = 0)
        adapter.updateData(listOf(DuckAiTab(entity, isActive = false)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(DUCK_AI_LIST, adapter.getItemViewType(0))
    }

    // --- SelectableTab with duck.ai URL routing ---

    @Test
    fun `SelectableTab with duck ai url in grid mode returns SELECTABLE_DUCK_AI_GRID`() {
        val url = "https://duck.ai/chat"
        val entity = TabEntity("1", url = url, position = 0)
        whenever(duckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(true)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(SELECTABLE_DUCK_AI_GRID, adapter.getItemViewType(0))
    }

    @Test
    fun `SelectableTab with duck ai url in list mode returns SELECTABLE_DUCK_AI_LIST`() {
        val url = "https://duck.ai/chat"
        val entity = TabEntity("1", url = url, position = 0)
        whenever(duckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(true)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(SELECTABLE_DUCK_AI_LIST, adapter.getItemViewType(0))
    }

    @Test
    fun `SelectableTab with regular url in grid mode returns GRID_TAB`() {
        val url = "https://example.com"
        val entity = TabEntity("1", url = url, position = 0)
        whenever(duckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(false)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(GRID_TAB, adapter.getItemViewType(0))
    }

    @Test
    fun `SelectableTab with regular url in list mode returns LIST_TAB`() {
        val url = "https://example.com"
        val entity = TabEntity("1", url = url, position = 0)
        whenever(duckChat.isDuckChatUrl(Uri.parse(url))).thenReturn(false)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(LIST_TAB, adapter.getItemViewType(0))
    }

    // --- NormalTab routing unchanged ---

    @Test
    fun `NormalTab in grid mode returns GRID_TAB`() {
        val entity = TabEntity("1", url = "https://example.com", position = 0)
        adapter.updateData(listOf(NormalTab(entity, isActive = false)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(GRID_TAB, adapter.getItemViewType(0))
    }

    @Test
    fun `NormalTab in list mode returns LIST_TAB`() {
        val entity = TabEntity("1", url = "https://example.com", position = 0)
        adapter.updateData(listOf(NormalTab(entity, isActive = false)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(LIST_TAB, adapter.getItemViewType(0))
    }
}
