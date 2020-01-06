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
import android.view.LayoutInflater
import android.widget.FrameLayout
import kotlinx.android.synthetic.main.view_tab_switcher_button.view.*

class TabSwitcherButton(context: Context) : FrameLayout(context) {

    var count = 0
        set(value) {
            field = value
            val text = if (count < 100) "$count" else "~"
            tabCount.text = text
        }

    var hasUnread = false
        set(value) {
            field = value
            anim.progress = if (hasUnread) 1.0f else 0.0f
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.view_tab_switcher_button, this, true)

        clickZone.setOnClickListener {
            super.callOnClick()
        }

        clickZone.setOnLongClickListener {
            super.performLongClick()
        }
    }

    fun increment(callback: () -> Unit) {
        anim.progress = 0.0f

        anim.addAnimatorListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                callback()
            }
        })

        fadeOutCount {
            count += 1
            fadeInCount()
        }

        anim.playAnimation()
    }

    private fun fadeOutCount(callback: () -> Unit) {
        val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
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