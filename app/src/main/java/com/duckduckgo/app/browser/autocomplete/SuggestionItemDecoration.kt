/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.autocomplete

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.ViewGroup.MarginLayoutParams
import androidx.recyclerview.widget.RecyclerView

class SuggestionItemDecoration(private val divider: Drawable) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        canvas.save()

        val childCount = parent.childCount
        val parentRight = parent.width - parent.paddingRight

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as MarginLayoutParams
            val currentViewType = child.tag
            val nextViewType = if (i + 1 < childCount) parent.getChildAt(i + 1).tag else UNKNOWN

            if (nextViewType == UNKNOWN) {
                continue
            }

            if (currentViewType == SEARCH_ITEM && nextViewType == OTHER_ITEM) {
                drawDivider(canvas, child, params, parentRight)
            }

            if (currentViewType == OTHER_ITEM && nextViewType == SEARCH_ITEM) {
                drawDivider(canvas, child, params, parentRight)
            }
        }

        canvas.restore()
    }

    private fun drawDivider(
        canvas: Canvas,
        child: android.view.View,
        params: MarginLayoutParams,
        parentRight: Int,
    ) {
        val horizontalSize = parentRight
        val verticalSize = child.bottom + params.bottomMargin
        divider.setBounds(0, verticalSize, horizontalSize, verticalSize + divider.intrinsicHeight)
        divider.draw(canvas)
    }

    companion object {
        internal const val SEARCH_ITEM = "SEARCH_ITEM"
        internal const val OTHER_ITEM = "OTHER_ITEM"
        internal const val UNKNOWN = "UNKNOWN"
    }
}
