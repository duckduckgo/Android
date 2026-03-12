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
import androidx.coordinatorlayout.widget.CoordinatorLayout.Behavior
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.app.browser.omnibar.OmnibarView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.AppBarLayout.ScrollingViewBehavior
import kotlin.math.max
import kotlin.math.min

/**
 * A [ScrollingViewBehavior] that observes [AppBarLayout] (top omnibar) present in the view hierarchy and applies top offset to the child view
 * equal to the visible height of the omnibar.
 *
 * This extension additionally observes the position of [BrowserNavigationBarView], if present and a sibling of the target child in the [CoordinatorLayout],
 * and applies bottom padding to the target child equal to the visible height of the navigation bar.
 *
 * This prevents the omnibar and the navigation bar from overlapping with, for example, content found in the web view.
 *
 * Note: If bottom [OmnibarLayout] is used ([OmnibarType.SINGLE_BOTTOM]), [BottomOmnibarBrowserContainerLayoutBehavior] should be set to the target child.
 */
class TopOmnibarBrowserContainerLayoutBehavior(
    context: Context,
    attrs: AttributeSet?,
) : ScrollingViewBehavior(context, attrs) {
    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean = dependency.isBrowserNavigationBar() || super.layoutDependsOn(parent, child, dependency)

    /**
     * Adds the child's bottom margin to the available height before the super measures it.
     *
     * [ScrollingViewBehavior] adds the AppBarLayout's [totalScrollRange][AppBarLayout.getTotalScrollRange] to the
     * available height so the child can extend past the parent while the bar is expanded.
     * However, the subsequent [measureChildWithMargins][android.view.ViewGroup.measureChildWithMargins] call
     * subtracts the child's bottom margin, which cancels out the scroll-range addition and leaves the child
     * too short to reach the parent's bottom when the AppBarLayout is fully collapsed.
     *
     * By inflating the parent spec by the bottom margin before handing it to the super, the final
     * measured height is large enough that `child.top + child.height >= parent.height` at every
     * scroll position.
     */
    override fun onMeasureChild(
        parent: CoordinatorLayout,
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int,
    ): Boolean {
        val lp = child.layoutParams as? CoordinatorLayout.LayoutParams
        val bottomMargin = lp?.bottomMargin ?: 0
        if (bottomMargin > 0) {
            val adjustedHeightSpec = View.MeasureSpec.makeMeasureSpec(
                View.MeasureSpec.getSize(parentHeightMeasureSpec) + bottomMargin,
                View.MeasureSpec.getMode(parentHeightMeasureSpec),
            )
            return super.onMeasureChild(parent, child, parentWidthMeasureSpec, widthUsed, adjustedHeightSpec, heightUsed)
        }
        return super.onMeasureChild(parent, child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
    }

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean =
        if (dependency.isBrowserNavigationBar()) {
            offsetByBottomElementVisibleHeight(child = child, dependency = dependency)
        } else {
            val result = super.onDependentViewChanged(parent, child, dependency)
            adjustBottomPaddingForEdgeToEdge(parent, child, dependency)
            result
        }

    /**
     * Dynamically adjusts bottom padding for edge-to-edge navigation bar insets.
     *
     * When the [BrowserNavigationBarView] is gone (SINGLE_TOP mode), the child needs bottom padding
     * to keep content above the system navigation bar. However, static padding scrolls into view
     * as the AppBarLayout collapses. This reduces the padding proportionally to the scroll amount,
     * so it's fully present when expanded and gone when collapsed — preventing any visible artifact
     * behind the translucent navigation bar during scroll.
     */
    private fun adjustBottomPaddingForEdgeToEdge(parent: CoordinatorLayout, child: View, dependency: View) {
        if (child.isGone) return
        // Only apply when there's no BrowserNavigationBarView handling insets
        val hasVisibleNavBar = parent.getDependencies(child).any { it.isBrowserNavigationBar() && !it.isGone }
        if (hasVisibleNavBar) return

        val navBarInsets = ViewCompat.getRootWindowInsets(parent)
            ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
        if (navBarInsets == 0) return

        val appBarLayout = dependency as? AppBarLayout ?: return
        val scrollRange = appBarLayout.totalScrollRange
        val scrollOffset = -dependency.top // how much the AppBarLayout has scrolled (0 = expanded)
        val fraction = if (scrollRange > 0) min(1f, scrollOffset.toFloat() / scrollRange) else 0f
        val newPadding = max(0, (navBarInsets * (1f - fraction)).toInt())

        if (child.paddingBottom != newPadding) {
            child.setPadding(child.paddingLeft, child.paddingTop, child.paddingRight, newPadding)
        }
    }
}

/**
 * A behavior that observes the position of the bottom [OmnibarLayout] ([OmnibarType.SINGLE_BOTTOM]), if present,
 * and applies bottom padding to the target view equal to the visible height of the omnibar.
 *
 * This prevents the omnibar from overlapping with, for example, content found in the web view.
 *
 * We can't use the [ScrollingViewBehavior] because it relies on the [AppBarLayout] and always forcefully places the target child _below_ the bar,
 * which doesn't work if the bar is at the bottom.
 *
 * We don't need to additionally observe the position of the [BrowserNavigationBarView] when bottom [OmnibarLayout] is used because it comes pre-embedded with the navigation bar.
 *
 * Note: If top [OmnibarLayout] is used ([OmnibarType.SINGLE_TOP] or [OmnibarType.SPLIT]), [TopOmnibarBrowserContainerLayoutBehavior] should be set to the target child.
 */
class BottomOmnibarBrowserContainerLayoutBehavior : Behavior<View>() {
    override fun layoutDependsOn(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean = dependency.isBottomOmnibar() || super.layoutDependsOn(parent, child, dependency)

    override fun onDependentViewChanged(
        parent: CoordinatorLayout,
        child: View,
        dependency: View,
    ): Boolean =
        if (dependency.isBottomOmnibar()) {
            offsetByBottomElementVisibleHeight(child = child, dependency = dependency)
        } else {
            super.onDependentViewChanged(parent, child, dependency)
        }
}

private fun offsetByBottomElementVisibleHeight(
    child: View,
    dependency: View,
): Boolean {
    val newBottomPadding =
        if (dependency.isGone) {
            0
        } else {
            dependency.measuredHeight - dependency.translationY.toInt()
        }
    return if (child.paddingBottom != newBottomPadding) {
        child.setPadding(
            child.paddingLeft,
            child.paddingTop,
            child.paddingRight,
            newBottomPadding,
        )
        true
    } else {
        false
    }
}

private fun View.isBrowserNavigationBar(): Boolean = this is BrowserNavigationBarView

private fun View.isBottomOmnibar(): Boolean = this is OmnibarView && this.omnibarType == OmnibarType.SINGLE_BOTTOM
