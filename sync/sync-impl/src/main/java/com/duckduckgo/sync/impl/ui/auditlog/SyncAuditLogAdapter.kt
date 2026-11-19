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

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogEntryBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogHeaderBinding
import com.duckduckgo.sync.impl.iconRes
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.Entry
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.Header

sealed interface SyncAuditLogListItem {
    data object Header : SyncAuditLogListItem
    data class Entry(val row: SyncAuditLogRow) : SyncAuditLogListItem
}

class SyncAuditLogAdapter : ListAdapter<SyncAuditLogListItem, ViewHolder>(ItemCallback) {

    fun submitRows(rows: List<SyncAuditLogRow>) {
        submitList(listOf(Header) + rows.map(::Entry))
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        Header -> R.layout.item_sync_audit_log_header
        is Entry -> R.layout.item_sync_audit_log_entry
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_sync_audit_log_header -> HeaderViewHolder(ItemSyncAuditLogHeaderBinding.inflate(inflater, parent, false))
            R.layout.item_sync_audit_log_entry -> EntryViewHolder(ItemSyncAuditLogEntryBinding.inflate(inflater, parent, false))
            else -> error("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (holder is EntryViewHolder) holder.bind((getItem(position) as Entry).row)
    }
}

private object ItemCallback : DiffUtil.ItemCallback<SyncAuditLogListItem>() {
    override fun areItemsTheSame(oldItem: SyncAuditLogListItem, newItem: SyncAuditLogListItem): Boolean =
        if (oldItem is Entry && newItem is Entry) oldItem.row.item.entryId == newItem.row.item.entryId else oldItem == newItem

    override fun areContentsTheSame(oldItem: SyncAuditLogListItem, newItem: SyncAuditLogListItem): Boolean = oldItem == newItem
}

private class HeaderViewHolder(binding: ItemSyncAuditLogHeaderBinding) : ViewHolder(binding.root)

private class EntryViewHolder(private val binding: ItemSyncAuditLogEntryBinding) : ViewHolder(binding.root) {

    fun bind(row: SyncAuditLogRow) {
        val context = itemView.context
        val action = when (row.item.action) {
            SyncAuditLogAction.SYNCED -> R.string.syncAuditLogSynced
            SyncAuditLogAction.REMOVED -> R.string.syncAuditLogRemoved
        }
        with(binding.syncAuditLogEntry) {
            setPrimaryText(context.getString(R.string.syncAuditLogRowTitle, row.time.format(context.resources), context.getString(action)))
            setSecondaryText(row.item.deviceName)
            setLeadingIconDrawable(icon(row.item))
        }
    }

    private fun icon(item: SyncAuditLogItem): Drawable {
        val context = itemView.context
        val deviceIcon = requireNotNull(ContextCompat.getDrawable(context, item.deviceType.iconRes()))
        if (item.action != SyncAuditLogAction.REMOVED) return deviceIcon

        val dot = requireNotNull(ContextCompat.getDrawable(context, R.drawable.sync_audit_log_removed_dot))
        return LayerDrawable(arrayOf(deviceIcon, dot)).apply {
            setLayerGravity(1, Gravity.BOTTOM or Gravity.END)
            setLayerSize(1, dot.intrinsicWidth, dot.intrinsicHeight)
        }
    }
}
