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
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.behavior.HideBottomViewOnScrollBehavior

/*
 * This custom behavior for the bottom omnibar is necessary because the default `HideBottomViewOnScrollBehavior` does not work.
 * The reason is that the `DuckDuckGoWebView` is passing only unconsumed movement, which `HideBottomViewOnScrollBehavior` ignores.
 */
class BottomAppBarBehavior<V : View>(context: Context, attrs: AttributeSet?) : HideBottomViewOnScrollBehavior<V>(context, attrs) {
    override fun onNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        target: View,
        dxConsumed: Int,
        dyConsumed: Int,
        dxUnconsumed: Int,
        dyUnconsumed: Int,
        type: Int,
        consumed: IntArray,
    ) {
        if (dyConsumed > 0 || dyUnconsumed > 0) {
            slideDown(child)
        } else if (dyConsumed < 0 || dyUnconsumed < 0) {
            slideUp(child)
        }
    }
}
