/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.browser.ui.autocomplete

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.withSave
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.common.ui.view.toPx

class SuggestionItemDecoration(private val divider: Drawable) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(
        canvas: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        canvas.withSave {
            val childCount = parent.childCount
            val parentRight = parent.width - parent.paddingRight

            for (i in 0 until childCount - 1) {
                val currentView = parent.getChildAt(i)
                val currentParams = currentView.layoutParams as ViewGroup.MarginLayoutParams
                val currentViewType = currentView.tag

                val nextView = parent.getChildAt(i + 1)
                val nextViewType = nextView.tag
                val nextViewParams = nextView.layoutParams as ViewGroup.MarginLayoutParams

                currentView.updateLayoutParams {
                    currentParams.apply {
                        if (i == 0) {
                            topMargin = 0
                        }
                        bottomMargin = 0
                    }
                }
                nextView.updateLayoutParams {
                    nextViewParams.apply {
                        topMargin = 0
                        if (i == childCount - 1) {
                            bottomMargin = 0
                        }
                    }
                }
                if (currentViewType == SEARCH_ITEM && nextViewType == OTHER_ITEM || currentViewType == OTHER_ITEM && nextViewType == SEARCH_ITEM) {
                    currentView.updateLayoutParams {
                        currentParams.apply {
                            bottomMargin = DIVIDER_PADDING_DP.toPx()
                        }
                    }
                    nextView.updateLayoutParams {
                        nextViewParams.apply {
                            topMargin = DIVIDER_PADDING_DP.toPx()
                        }
                    }
                    drawDivider(this, currentView, currentParams, parentRight)
                }
            }
        }
    }

    private fun drawDivider(
        canvas: Canvas,
        child: View,
        params: ViewGroup.MarginLayoutParams,
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

        internal const val DIVIDER_PADDING_DP = 8
    }
}
