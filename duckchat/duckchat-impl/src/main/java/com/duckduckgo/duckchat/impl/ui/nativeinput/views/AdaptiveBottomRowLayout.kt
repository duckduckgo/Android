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
package com.duckduckgo.duckchat.impl.ui.nativeinput.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.duckduckgo.mobile.android.R as CommonR

/**
 * The bottom row of the native input: a start group (attach, tools) and an end group (pickers, voice, submit).
 * When the two do not fit side by side, [CompactableControl]s inside it are asked to shrink one level at a time,
 * so the submit button always keeps its full label.
 */
class AdaptiveBottomRowLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val minGap = resources.getDimensionPixelSize(CommonR.dimen.keyline_2)
    private var measuring = false

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val groups = (0 until childCount).map(::getChildAt).filter { it.visibility != GONE }
        val controls = collectControls()
        measuring = true
        try {
            if (groups.size == 2 && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED && controls.isNotEmpty()) {
                for (level in LEVEL_FULL..LEVEL_MAX) {
                    controls.forEach { it.setCompactLevel(level) }
                    if (neededWidth(groups, controls, level, heightMeasureSpec) <= available) break
                }
            } else {
                controls.forEach { it.setCompactLevel(LEVEL_FULL) }
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        } finally {
            measuring = false
        }
    }

    // Controls change their own text and visibility while the row is being measured, which asks for a layout
    // the row is already doing. Swallowing it avoids a measure loop.
    override fun requestLayout() {
        if (!measuring) super.requestLayout()
    }

    // What the groups need at [level] if every control took its worst-case width: the measured groups, with each
    // control's current contribution swapped for its worst case. The result does not depend on what is selected,
    // typed or active, so the same screen always gets the same layout.
    private fun neededWidth(
        groups: List<View>,
        controls: List<CompactableControl>,
        level: Int,
        heightMeasureSpec: Int,
    ): Int {
        val measured = groups.sumOf { it.naturalWidth(heightMeasureSpec) }
        val current = controls.sumOf { contributionOf(it as View, groups) }
        val worstCase = controls.sumOf { it.worstCaseWidth(level) }
        return measured - current + worstCase + minGap
    }

    // A control sits in a container that is a direct child of one of the two groups; the container's visibility and
    // margins decide how much room the control takes right now.
    private fun contributionOf(
        control: View,
        groups: List<View>,
    ): Int {
        var container: View = control
        while (groups.none { it === container.parent }) {
            container = container.parent as? View ?: return 0
        }
        if (container.visibility == GONE) return 0
        val params = container.layoutParams as? MarginLayoutParams
        return container.measuredWidth + (params?.marginStart ?: 0) + (params?.marginEnd ?: 0)
    }

    private fun View.naturalWidth(heightMeasureSpec: Int): Int {
        measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        val params = layoutParams as? MarginLayoutParams
        return measuredWidth + (params?.marginStart ?: 0) + (params?.marginEnd ?: 0)
    }

    private fun collectControls(): List<CompactableControl> {
        val found = mutableListOf<CompactableControl>()
        fun walk(view: View) {
            if (view is CompactableControl) found += view
            if (view is ViewGroup) for (i in 0 until view.childCount) walk(view.getChildAt(i))
        }
        walk(this)
        return found
    }

    companion object {
        const val LEVEL_FULL = 0

        /** The model pill is an icon. */
        const val LEVEL_MODEL_ICON = 1

        /** The tools button and the active mode chip are one control. */
        const val LEVEL_MERGED_TOOLS = 2
        const val LEVEL_MAX = LEVEL_MERGED_TOOLS
    }
}
