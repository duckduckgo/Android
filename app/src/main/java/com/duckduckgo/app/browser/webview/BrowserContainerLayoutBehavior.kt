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
import androidx.core.view.isGone
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior

/**
 * A [ScrollingViewBehavior] that additionally observes the position of [BrowserNavigationBarView] or bottom [OmnibarLayout], if present,
 * and applies bottom padding to the target view equal to the visible height of the bottom element.
 *
 * This prevents the bottom element from overlapping with, for example, content found in the web view.
 *
 * Note: [BrowserNavigationBarView] or bottom [OmnibarLayout] will never be children of the coordinator layout at the same time, so they won't be competing for updates:
 * - When top [OmnibarLayout] is used, [BrowserNavigationBarView] is added directly to the coordinator layout.
 * - When bottom [OmnibarLayout] is used, it comes embedded with the [BrowserNavigationBarView].
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
        return dependency.isBrowserNavigationBar() || dependency.isBottomOmnibar() || super.layoutDependsOn(parent, child, dependency)
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean {
        return if (dependency.isBrowserNavigationBar() || dependency.isBottomOmnibar()) {
            val newBottomPadding = if (dependency.isGone) {
                0
            } else {
                dependency.measuredHeight - dependency.translationY.toInt()
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

    private fun View.isBrowserNavigationBar(): Boolean = this is BrowserNavigationBarView
    private fun View.isBottomOmnibar(): Boolean = this is OmnibarLayout && this.omnibarPosition == OmnibarPosition.BOTTOM
}
