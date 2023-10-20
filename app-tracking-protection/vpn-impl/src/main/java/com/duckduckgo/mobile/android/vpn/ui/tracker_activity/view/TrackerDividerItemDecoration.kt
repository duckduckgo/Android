/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.ui.tracker_activity.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.R
import com.duckduckgo.mobile.android.ui.view.getColorFromAttr

class TrackerDividerItemDecoration(val context: Context) : RecyclerView.ItemDecoration() {
    private val dividerPaint = Paint()

    init {
        dividerPaint.color = context.getColorFromAttr(R.attr.daxColorLines)
        dividerPaint.strokeWidth = context.resources.getDimensionPixelSize(R.dimen.horizontalDividerHeight).toFloat()
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val startPadding =
            context.resources.getDimensionPixelSize(R.dimen.listItemImageContainerSize) +
                2 * context.resources.getDimensionPixelSize(R.dimen.keyline_4)
        val start = parent.paddingLeft + startPadding
        val end = parent.width - parent.paddingRight

        val childCount = parent.childCount
        for (i in 0 until childCount - 1) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams

            val top = child.bottom + params.bottomMargin
            val bottom = top + dividerPaint.strokeWidth

            canvas.drawLine(start.toFloat(), top.toFloat(), end.toFloat(), bottom, dividerPaint)
        }
    }
}
