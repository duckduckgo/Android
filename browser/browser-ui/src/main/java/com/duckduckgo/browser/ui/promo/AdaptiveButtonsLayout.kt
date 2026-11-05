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

package com.duckduckgo.browser.ui.promo

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.core.view.children
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.toPx

/**
 * Lays out the buttons horizontally in equal widths, or vertically at full width when any label doesn't fit
 * on one line in its share.
 */
internal class AdaptiveButtonsLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ViewGroup(context, attrs, defStyleAttr) {
    private val horizontalGap = 8.toPx(context)
    private var isVertical = false

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val buttons = visibleButtons()
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val contentWidth = width - paddingLeft - paddingRight
        val horizontalWidth = if (buttons.isEmpty()) contentWidth else (contentWidth - horizontalGap * (buttons.size - 1)) / buttons.size

        isVertical = buttons.any { it.singleLineWidth() > horizontalWidth }
        val contentHeight = if (isVertical) {
            measureVertically(buttons.asReversed(), contentWidth)
        } else {
            measureHorizontally(buttons, horizontalWidth)
        }
        setMeasuredDimension(width, resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec))
    }

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        val buttons = visibleButtons()
        if (isVertical) {
            layoutVertically(buttons.asReversed())
        } else {
            layoutHorizontally(if (layoutDirection == LAYOUT_DIRECTION_RTL) buttons.asReversed() else buttons)
        }
    }

    private fun measureHorizontally(
        buttons: List<View>,
        buttonWidth: Int,
    ): Int {
        buttons.forEach { it.measure(exactly(buttonWidth), unspecified()) }
        // Remeasure at the tallest height so the buttons line up even when their content heights differ.
        val maxHeight = buttons.maxOfOrNull { it.measuredHeight } ?: 0
        buttons.forEach { it.measure(exactly(buttonWidth), exactly(maxHeight)) }
        return maxHeight
    }

    private fun layoutHorizontally(buttons: List<View>) {
        var x = paddingLeft
        buttons.forEach { button ->
            button.layout(x, paddingTop, x + button.measuredWidth, paddingTop + button.measuredHeight)
            x += button.measuredWidth + horizontalGap
        }
    }

    private fun measureVertically(
        buttons: List<View>,
        buttonWidth: Int,
    ): Int {
        buttons.forEach { it.measure(exactly(buttonWidth), unspecified()) }
        val height = buttons.sumOf { it.measuredHeight }
        val verticalGaps = buttons.zipWithNext { above, below -> verticalGap(above, below) }.sum()
        return height + verticalGaps
    }

    private fun layoutVertically(buttons: List<View>) {
        var y = paddingTop
        buttons.forEachIndexed { index, button ->
            button.layout(paddingLeft, y, paddingLeft + button.measuredWidth, y + button.measuredHeight)
            buttons.getOrNull(index + 1)?.let { below -> y += button.measuredHeight + verticalGap(button, below) }
        }
    }

    // We subtract the buttons' transparent vertical insets so the visible gap matches the horizontal one.
    private fun verticalGap(above: View, below: View): Int {
        val bottomInset = (above as? DaxButton)?.insetBottom ?: 0
        val topInset = (below as? DaxButton)?.insetTop ?: 0
        return (horizontalGap - bottomInset - topInset).coerceAtLeast(0)
    }

    private fun visibleButtons(): List<View> = children.filter { it.isVisible }.toList()

    private fun View.singleLineWidth(): Int {
        measure(unspecified(), unspecified())
        return measuredWidth
    }

    private fun exactly(size: Int) = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)

    private fun unspecified() = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
}
