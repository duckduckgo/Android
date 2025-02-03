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

package com.duckduckgo.app.browser.omnibar

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.utils.extractDomain

/**
 * This is a custom behavior for the trackersBlockedSlidingView and is used only when the omnibar is positioned at the bottom.
 *  We want to slide up the trackersBlockedSlidingView view and make it visible when the omnibar goes down, and slide down
 *  the trackersBlockedSlidingView view and set it to gone when the omnibar goes up.
 */
class TrackersBlockedViewSlideBehavior(
    private val siteLiveData: MutableLiveData<Site>,
    context: Context,
    attrs: AttributeSet? = null,
) : CoordinatorLayout.Behavior<View>(context, attrs) {

    private var bottomOmnibar: OmnibarLayout? = null
    private var gravity: Int? = null
    private var trackers: DaxTextView? = null
    private var website: DaxTextView? = null
    private var trackersBurstAnimationView: LottieAnimationView? = null
    private var browserLayout: View? = null

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        // Avoid any unnecessary operations. Hide the child if it's visible on new tab (the browser layout is gone or scrolling is disabled).
        if (child.isVisible && (browserLayout?.isGone == true || bottomOmnibar?.isOmnibarScrollingEnabled() == false)) {
            child.hide()
            return false
        }

        if (dependency.id == R.id.browserLayout) {
            browserLayout = dependency
        } else if (dependency.id == R.id.trackersBurstAnimationView) {
            trackersBurstAnimationView = dependency as LottieAnimationView
        } else if (dependency.id == R.id.newOmnibarBottom) {
            if (gravity == null) {
                val layoutParams = child.layoutParams as? CoordinatorLayout.LayoutParams
                gravity = layoutParams?.gravity
                child.hide()
            }
            if (bottomOmnibar == null) {
                bottomOmnibar = dependency as OmnibarLayout
                trackers = child.findViewById(R.id.trackers)
                website = child.findViewById(R.id.website)
            }
        }
        return super.layoutDependsOn(parent, child, dependency)
    }

    override fun onNestedPreScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        target: View,
        dx: Int,
        dy: Int,
        consumed: IntArray,
        type: Int,
    ) {
        if (bottomOmnibar?.isOmnibarScrollingEnabled() == true && isSiteProtected()) {
            val translation = bottomOmnibar?.getTranslation() ?: 0f
            val bottomOmnibarHeight = bottomOmnibar?.height ?: 0
            if (translation == 0f || translation < bottomOmnibarHeight || browserLayout?.isGone == true) {
                child.hide()
            } else {
                val site = siteLiveData.value
                trackers?.text = site?.trackerCount.toString()
                website?.text = site?.url?.extractDomain()
                if (trackersBurstAnimationView?.isAnimating == true) {
                    trackersBurstAnimationView?.cancelAnimation()
                }
                child.show()
            }
            child.postOnAnimation {
                super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
            }
        }
    }

    override fun onStartNestedScroll(
        coordinatorLayout: CoordinatorLayout,
        child: View,
        directTargetChild: View,
        target: View,
        axes: Int,
        type: Int,
    ): Boolean {
        if (gravity != Gravity.BOTTOM) {
            return false
        }
        return axes == ViewCompat.SCROLL_AXIS_VERTICAL
    }

    private fun isSiteProtected(): Boolean {
        val site = siteLiveData.value
        val shield = site?.privacyProtection() ?: PrivacyShield.UNKNOWN
        return shield == PrivacyShield.PROTECTED
    }
}
