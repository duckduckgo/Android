/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.browser

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.duckduckgo.app.browser.databinding.ViewTabSwitcherButtonBinding
import com.duckduckgo.mobile.android.ui.viewbinding.viewBinding

class TabSwitcherButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : RelativeLayout(context, attrs, defStyleAttr) {

    private val binding: ViewTabSwitcherButtonBinding by viewBinding()

    var count = 0
        set(value) {
            field = value
            val text = if (count < 100) "$count" else "~"
            binding.tabCount.text = text
        }

    var hasUnread = false

    fun increment(callback: () -> Unit) {
        fadeOutCount {
            count += 1
            fadeInCount()
            callback()
        }
    }

    fun animateCount() {
        fadeOutCount {
            fadeInCount()
        }
    }

    private fun fadeOutCount(callback: () -> Unit) {
        val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                // otherwise on end keeps being called repeatedly
                binding.tabCount.animate().setListener(null)
                callback()
            }
        }

        binding.tabCount.animate()
            .setDuration(300)
            .alpha(0.0f)
            .setListener(listener)
            .start()
    }

    private fun fadeInCount() {
        binding.tabCount.animate()
            .setDuration(300)
            .alpha(1.0f)
            .start()
    }

}
