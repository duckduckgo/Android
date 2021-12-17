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

package com.duckduckgo.app.tabs.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.ui.view.toPx

class TabGridItemDecorator(context: Context, var selectedTabId: String?) :
    RecyclerView.ItemDecoration() {

    private val borderStroke: Paint =
        Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = BORDER_WIDTH

            val typedValue = TypedValue()
            context.theme.resolveAttribute(R.attr.normalTextColor, typedValue, true)
            color = ContextCompat.getColor(context, typedValue.resourceId)
        }

    override fun onDrawOver(canvas: Canvas, recyclerView: RecyclerView, state: RecyclerView.State) {
        val adapter = recyclerView.adapter as TabSwitcherAdapter? ?: return

        for (i in 0 until recyclerView.childCount) {
            val child = recyclerView.getChildAt(i)

            val positionInAdapter = recyclerView.getChildAdapterPosition(child)
            if (positionInAdapter < 0) {
                continue
            }

            val tab = adapter.getTab(positionInAdapter)

            if (tab.tabId == selectedTabId) {
                drawSelectedTabDecoration(child, canvas)
            }
        }

        super.onDrawOver(canvas, recyclerView, state)
    }

    private fun drawSelectedTabDecoration(child: View, c: Canvas) {
        borderStroke.alpha = (child.alpha * 255).toInt()
        c.drawRoundRect(child.getBounds(), BORDER_RADIUS, BORDER_RADIUS, borderStroke)
    }

    private fun View.getBounds(): RectF {
        val leftPosition = left + translationX - paddingLeft - BORDER_PADDING
        val rightPosition = right + translationX + paddingRight + BORDER_PADDING

        val topPosition = top + translationY - paddingTop - BORDER_PADDING
        val bottomPosition = bottom + translationY + paddingBottom + BORDER_PADDING

        return RectF(leftPosition, topPosition, rightPosition, bottomPosition)
    }

    companion object {
        private val BORDER_RADIUS = 9.toPx().toFloat()
        private val BORDER_WIDTH = 2.toPx().toFloat()
        private val BORDER_PADDING = 3.toPx().toFloat()
    }
}
