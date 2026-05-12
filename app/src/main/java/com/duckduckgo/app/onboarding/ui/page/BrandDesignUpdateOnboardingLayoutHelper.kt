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

package com.duckduckgo.app.onboarding.ui.page

import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.widget.NestedScrollView

object BrandDesignUpdateOnboardingLayoutHelper {

    /**
     * Returns whether there is enough vertical space within [rootView] to display both
     * the [dialogView] and the [decorationView] without overlap.
     *
     * With edge-to-edge enabled, the root view spans the full screen including system
     * bar areas. Both the dialog margins (from parent top) and the decoration margins
     * (from parent bottom) are defined relative to the parent edges, so the check
     * compares against [rootView.height] directly — no inset subtraction is needed.
     *
     * @param rootView the root view used to determine available height
     * @param dialogView the dialog whose measured height is checked
     * @param decorationView the optional decoration (e.g. walking Dax animation)
     */
    fun hasSpaceForAnimation(
        rootView: View,
        dialogView: View,
        decorationView: View,
    ): Boolean {
        if (rootView.height == 0) return false
        if (isInScrollableContainer(dialogView, rootView)) return true

        val dialogWidthSpec = View.MeasureSpec.makeMeasureSpec(rootView.width, View.MeasureSpec.AT_MOST)
        val dialogHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        dialogView.measure(dialogWidthSpec, dialogHeightSpec)

        val dialogParams = dialogView.layoutParams as ViewGroup.MarginLayoutParams
        val dialogSpace = dialogView.measuredHeight + dialogParams.topMargin + dialogParams.bottomMargin

        val decorationParams = decorationView.layoutParams as ViewGroup.MarginLayoutParams
        val decorationSpace = decorationView.layoutParams.height + decorationParams.bottomMargin

        return rootView.height >= dialogSpace + decorationSpace
    }

    /**
     * Calculates the height to use for the walking Dax animation based on available space.
     *
     * Returns the height in pixels to apply (clamped between [minHeightPx] and [maxHeightPx]),
     * or null if Dax should be hidden because there is not enough room even at the minimum height.
     *
     * @param rootView the root view used to determine available height
     * @param dialogView the dialog whose measured height is checked
     * @param daxView the walking Dax animation view
     * @param maxHeightPx the maximum allowed height for Dax in pixels
     * @param minHeightPx the minimum required height for Dax in pixels; below this Dax is hidden
     */
    fun calculateWalkingDaxHeight(
        rootView: View,
        dialogView: View,
        daxView: View,
        maxHeightPx: Int,
        minHeightPx: Int,
    ): Int? {
        if (rootView.height == 0) return null
        if (isInScrollableContainer(dialogView, rootView)) return maxHeightPx

        val dialogWidthSpec = View.MeasureSpec.makeMeasureSpec(rootView.width, View.MeasureSpec.AT_MOST)
        val dialogHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        dialogView.measure(dialogWidthSpec, dialogHeightSpec)

        val dialogParams = dialogView.layoutParams as ViewGroup.MarginLayoutParams
        val dialogSpace = dialogView.measuredHeight + dialogParams.topMargin + dialogParams.bottomMargin

        val daxParams = daxView.layoutParams as ViewGroup.MarginLayoutParams
        val availableForDax = rootView.height - dialogSpace - daxParams.bottomMargin

        return when {
            availableForDax < minHeightPx -> null
            else -> availableForDax.coerceAtMost(maxHeightPx)
        }
    }

    private fun isInScrollableContainer(view: View, stopAt: View): Boolean {
        var parent = view.parent
        while (parent != null && parent != stopAt) {
            if (parent is ScrollView || parent is NestedScrollView) return true
            parent = parent.parent
        }
        return false
    }
}
