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
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.mobile.android.ui.view.toPx

class FeedbackItemDecoration(private val divider: Drawable) : RecyclerView.ItemDecoration() {

    override fun onDrawOver(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val childCount = parent.childCount
        val parentRight = parent.width - parent.paddingRight

        for (i in 0 until childCount) {
            val child = parent.getChildAt(i)
            val params = child.layoutParams as MarginLayoutParams

            drawBottomDivider(canvas, parent, child, params, i, parentRight)

            if (isFirstItem(i)) {
                drawTopDivider(canvas, child, params, parentRight)
            }
        }
    }

    private fun drawTopDivider(
        canvas: Canvas,
        child: View,
        params: MarginLayoutParams,
        parentRight: Int
    ) {
        val horizontalSize = horizontalSizeForFullWidthDivider(parentRight)
        val verticalSize = verticalSizeForTopDivider(child, params)
        drawDivider(canvas, horizontalSize, verticalSize)
    }

    private fun drawBottomDivider(
        canvas: Canvas,
        parent: RecyclerView,
        child: View,
        params: MarginLayoutParams,
        i: Int,
        parentRight: Int
    ) {
        val verticalSize = verticalSizeForBottomDivider(child, params)
        val horizontalSize = determineDividerWidth(i, parent, parentRight)
        drawDivider(canvas, horizontalSize, verticalSize)
    }

    private fun determineDividerWidth(
        i: Int,
        parent: RecyclerView,
        parentRight: Int
    ): HorizontalSize {
        return if (isLastItem(i, parent)) {
            horizontalSizeForFullWidthDivider(parentRight)
        } else {
            horizontalSizeForPartialWidthDivider(parent, parentRight)
        }
    }

    private fun drawDivider(
        canvas: Canvas,
        horizontalSize: HorizontalSize,
        verticalSize: VerticalSize
    ) {
        divider.setBounds(
            horizontalSize.left, verticalSize.top, horizontalSize.right, verticalSize.bottom)
        divider.draw(canvas)
    }

    private fun horizontalSizeForFullWidthDivider(right: Int): HorizontalSize =
        HorizontalSize(left = 0, right = right)

    private fun horizontalSizeForPartialWidthDivider(
        parent: RecyclerView,
        right: Int
    ): HorizontalSize {
        return HorizontalSize(left = parent.paddingLeft + INDENTATION_SIZE_DP.toPx(), right = right)
    }

    private fun verticalSizeForTopDivider(child: View, params: MarginLayoutParams): VerticalSize {
        val top = child.top + params.topMargin
        val bottom = top + divider.intrinsicHeight
        return VerticalSize(top = top, bottom = bottom)
    }

    private fun verticalSizeForBottomDivider(
        child: View,
        params: MarginLayoutParams
    ): VerticalSize {
        val top = child.bottom + params.bottomMargin
        val bottom = top + divider.intrinsicHeight
        return VerticalSize(top = top, bottom = bottom)
    }

    private data class HorizontalSize(val left: Int, val right: Int)

    private data class VerticalSize(val top: Int, val bottom: Int)

    private fun isFirstItem(position: Int) = position == 0

    private fun isLastItem(position: Int, parent: RecyclerView) =
        (position + 1) == parent.totalItemCount()

    private fun RecyclerView.totalItemCount() = adapter?.itemCount

    companion object {
        private const val INDENTATION_SIZE_DP = 22
    }
}
