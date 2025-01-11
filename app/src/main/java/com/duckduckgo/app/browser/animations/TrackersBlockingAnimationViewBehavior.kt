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

package com.duckduckgo.app.browser.animations

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout

class TrackersBlockingAnimationViewBehavior(val context: Context, attrs: AttributeSet? = null) : CoordinatorLayout.Behavior<View>(context, attrs) {

    override fun onLayoutChild(parent: CoordinatorLayout, child: View, layoutDirection: Int): Boolean {
        parent.onLayoutChild(child, layoutDirection)

        val layoutParams = child.layoutParams as? CoordinatorLayout.LayoutParams
        val gravity = layoutParams?.gravity

        // Apply this only when the view is aligned to the bottom, which occurs when the omnibar is positioned there.
        if (gravity == Gravity.BOTTOM) {
            // Offset the view by half of its height and half of the omnibar height.
            val offset = child.height / 2 + context.getActionBarSize() / 2
            child.translationY = offset.toFloat()
        }

        return true
    }

    private fun Context.getActionBarSize(): Int {
        val typedValue = TypedValue()
        if (theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
            return TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
        }
        return 0
    }
}
