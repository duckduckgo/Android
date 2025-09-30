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

package com.duckduckgo.duckchat.impl.inputscreen.ui.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.viewbinding.viewBinding
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.databinding.ViewNewToolbarTabSwitcherButtonBinding
import com.duckduckgo.mobile.android.R as CommonR

class InputScreenTabSwitcherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    internal val tabCount: FrameLayout by lazy { findViewById(R.id.tabCount) }

    fun increment(callback: () -> Unit) {
        fadeOutCount {
            count += 1
            fadeInCount()
            callback()
        }
    }

    private fun fadeOutCount(callback: () -> Unit) {
        val listener =
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // otherwise on end keeps being called repeatedly
                    tabCount.animate().setListener(null)
                    callback()
                }
            }

        tabCount
            .animate()
            .setDuration(300)
            .alpha(0.0f)
            .setListener(listener)
            .start()
    }

    private fun fadeInCount() {
        tabCount
            .animate()
            .setDuration(300)
            .alpha(1.0f)
            .start()
    }

    private val binding: ViewNewToolbarTabSwitcherButtonBinding by viewBinding()

    var hasUnread: Boolean = false
        set(value) {
            if (field != value) {
                if (value) {
                    binding.tabsImageView.setImageResource(CommonR.drawable.ic_tab_24_highlighted)
                } else {
                    binding.tabsImageView.setImageResource(CommonR.drawable.ic_tab_24)
                }
            }
            field = value
        }

    var count = 0
        set(value) {
            if (field != value) {
                if (value < 100) {
                    binding.tabCount.text = "$value"
                    binding.tabCount.show()
                    binding.tabCountInfinite.gone()
                } else {
                    binding.tabCount.gone()
                    binding.tabCountInfinite.show()
                }
            }
            field = value
        }
}
