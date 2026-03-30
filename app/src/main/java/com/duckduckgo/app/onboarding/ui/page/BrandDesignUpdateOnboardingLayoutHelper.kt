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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView

object BrandDesignUpdateOnboardingLayoutHelper {

    /**
     * Returns whether there is enough vertical screen space to display both
     * the [dialogView] and the [decorationView] without overlapping the system bars.
     *
     * @param rootView the root view used to determine available height and insets
     * @param dialogView the dialog whose measured height is checked
     * @param decorationView the optional decoration (e.g. walking Dax animation)
     */
    fun hasSpaceForAnimation(
        rootView: View,
        dialogView: View,
        decorationView: View,
    ): Boolean {
        if (rootView.height == 0) return true
        if (isInScrollableContainer(dialogView, rootView)) return true

        val insets = ViewCompat.getRootWindowInsets(rootView)
        val topInset = insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val bottomInset = insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        val availableHeight = rootView.height - topInset - bottomInset

        val dialogWidthSpec = View.MeasureSpec.makeMeasureSpec(rootView.width, View.MeasureSpec.AT_MOST)
        val dialogHeightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        dialogView.measure(dialogWidthSpec, dialogHeightSpec)

        val dialogParams = dialogView.layoutParams as ViewGroup.MarginLayoutParams
        val dialogSpace = dialogView.measuredHeight + dialogParams.topMargin + dialogParams.bottomMargin

        val decorationParams = decorationView.layoutParams as ViewGroup.MarginLayoutParams
        val decorationSpace = decorationView.layoutParams.height + decorationParams.bottomMargin

        return availableHeight >= dialogSpace + decorationSpace
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
