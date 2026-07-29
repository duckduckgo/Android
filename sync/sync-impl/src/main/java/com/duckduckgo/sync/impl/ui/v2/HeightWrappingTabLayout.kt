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

package com.duckduckgo.sync.impl.ui.v2

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.tabs.TabLayout
import com.google.android.material.R as MaterialR

/**
 * [TabLayout] ignores wrap_content vertically: its onMeasure enforces a fixed 48dp default height
 * by inflating the internal tab strip's minimum height. This variant wraps the tallest tab
 * instead, so the switcher grows and shrinks with its labels under font scaling.
 */
class HeightWrappingTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = MaterialR.attr.tabStyle,
) : TabLayout(context, attrs, defStyleAttr) {

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        var heightSpec = heightMeasureSpec
        val strip = getChildAt(0)
        if (strip != null && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY) {
            // Clear the 48dp minimum a previous measure pass may have set on the strip, then
            // pass an EXACTLY spec of the strip's natural height so super skips its default.
            strip.minimumHeight = 0
            strip.measure(
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            )
            var height = strip.measuredHeight + paddingTop + paddingBottom
            if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.AT_MOST) {
                height = height.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            }
            heightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        }
        super.onMeasure(widthMeasureSpec, heightSpec)
    }
}
