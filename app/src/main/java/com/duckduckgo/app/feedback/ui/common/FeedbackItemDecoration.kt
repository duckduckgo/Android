/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.feedback.ui.common

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.global.view.toPx

class FeedbackItemDecoration(private val divider: Drawable) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount

        val left = parent.paddingLeft + 22.toPx()
        val right = parent.width - parent.paddingRight

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as RecyclerView.LayoutParams
            val top = child.bottom + params.bottomMargin
            val bottom = top + divider.intrinsicHeight

            drawDividerAlongBottom(canvas, childLeft = left, childTop = top, childRight = right, childBottom = bottom)
        }
    }

    private fun drawDividerAlongBottom(canvas: Canvas, childLeft: Int, childTop: Int, childRight: Int, childBottom: Int) {
        divider.setBounds(childLeft, childTop, childRight, childBottom)
        divider.draw(canvas)
    }
}
