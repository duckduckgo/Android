/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.duckduckgo.app.browser.R
import com.google.android.material.appbar.AppBarLayout

/*
 * This custom behavior prevents the top omnibar from hiding everywhere except for the browser view (i.e. the autocomplete suggestions)
 */
class TopAppBarBehavior(
    context: Context,
    private val omnibar: OmnibarBehaviour,
    attrs: AttributeSet? = null,
) : AppBarLayout.Behavior(context, attrs) {
    override fun layoutDependsOn(parent: CoordinatorLayout, child: AppBarLayout, dependency: View): Boolean {
        if (dependency.id != R.id.browserLayout && dependency.id != R.id.webViewFullScreenContainer) {
            offsetBottomByToolbar(dependency)
        }

        return super.layoutDependsOn(parent, child, dependency)
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: AppBarLayout,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (target.id == R.id.browserWebView) {
            if (omnibar.isOmnibarScrollingEnabled()) {
                super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
            }
        }
    }

    private fun offsetBottomByToolbar(view: View?) {
        val omnibarHeight = omnibar.measuredHeight()
        if (omnibarHeight > 0 && view is View && view.layoutParams is MarginLayoutParams) {
            val layoutParams = view.layoutParams as MarginLayoutParams
            if (layoutParams.bottomMargin != omnibarHeight) {
                layoutParams.bottomMargin = omnibarHeight
                view.postOnAnimation {
                    view.requestLayout()
                }
            }
        }
    }
}
