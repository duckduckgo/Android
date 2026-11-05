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

package com.duckduckgo.common.ui.view

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import com.duckduckgo.common.ui.view.button.DaxButtonPrimary
import com.duckduckgo.common.ui.view.button.DaxButtonSecondary
import com.duckduckgo.mobile.android.R

internal fun rmfPrimaryButtonWidth(width: Int, primary: Int, secondary: Int, gap: Int): Int? {
    val available = width - gap
    if (primary + secondary > available) return null
    if (primary <= available / 2 && secondary <= available / 2) return available - available / 2
    return available - secondary
}

internal class RmfMessageActions(context: Context) : ViewGroup(context) {
    private val buttonContext = ContextThemeWrapper(context, R.style.ThemeOverlay_Rebrand)
    val primary = DaxButtonPrimary(buttonContext, null).apply { id = R.id.primaryActionButton }
    val secondary = DaxButtonSecondary(buttonContext, null).apply {
        id = R.id.secondaryActionButton
        minWidth = 0
        minimumWidth = 0
    }
    private val gap = resources.getDimensionPixelSize(R.dimen.keyline_2)
    private var primaryWidth: Int? = null

    init {
        addView(primary)
        addView(secondary)
        listOf(primary, secondary).forEach { button ->
            button.setSingleLine(false)
            button.maxLines = Int.MAX_VALUE
            button.ellipsize = null
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val buttons = listOf(primary, secondary).filter { it.isVisible }
        buttons.forEach { it.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED) }
        val touchTarget = resources.getDimensionPixelSize(R.dimen.keyline_7)
        if (secondary.isVisible && secondary.measuredWidth < touchTarget) {
            secondary.measure(MeasureSpec.makeMeasureSpec(touchTarget, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED)
        }
        val width = resolveSize(buttons.sumOf { it.measuredWidth } + if (buttons.size == 2) gap else 0, widthMeasureSpec)
        primaryWidth = if (buttons.size == 2) rmfPrimaryButtonWidth(width, primary.measuredWidth, secondary.measuredWidth, gap) else width
        buttons.forEach { button ->
            val buttonWidth = when {
                buttons.size != 2 || primaryWidth == null -> width
                button === primary -> primaryWidth!!
                else -> width - gap - primaryWidth!!
            }
            button.measure(MeasureSpec.makeMeasureSpec(buttonWidth, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED)
        }
        // Small ADS buttons include 4dp above and below their 40dp surface, providing the stacked 8dp gap.
        val height = if (primaryWidth == null) buttons.sumOf { it.measuredHeight } else buttons.maxOfOrNull { it.measuredHeight } ?: 0
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val buttons = listOf(primary, secondary).filter { it.isVisible }
        if (buttons.size == 2 && primaryWidth != null) {
            val secondaryLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) width - secondary.measuredWidth else 0
            val primaryLeft = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) 0 else width - primary.measuredWidth
            secondary.layout(secondaryLeft, 0, secondaryLeft + secondary.measuredWidth, secondary.measuredHeight)
            primary.layout(primaryLeft, 0, primaryLeft + primary.measuredWidth, primary.measuredHeight)
        } else {
            var y = 0
            buttons.forEach { button ->
                button.layout(0, y, width, y + button.measuredHeight)
                y += button.measuredHeight
            }
        }
    }
}
