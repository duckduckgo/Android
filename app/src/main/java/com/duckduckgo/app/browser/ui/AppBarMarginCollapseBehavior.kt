/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.browser.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import com.google.android.material.appbar.AppBarLayout

class AppBarMarginCollapseBehavior : AppBarLayout.ScrollingViewBehavior {
    constructor() : super()

    constructor(context: Context, attrs: AttributeSet): super(context, attrs)

    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int
    ): Boolean {
        // Equivalent to HeaderScrollingViewBehavior.onMeasureChild, except for slightly different
        // handling of measured header height and window insets here

        val childLpHeight = child.layoutParams.height
        if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT
            || childLpHeight == ViewGroup.LayoutParams.WRAP_CONTENT
        ) {
            // If the menu's height is set to match_parent/wrap_content then measure it
            // with the maximum visible height
            val dependencies = parent.getDependencies(child)
            val header: View? = findFirstDependency(dependencies)
            if (header != null) {
                var availableHeight = View.MeasureSpec.getSize(parentHeightMeasureSpec)
                if (availableHeight <= 0) {
                    // If the measure spec doesn't specify a size, use the current height
                    availableHeight = parent.height
                }
                var height = availableHeight
                // Reduce measured size by the size of the visible part of the header
                val headerHeight = header.measuredHeight + header.top
                if (shouldHeaderOverlapScrollingChild()) {
                    child.translationY = -headerHeight.toFloat()
                } else {
                    height -= headerHeight
                }
                val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(
                    height,
                    if (childLpHeight == ViewGroup.LayoutParams.MATCH_PARENT) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
                )

                // Now measure the scrolling view with the correct height
                parent.onMeasureChild(
                    child, parentWidthMeasureSpec, widthUsed, heightMeasureSpec, heightUsed
                )
                return true
            }
        }
        return false
    }

    fun findFirstDependency(views: List<View?>): AppBarLayout? {
        for (view in views) {
            if (view is AppBarLayout) {
                return view
            }
        }
        return null
    }
}