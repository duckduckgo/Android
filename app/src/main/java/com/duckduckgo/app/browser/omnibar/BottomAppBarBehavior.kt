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

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.NestedScrollType
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import com.google.android.material.snackbar.Snackbar
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 * This custom behavior for the bottom omnibar is necessary because the default `HideBottomViewOnScrollBehavior` does not work.
 * The reason is that the `DuckDuckGoWebView` is passing only unconsumed movement, which `HideBottomViewOnScrollBehavior` ignores.
 */
class BottomAppBarBehavior<V : View>(
    context: Context,
    private val omnibar: LegacyOmnibarView,
    attrs: AttributeSet? = null,
) : CoordinatorLayout.Behavior<V>(context, attrs) {
    @NestedScrollType
    private var lastStartedType: Int = 0
    private var offsetAnimator: ValueAnimator? = null

    private var browserLayout: RelativeLayout? = null

    @SuppressLint("RestrictedApi")
    override fun layoutDependsOn(parent: CoordinatorLayout, child: V, dependency: View): Boolean {
        if (dependency is Snackbar.SnackbarLayout) {
            updateSnackbar(child, dependency)
        }

        if (dependency.id == R.id.browserLayout) {
            browserLayout = dependency as RelativeLayout
        }

        offsetBottomByToolbar(dependency)

        return super.layoutDependsOn(parent, child, dependency)
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: V,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        if (axes == ViewCompat.SCROLL_AXIS_VERTICAL) {
            lastStartedType = type
            offsetAnimator?.cancel()
            return true
        } else {
            return false
        }
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        toolbar: V,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (omnibar.isScrollingEnabled) {
            super.onNestedPreScroll(coordinatorLayout, toolbar, target, dx, dy, consumed, type)

            // only hide the app bar in the browser layout
            if (target.id == R.id.browserWebView) {
                toolbar.translationY = max(0f, min(toolbar.height.toFloat(), toolbar.translationY + dy))
            }

            offsetBottomByToolbar(target)
        }
    }

    private fun offsetBottomByToolbar(view: View?) {
        if (view?.layoutParams is CoordinatorLayout.LayoutParams) {
            view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                this.bottomMargin = omnibar.measuredHeight - omnibar.translationY.roundToInt()
            }
            view.requestLayout()
        }
    }

    override fun onStopNestedScroll(coordinatorLayout: CoordinatorLayout, child: V, target: View, type: Int) {
        if (lastStartedType == ViewCompat.TYPE_TOUCH || type == ViewCompat.TYPE_NON_TOUCH) {
            val dY = child.translationY
            val threshold = child.height * 0.5f
            if (dY >= threshold) {
                // slide down
                animateToolbarVisibility(isVisible = false)
            } else {
                // slide up
                animateToolbarVisibility(isVisible = true)
            }
        }
    }

    fun animateToolbarVisibility(isVisible: Boolean) {
        if (offsetAnimator == null) {
            offsetAnimator = ValueAnimator().apply {
                interpolator = DecelerateInterpolator()
                duration = 300L
            }
        } else {
            offsetAnimator?.cancel()
        }

        offsetAnimator?.addUpdateListener { animation ->
            val animatedValue = animation.animatedValue as Float
            omnibar.translationY = animatedValue
            offsetBottomByToolbar(browserLayout)
        }

        val targetTranslation = if (isVisible) 0f else omnibar.height.toFloat()
        offsetAnimator?.setFloatValues(omnibar.translationY, targetTranslation)
        offsetAnimator?.start()
    }

    @SuppressLint("RestrictedApi")
    private fun updateSnackbar(child: View, snackbarLayout: Snackbar.SnackbarLayout) {
        if (snackbarLayout.layoutParams is CoordinatorLayout.LayoutParams) {
            val params = snackbarLayout.layoutParams as CoordinatorLayout.LayoutParams

            params.anchorId = child.id
            params.anchorGravity = Gravity.TOP
            params.gravity = Gravity.TOP
            snackbarLayout.layoutParams = params

            // add a padding to the snackbar to avoid it touching the anchor view
            if (snackbarLayout.translationY == 0f) {
                snackbarLayout.translationY -= child.context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.keyline_2)
            }
        }
    }
}
