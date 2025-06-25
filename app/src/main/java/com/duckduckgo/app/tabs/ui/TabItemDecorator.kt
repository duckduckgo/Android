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
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.NormalTab
import com.duckduckgo.app.tabs.ui.TabSwitcherItem.Tab.SelectableTab
import com.duckduckgo.common.ui.experiments.visual.store.NewDesignDataStore
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

class TabItemDecorator(context: Context, experimentStore: NewDesignDataStore) : RecyclerView.ItemDecoration() {
    private val isNewDesignEnabled = experimentStore.isSplitOmnibarEnabled.value || experimentStore.isNewDesignEnabled.value
    private val borderPadding = if (isNewDesignEnabled) BORDER_PADDING_NEW else BORDER_PADDING
    private val activeTabBorderColor = if (isNewDesignEnabled) {
        CommonR.attr.daxColorTabHighlight
    } else {
        CommonR.attr.daxColorBackgroundInverted
    }
    private val selectionBorderWidth = if (isNewDesignEnabled) {
        ACTIVE_TAB_BORDER_WIDTH
    } else {
        SELECTION_BORDER_WIDTH
    }

    private val activeTabBorderStroke: Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = ACTIVE_TAB_BORDER_WIDTH

        val typedValue = TypedValue()
        context.theme.resolveAttribute(activeTabBorderColor, typedValue, true)
        color = ContextCompat.getColor(context, typedValue.resourceId)
    }

    private val selectionBorderStroke: Paint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = selectionBorderWidth

        val typedValue = TypedValue()
        context.theme.resolveAttribute(CommonR.attr.daxColorAccentBlue, typedValue, true)
        color = ContextCompat.getColor(context, typedValue.resourceId)
    }

    override fun onDrawOver(
        canvas: Canvas,
        recyclerView: RecyclerView,
        state: RecyclerView.State,
    ) {
        val adapter = recyclerView.adapter as? TabSwitcherAdapter ?: return
        recyclerView.children.forEach { child ->
            val positionInAdapter = recyclerView.getChildAdapterPosition(child)
            adapter.getTabSwitcherItem(positionInAdapter)?.let { tabSwitcherItem ->
                when {
                    tabSwitcherItem is SelectableTab && tabSwitcherItem.isSelected -> {
                        drawTabDecoration(child, canvas, selectionBorderStroke)
                    }
                    tabSwitcherItem is NormalTab && tabSwitcherItem.isActive -> {
                        drawTabDecoration(child, canvas, activeTabBorderStroke)
                    }
                    tabSwitcherItem is TabSwitcherItem.TrackerAnimationInfoPanel -> Unit // No border for animation tile
                }
            }
        }

        super.onDrawOver(canvas, recyclerView, state)
    }

    private fun drawTabDecoration(child: View, c: Canvas, paint: Paint) {
        selectionBorderStroke.alpha = (child.alpha * 255).toInt()
        c.drawRoundRect(child.getBounds(), BORDER_RADIUS, BORDER_RADIUS, paint)
    }

    private fun View.getBounds(): RectF {
        val leftPosition = left + translationX - paddingLeft - borderPadding
        val rightPosition = right + translationX + paddingRight + borderPadding

        val topPosition = top + translationY - paddingTop - borderPadding
        val bottomPosition = bottom + translationY + paddingBottom + borderPadding

        return RectF(leftPosition, topPosition, rightPosition, bottomPosition)
    }

    companion object {
        private val BORDER_RADIUS = 12.toPx().toFloat()
        private val ACTIVE_TAB_BORDER_WIDTH = 2.toPx().toFloat()
        private val SELECTION_BORDER_WIDTH = 4.toPx().toFloat()
        private val BORDER_PADDING = 3.toPx().toFloat()
        private val BORDER_PADDING_NEW = 1.toPx().toFloat()
    }
}
