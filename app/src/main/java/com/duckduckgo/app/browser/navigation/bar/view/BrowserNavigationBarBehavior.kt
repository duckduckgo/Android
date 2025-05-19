/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.navigation.bar.view

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior
import com.duckduckgo.app.browser.omnibar.experiments.FadeOmnibarLayout
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition.TOP
import kotlin.math.abs

/**
 * Behavior that offsets the navigation bar proportionally to the offset of the top omnibar.
 *
 * This practically applies only when paired with the top omnibar because if the bottom omnibar is used, it comes with the navigation bar embedded.
 */
class BrowserNavigationBarBehavior(
    context: Context,
    attrs: AttributeSet?,
) : Behavior<View>(context, attrs) {

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean {
        return dependency is FadeOmnibarLayout && dependency.omnibarPosition == TOP
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean {
        if (dependency is FadeOmnibarLayout && dependency.omnibarPosition == TOP) {
            val dependencyOffset = abs(dependency.top)
            val offsetPercentage = dependencyOffset.toFloat() / dependency.measuredHeight.toFloat()
            val childHeight = child.measuredHeight
            val childOffset = childHeight * offsetPercentage
            child.translationY = childOffset
            return true
        }
        return false
    }
}
