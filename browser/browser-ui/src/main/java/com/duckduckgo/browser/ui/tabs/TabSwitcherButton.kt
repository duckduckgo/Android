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

package com.duckduckgo.browser.ui.tabs

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.browser.ui.R

open class TabSwitcherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    open var count: Int = 0

    open var hasUnread = false

    internal val tabCount: FrameLayout by lazy { findViewById(R.id.tabCount) }

    fun increment(callback: () -> Unit) {
        fadeOutCount {
            count += 1
            fadeInCount()
            callback()
        }
    }

    private fun fadeOutCount(callback: () -> Unit) {
        val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                // otherwise on end keeps being called repeatedly
                tabCount.animate().setListener(null)
                callback()
            }
        }

        tabCount.animate()
            .setDuration(300)
            .alpha(0.0f)
            .setListener(listener)
            .start()
    }

    private fun fadeInCount() {
        tabCount.animate()
            .setDuration(300)
            .alpha(1.0f)
            .start()
    }
}
