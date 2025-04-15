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

package com.duckduckgo.app.browser.webview

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior

/**
 * A [ScrollingViewBehavior] that additionally observes the position of [BrowserNavigationBarView], if present,
 * and applies bottom padding to the target view equal to the visible height of the navigation bar.
 *
 * This prevents the navigation bar from overlapping with, for example, content found in the web view.
 *
 * Note: This behavior is intended for use with the top omnibar. When the bottom omnibar is used,
 * it already includes the navigation bar, so no additional coordination is required.
 */
class BrowserContainerLayoutBehavior(
    context: Context,
    attrs: AttributeSet?,
) : ScrollingViewBehavior(context, attrs) {

    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean {
        return if (dependency is BrowserNavigationBarView) {
            true
        } else {
            super.layoutDependsOn(parent, child, dependency)
        }
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean {
        return if (dependency is BrowserNavigationBarView) {
            val newBottomPadding = if (dependency.isVisible) {
                dependency.measuredHeight - dependency.translationY.toInt()
            } else {
                0
            }
            if (child.paddingBottom != newBottomPadding) {
                child.setPadding(
                    0,
                    0,
                    0,
                    newBottomPadding,
                )
                true
            } else {
                false
            }
        } else {
            super.onDependentViewChanged(parent, child, dependency)
        }
    }
}
