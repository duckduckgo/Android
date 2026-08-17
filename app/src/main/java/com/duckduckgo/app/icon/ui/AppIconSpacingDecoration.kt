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

package com.duckduckgo.app.icon.ui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Spreads a row of fixed-width icons edge to edge, so the first and last sit flush against the
 * list's own padding and the leftover width is shared equally between them. The grid places every
 * item at the start of its cell, so each column is nudged along by the difference between where
 * the even spread wants it and where its cell begins. Rows are separated by the same gap.
 */
class AppIconSpacingDecoration(private val metrics: AppIconCellMetrics) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val spanCount = (parent.layoutManager as? GridLayoutManager)?.spanCount ?: return
        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val onLastRow = position / spanCount == (state.itemCount - 1) / spanCount
        outRect.bottom = if (onLastRow) 0 else metrics.cellGap

        if (spanCount < 2) return

        val available = parent.width - parent.paddingStart - parent.paddingEnd
        if (available <= 0) return

        val cell = available / spanCount
        val spacing = (available - spanCount * metrics.cellSize) / (spanCount - 1)
        val offset = ((position % spanCount) * (metrics.cellSize + spacing - cell)).coerceAtLeast(0)

        if (parent.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            outRect.right = offset
        } else {
            outRect.left = offset
        }
    }
}
