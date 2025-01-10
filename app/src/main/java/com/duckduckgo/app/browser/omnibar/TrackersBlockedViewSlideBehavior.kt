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
import androidx.lifecycle.MutableLiveData
import com.duckduckgo.app.browser.R
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

    override fun layoutDependsOn(parent: CoordinatorLayout, child: View, dependency: View): Boolean {
        if (dependency.id == R.id.newOmnibarBottom) {
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
        if (bottomOmnibar?.isOmnibarScrollingEnabled() == true) {
            val translation = bottomOmnibar?.getTranslation() ?: 0f
            if (translation == 0f) {
                child.hide()
            } else {
                val site = siteLiveData.value
                trackers?.text = site?.trackerCount.toString()
                website?.text = site?.url?.extractDomain()
                child.show()
            }
            super.onNestedPreScroll(coordinatorLayout, child, target, dx, dy, consumed, type)
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
}
