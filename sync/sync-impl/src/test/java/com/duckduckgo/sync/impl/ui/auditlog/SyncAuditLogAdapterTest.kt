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

package com.duckduckgo.sync.impl.ui.auditlog

import android.text.TextUtils.TruncateAt
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.ui.view.listitem.TwoLineListItem
import com.duckduckgo.sync.impl.DeviceType
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Content
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Footer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.shadows.ShadowLooper
import java.time.Instant
import com.duckduckgo.mobile.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class SyncAuditLogAdapterTest {

    private lateinit var activity: AppCompatActivity

    @Before
    fun setUp() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java)
        activity = controller.get().apply { setTheme(CommonR.style.Theme_DuckDuckGo_Light) }
        controller.setup()
    }

    @Test
    fun whenRemovedRowThenFirstLineIsActionThenDeviceName() {
        val entry = bindEntry(row(SyncAuditLogAction.REMOVED, "iPhone", RelativeTime.JustNow))

        assertEquals("Removed · iPhone", entry.primaryText())
    }

    @Test
    fun whenSyncedRowThenFirstLineIsActionThenDeviceName() {
        val entry = bindEntry(row(SyncAuditLogAction.SYNCED, "Pixel 8", RelativeTime.Days(2)))

        assertEquals("Synced · Pixel 8", entry.primaryText())
    }

    @Test
    fun whenRowBoundThenSecondLineIsRelativeTime() {
        assertEquals("Just now", bindEntry(row(SyncAuditLogAction.REMOVED, "iPhone", RelativeTime.JustNow)).secondaryText())
        assertEquals("2 days ago", bindEntry(row(SyncAuditLogAction.SYNCED, "iPhone", RelativeTime.Days(2))).secondaryText())
    }

    @Test
    fun whenDeviceNameIsLongThenFirstLineIsCutAtTheEndSoTheActionStaysVisible() {
        val entry = bindEntry(row(SyncAuditLogAction.REMOVED, "A very long device name ".repeat(10), RelativeTime.JustNow))
        val firstLine = entry.findViewById<TextView>(CommonR.id.primaryText)

        assertTrue(firstLine.text.startsWith("Removed · "))
        assertEquals(1, firstLine.maxLines)
        assertEquals(TruncateAt.END, firstLine.ellipsize)
    }

    @Test
    fun whenRowHighlightedThenRowHasBackground() {
        assertNotNull(bindEntry(row(SyncAuditLogAction.SYNCED, "iPhone", RelativeTime.JustNow, highlighted = true)).background)
        assertNull(bindEntry(row(SyncAuditLogAction.SYNCED, "iPhone", RelativeTime.JustNow, highlighted = false)).background)
    }

    @Test
    fun whenErrorThenHeaderThenUnavailableRowWithIcon() {
        val items = itemViews(Content.ERROR)

        assertEquals(2, items.size)
        assertEquals(listOf("Most recently synced or removed devices:"), items[0].texts())
        assertEquals(listOf("Device activity is temporarily unavailable"), items[1].texts())
        assertNotNull(items[1].findViewById<ImageView>(CommonR.id.leadingIcon).drawable)
        assertFalse(items[1].isClickable)
    }

    @Test
    fun whenEmptyThenHeaderThenNoRecentActivityRow() {
        val items = itemViews(Content.EMPTY)

        assertEquals(2, items.size)
        assertEquals(listOf("You have no recent activity"), items[1].texts())
    }

    @Test
    fun whenOnlyThisDeviceThenHeaderThenOnlyThisDeviceRow() {
        val items = itemViews(Content.ONLY_THIS_DEVICE)

        assertEquals(2, items.size)
        assertEquals(listOf("Only this device is synced"), items[1].texts())
    }

    @Test
    fun whenListWithLoadMoreErrorThenHeaderThenRowsThenRetryFooter() {
        val rows = listOf(
            row(SyncAuditLogAction.SYNCED, "iPhone", RelativeTime.JustNow),
            row(SyncAuditLogAction.REMOVED, "Pixel 8", RelativeTime.JustNow),
        )
        val items = itemViews(Content.LIST, rows, Footer.ERROR)

        assertEquals(4, items.size)
        assertEquals(listOf("Synced · iPhone", "Just now"), items[1].texts())
        assertEquals(listOf("Removed · Pixel 8", "Just now"), items[2].texts())
        assertEquals(listOf("Older activity is temporarily unavailable. Tap to retry."), items[3].texts())
    }

    @Test
    fun whenLoadingThenOnlyHeader() {
        assertEquals(1, itemViews(Content.LOADING).size)
    }

    private fun itemViews(
        content: Content,
        rows: List<SyncAuditLogRow> = emptyList(),
        footer: Footer = Footer.NONE,
    ): List<View> {
        val adapter = SyncAuditLogAdapter(onRetryClicked = {})
        adapter.submitRows(rows, footer, content) {}
        ShadowLooper.idleMainLooper()
        val parent = FrameLayout(activity)
        return (0 until adapter.itemCount).map { position ->
            val holder: ViewHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
            adapter.onBindViewHolder(holder, position)
            holder.itemView
        }
    }

    private fun View.texts(): List<String> = when {
        visibility != View.VISIBLE -> emptyList()
        this is TextView -> listOf(text.toString()).filter { it.isNotEmpty() }
        this is ViewGroup -> (0 until childCount).flatMap { getChildAt(it).texts() }
        else -> emptyList()
    }

    private fun bindEntry(row: SyncAuditLogRow): TwoLineListItem {
        val adapter = SyncAuditLogAdapter(onRetryClicked = {})
        adapter.submitRows(listOf(row), Footer.NONE, Content.LIST) {}
        ShadowLooper.idleMainLooper()
        val position = 1
        val parent = FrameLayout(activity)
        val holder: ViewHolder = adapter.onCreateViewHolder(parent, adapter.getItemViewType(position))
        adapter.onBindViewHolder(holder, position)
        return holder.itemView.findViewById(R.id.syncAuditLogEntry)
    }

    private fun TwoLineListItem.primaryText() = findViewById<TextView>(CommonR.id.primaryText).text.toString()

    private fun TwoLineListItem.secondaryText() = findViewById<TextView>(CommonR.id.secondaryText).text.toString()

    private fun row(
        action: SyncAuditLogAction,
        deviceName: String,
        time: RelativeTime,
        highlighted: Boolean = false,
    ) = SyncAuditLogRow(
        item = SyncAuditLogItem("id", action, Instant.EPOCH, deviceName, DeviceType("mobile")),
        time = time,
        highlighted = highlighted,
    )
}
