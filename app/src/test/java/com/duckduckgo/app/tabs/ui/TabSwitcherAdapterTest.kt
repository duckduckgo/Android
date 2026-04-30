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

import android.content.Context
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.browser.AddressDisplayFormatter
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.tabpreview.WebViewPreviewPersister
import com.duckduckgo.app.browser.tabs.adapter.TabSwitcherItemDiffCallback.Companion.DIFF_KEY_TITLE
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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
class TabSwitcherAdapterTest {

    private val itemClickListener: TabSwitcherListener = mock()
    private val webViewPreviewPersister: WebViewPreviewPersister = mock()
    private val lifecycleOwner: LifecycleOwner = mock()
    private val faviconManager: FaviconManager = mock()
    private val dispatchers: DispatcherProvider = mock()
    private val trackerCountAnimator: TrackerCountAnimator = mock()
    private val addressDisplayFormatter: AddressDisplayFormatter = mock()

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
            addressDisplayFormatter = addressDisplayFormatter,
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
    fun `SelectableTab with duck ai flag in grid mode returns SELECTABLE_DUCK_AI_GRID`() {
        val entity = TabEntity("1", url = "https://duck.ai/chat", position = 0)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false, isDuckAi = true)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(SELECTABLE_DUCK_AI_GRID, adapter.getItemViewType(0))
    }

    @Test
    fun `SelectableTab with duck ai flag in list mode returns SELECTABLE_DUCK_AI_LIST`() {
        val entity = TabEntity("1", url = "https://duck.ai/chat", position = 0)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false, isDuckAi = true)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(SELECTABLE_DUCK_AI_LIST, adapter.getItemViewType(0))
    }

    @Test
    fun `SelectableTab without duck ai flag in grid mode returns GRID_TAB`() {
        val entity = TabEntity("1", url = "https://example.com", position = 0)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false, isDuckAi = false)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(GRID_TAB, adapter.getItemViewType(0))
    }

    @Test
    fun `SelectableTab without duck ai flag in list mode returns LIST_TAB`() {
        val entity = TabEntity("1", url = "https://example.com", position = 0)
        adapter.updateData(listOf(SelectableTab(entity, isSelected = false, isDuckAi = false)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(LIST_TAB, adapter.getItemViewType(0))
    }

    // --- NormalTab routing unchanged ---

    @Test
    fun `NormalTab in grid mode returns GRID_TAB`() {
        val entity = TabEntity("1", url = "https://example.com", position = 0)
        adapter.updateData(listOf(NormalTab(entity = entity, isActive = false)))
        adapter.onLayoutTypeChanged(GRID)
        assertEquals(GRID_TAB, adapter.getItemViewType(0))
    }

    @Test
    fun `NormalTab in list mode returns LIST_TAB`() {
        val entity = TabEntity("1", url = "https://example.com", position = 0)
        adapter.updateData(listOf(NormalTab(entity = entity, isActive = false)))
        adapter.onLayoutTypeChanged(LIST)
        assertEquals(LIST_TAB, adapter.getItemViewType(0))
    }

    // --- Title rendering on payload-based bind (regression: stale title kept after URL changed) ---
    //
    // Repro: the differ produces a payload Bundle where DIFF_KEY_TITLE is present-but-null
    // (transition to null title can happen between pageChanged() and titleReceived() in the
    // BrowserTabViewModel). Before the fix, `bundle.getString(KEY)?.let { ... }` skipped on null,
    // leaving the previously-rendered text in place. After the fix, the title is re-derived from
    // the entity (via displayTitle() with URL-host fallback).

    @Test
    fun `payload with null title re-derives title from entity instead of leaving stale text`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entity = TabEntity(tabId = "1", url = "https://clicks.easyjet.com/redirect", title = null, position = 0)
        adapter.updateData(listOf(NormalTab(entity = entity, isActive = false)))
        adapter.onLayoutTypeChanged(LIST)

        val holder = listHolderWithStaleTitle(context, staleTitle = "How can we help? | Huel ES")

        // The diff callback writes the new title (null) under DIFF_KEY_TITLE - this is the buggy
        // bundle that previously caused the holder's TextView to keep its prior text.
        val payload = Bundle().apply { putString(DIFF_KEY_TITLE, null) }

        adapter.onBindViewHolder(holder, 0, mutableListOf(payload))

        // displayTitle() falls back to URL host when title is null
        assertEquals("clicks.easyjet.com", holder.title.text.toString())
    }

    @Test
    fun `payload with non-null title still updates TextView`() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entity = TabEntity(tabId = "1", url = "https://example.com", title = "Fresh Title", position = 0)
        adapter.updateData(listOf(NormalTab(entity = entity, isActive = false)))
        adapter.onLayoutTypeChanged(LIST)

        val holder = listHolderWithStaleTitle(context, staleTitle = "Old Title")

        val payload = Bundle().apply { putString(DIFF_KEY_TITLE, "Fresh Title") }

        adapter.onBindViewHolder(holder, 0, mutableListOf(payload))

        assertEquals("Fresh Title", holder.title.text.toString())
    }

    private fun listHolderWithStaleTitle(
        context: Context,
        staleTitle: String,
    ): TabSwitcherAdapter.TabSwitcherViewHolder.ListTabViewHolder {
        val titleView = TextView(context).apply { text = staleTitle }
        val holder = TabSwitcherAdapter.TabSwitcherViewHolder.ListTabViewHolder(
            rootView = FrameLayout(context),
            favicon = ImageView(context),
            title = titleView,
            close = ImageView(context),
            tabUnread = ImageView(context),
            selectionIndicator = ImageView(context),
            url = TextView(context),
        )
        // RecyclerView normally sets mItemViewType when binding. Hand-constructed holders default
        // to INVALID_TYPE, which would make the adapter's `when (holder.itemViewType)` dispatch
        // miss LIST_TAB and silently drop the payload. Force it here.
        val field = RecyclerView.ViewHolder::class.java.getDeclaredField("mItemViewType")
        field.isAccessible = true
        field.setInt(holder, LIST_TAB)
        return holder
    }
}
