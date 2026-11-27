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
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.sync.impl.R
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogEntryBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogErrorBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogHeaderBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogLoadingBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogMessageBinding
import com.duckduckgo.sync.impl.databinding.ItemSyncAuditLogUnavailableBinding
import com.duckduckgo.sync.impl.iconRes
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.Entry
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.Header
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.LoadMoreFailed
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.LoadingMore
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.Message
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogListItem.Unavailable
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Content
import com.duckduckgo.sync.impl.ui.auditlog.SyncAuditLogViewModel.Footer
import com.duckduckgo.mobile.android.R as CommonR

sealed interface SyncAuditLogListItem {
    data object Header : SyncAuditLogListItem
    data class Entry(val row: SyncAuditLogRow) : SyncAuditLogListItem
    data object LoadingMore : SyncAuditLogListItem
    data object LoadMoreFailed : SyncAuditLogListItem
    data class Message(@StringRes val text: Int) : SyncAuditLogListItem
    data object Unavailable : SyncAuditLogListItem
}

class SyncAuditLogAdapter(
    private val onRetryClicked: () -> Unit,
) : ListAdapter<SyncAuditLogListItem, ViewHolder>(ItemCallback) {

    fun submitRows(
        rows: List<SyncAuditLogRow>,
        footer: Footer,
        content: Content,
        onCommitted: () -> Unit,
    ) {
        val body = when (content) {
            Content.LOADING -> emptyList()
            Content.ERROR -> listOf(Unavailable)
            Content.EMPTY -> listOf(Message(R.string.syncAuditLogEmpty))
            Content.ONLY_THIS_DEVICE -> listOf(Message(R.string.syncAuditLogOnlyThisDevice))
            Content.LIST -> rows.map(::Entry) + listOfNotNull(footer.toItem())
        }
        submitList(listOf(Header) + body) { onCommitted() }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        Header -> R.layout.item_sync_audit_log_header
        is Entry -> R.layout.item_sync_audit_log_entry
        LoadingMore -> R.layout.item_sync_audit_log_loading
        LoadMoreFailed -> R.layout.item_sync_audit_log_error
        is Message -> R.layout.item_sync_audit_log_message
        Unavailable -> R.layout.item_sync_audit_log_unavailable
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            R.layout.item_sync_audit_log_header -> StaticViewHolder(ItemSyncAuditLogHeaderBinding.inflate(inflater, parent, false).root)
            R.layout.item_sync_audit_log_entry -> EntryViewHolder(ItemSyncAuditLogEntryBinding.inflate(inflater, parent, false))
            R.layout.item_sync_audit_log_loading -> StaticViewHolder(ItemSyncAuditLogLoadingBinding.inflate(inflater, parent, false).root)
            R.layout.item_sync_audit_log_error -> StaticViewHolder(
                ItemSyncAuditLogErrorBinding.inflate(inflater, parent, false).root.apply { setOnClickListener { onRetryClicked() } },
            )
            R.layout.item_sync_audit_log_message -> MessageViewHolder(ItemSyncAuditLogMessageBinding.inflate(inflater, parent, false))
            R.layout.item_sync_audit_log_unavailable -> StaticViewHolder(
                ItemSyncAuditLogUnavailableBinding.inflate(inflater, parent, false).root,
            )
            else -> error("Unknown view type: $viewType")
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (holder) {
            is EntryViewHolder -> holder.bind((getItem(position) as Entry).row)
            is MessageViewHolder -> holder.binding.syncAuditLogMessage.setText((getItem(position) as Message).text)
        }
    }

    private fun Footer.toItem(): SyncAuditLogListItem? = when (this) {
        Footer.NONE -> null
        Footer.LOADING -> LoadingMore
        Footer.ERROR -> LoadMoreFailed
    }
}

private object ItemCallback : DiffUtil.ItemCallback<SyncAuditLogListItem>() {
    override fun areItemsTheSame(oldItem: SyncAuditLogListItem, newItem: SyncAuditLogListItem): Boolean =
        if (oldItem is Entry && newItem is Entry) oldItem.row.item.entryId == newItem.row.item.entryId else oldItem == newItem

    override fun areContentsTheSame(oldItem: SyncAuditLogListItem, newItem: SyncAuditLogListItem): Boolean = oldItem == newItem
}

private class StaticViewHolder(view: View) : ViewHolder(view)

private class MessageViewHolder(val binding: ItemSyncAuditLogMessageBinding) : ViewHolder(binding.root)

private class EntryViewHolder(private val binding: ItemSyncAuditLogEntryBinding) : ViewHolder(binding.root) {

    fun bind(row: SyncAuditLogRow) {
        val context = itemView.context
        val action = when (row.item.action) {
            SyncAuditLogAction.SYNCED -> R.string.syncAuditLogSynced
            SyncAuditLogAction.REMOVED -> R.string.syncAuditLogRemoved
        }
        with(binding.syncAuditLogEntry) {
            setPrimaryText(context.getString(R.string.syncAuditLogRowTitle, context.getString(action), row.item.deviceName))
            setSecondaryText(row.time.format(context.resources))
            setLeadingIconDrawable(icon(row.item))
            if (row.highlighted) setBackgroundColor(context.getColorFromAttr(CommonR.attr.daxColorAccentAltPrimary)) else background = null
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
