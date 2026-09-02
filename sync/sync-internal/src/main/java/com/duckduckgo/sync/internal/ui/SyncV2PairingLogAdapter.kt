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

package com.duckduckgo.sync.internal.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.sync.internal.databinding.ItemSyncV2PairingLogRowBinding

internal class SyncV2PairingLogAdapter(
    private val onCopyJson: (String) -> Unit,
) : ListAdapter<LogRow, SyncV2PairingLogRowViewHolder>(LogRowDiffCallback) {

    private val expandedRowIds = mutableSetOf<Long>()

    fun clearExpansionState() {
        expandedRowIds.clear()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SyncV2PairingLogRowViewHolder {
        val binding = ItemSyncV2PairingLogRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return SyncV2PairingLogRowViewHolder(
            binding = binding,
            isExpanded = { rowId -> rowId in expandedRowIds },
            onToggleExpanded = { rowId -> if (!expandedRowIds.add(rowId)) expandedRowIds.remove(rowId) },
            onCopyJson = onCopyJson,
        )
    }

    override fun onBindViewHolder(holder: SyncV2PairingLogRowViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private object LogRowDiffCallback : DiffUtil.ItemCallback<LogRow>() {
        override fun areItemsTheSame(oldItem: LogRow, newItem: LogRow) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: LogRow, newItem: LogRow) = oldItem == newItem

        override fun getChangePayload(oldItem: LogRow, newItem: LogRow) = Unit
    }
}

internal class SyncV2PairingLogRowViewHolder(
    private val binding: ItemSyncV2PairingLogRowBinding,
    private val isExpanded: (rowId: Long) -> Boolean,
    private val onToggleExpanded: (rowId: Long) -> Unit,
    onCopyJson: (String) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    private var currentRow: LogRow? = null

    init {
        binding.summaryRow.setOnClickListener {
            currentRow?.takeIf { it.isExpandable }?.let { row ->
                onToggleExpanded(row.id)
                renderExpansion(row)
            }
        }
        binding.copyJsonButton.setOnClickListener {
            currentRow?.rawJson?.let(onCopyJson)
        }
    }

    fun bind(row: LogRow) {
        currentRow = row
        binding.eventTypeTextView.text = row.eventType.label
        binding.eventSummaryTextView.text = row.summary
        binding.timestampTextView.text = row.timestampText
        renderExpansion(row)
    }

    private fun renderExpansion(row: LogRow) {
        if (!row.isExpandable) {
            binding.chevronTextView.visibility = View.GONE
            binding.expandedDetail.visibility = View.GONE
            return
        }
        binding.chevronTextView.visibility = View.VISIBLE
        val expanded = isExpanded(row.id)
        binding.chevronTextView.text = if (expanded) "▾" else "▸"
        binding.expandedDetail.visibility = if (expanded) View.VISIBLE else View.GONE
        if (!expanded) return

        binding.detailsTextView.isGone = row.details.isNullOrEmpty()
        binding.detailsTextView.text = row.details
        if (row.prettyJson == null) {
            binding.rawJsonTextView.visibility = View.GONE
            binding.copyJsonButton.visibility = View.GONE
        } else {
            binding.rawJsonTextView.visibility = View.VISIBLE
            binding.rawJsonTextView.text = row.prettyJson
            binding.copyJsonButton.visibility = View.VISIBLE
        }
    }
}

internal class AlternatingRowBackgroundDecoration(context: Context) : RecyclerView.ItemDecoration() {

    private val alternateRowPaint = Paint().apply {
        color = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorContainer)
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            val position = parent.getChildAdapterPosition(child)
            if (position == RecyclerView.NO_POSITION || position % 2 == 0) {
                canvas.drawRect(
                    child.left.toFloat(),
                    child.top + child.translationY,
                    child.right.toFloat(),
                    child.bottom + child.translationY,
                    alternateRowPaint,
                )
            }
        }
    }
}
