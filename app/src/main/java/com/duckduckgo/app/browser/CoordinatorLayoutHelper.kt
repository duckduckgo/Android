/*
 * Copyright (c) 2023 DuckDuckGo
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
 *
 * Code from: https://github.com/Telefonica/android-nested-scroll-webview/blob/main/nestedscrollwebview/src/main/java/com/telefonica/nestedscrollwebview/helper/CoordinatorLayoutChildHelper.kt
 */

package com.duckduckgo.app.browser

import android.view.View
import android.view.ViewParent
import androidx.coordinatorlayout.widget.CoordinatorLayout

class CoordinatorLayoutHelper {

    private var lastYPosition: Int? = null
    private var coordinatorChildView: View? = null
    private var coordinatorParentView: CoordinatorLayout? = null

    private var isBottomMatchingBehaviourEnabled = false

    fun onViewAttached(view: View) {
        lastYPosition = null
        coordinatorChildView = null
        coordinatorParentView = null

        var childView: View? = view
        while (childView?.parent is View && coordinatorParentView == null) {
            when (val viewParent: ViewParent = childView.parent) {
                is CoordinatorLayout -> {
                    coordinatorParentView = viewParent
                    coordinatorChildView = childView
                }
                is View ->
                    childView = viewParent
                else ->
                    childView = null
            }
        }
    }

    fun setBottomMatchingBehaviourEnabled(enabled: Boolean) {
        if (isBottomMatchingBehaviourEnabled && !enabled) {
            lastYPosition = null
            resetBottomMargin()
        }
        isBottomMatchingBehaviourEnabled = enabled
        computeBottomMarginIfNeeded()
    }

    fun computeBottomMarginIfNeeded() {
        if (coordinatorChildView == null || coordinatorParentView == null || !isBottomMatchingBehaviourEnabled) {
            return
        }

        val childBounds = IntArray(2)
        coordinatorChildView!!.getLocationOnScreen(childBounds)
        if (childBounds[1] != lastYPosition) {
            val childBottom = childBounds[1] + coordinatorChildView!!.height
            lastYPosition = childBounds[1]

            val parentBounds = IntArray(2)
            coordinatorParentView!!.getLocationOnScreen(parentBounds)
            val parentBottom = parentBounds[1] + coordinatorParentView!!.height

            val diff = childBottom - parentBottom
            if (diff != 0) {
                with(coordinatorChildView!!.layoutParams as CoordinatorLayout.LayoutParams) {
                    bottomMargin += diff
                    coordinatorChildView!!.layoutParams = this
                }
            }
        }
    }

    private fun resetBottomMargin() {
        coordinatorChildView?.let { childView ->
            with(childView.layoutParams as CoordinatorLayout.LayoutParams) {
                bottomMargin = 0
                childView.layoutParams = this
            }
        }
    }
}
