/*
 * Copyright (c) 2020 DuckDuckGo
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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.duckduckgo.app.global.view.setAllParentsClip
import timber.log.Timber

class PulseAnimation(private val lifecycleOwner: LifecycleOwner) : LifecycleObserver {
    private var pulseAnimation: AnimatorSet = AnimatorSet()
    private var highlightImageView: View? = null

    @Suppress("unused")
    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun onStart() {
        if (pulseAnimation.isPaused) {
            pulseAnimation.resume()
        }
    }

    @Suppress("unused")
    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun onStop() {
        if (pulseAnimation.isRunning) {
            pulseAnimation.pause()
        }
    }

    fun playOn(targetView: View) {
        if (highlightImageView == null) {
            highlightImageView = addHighlightView(targetView)
            highlightImageView?.doOnLayout {
                it.setAllParentsClip(enabled = false)
                startPulseAnimation(it)
            }
            lifecycleOwner.lifecycle.addObserver(this)
        }
    }

    fun stop() {
        if (pulseAnimation.isRunning) {
            pulseAnimation.end()
        }
        highlightImageView?.isVisible = false
        highlightImageView = null
        lifecycleOwner.lifecycle.removeObserver(this)
    }

    private fun startPulseAnimation(view: View) {
        if (!pulseAnimation.isRunning) {
            val pulse = ObjectAnimator.ofPropertyValuesHolder(
                view,
                PropertyValuesHolder.ofFloat("scaleX", 1.4f, 1.8f, 2f, 1.8f, 1.4f),
                PropertyValuesHolder.ofFloat("scaleY", 1.4f, 1.8f, 2f, 1.8f, 1.4f),
                PropertyValuesHolder.ofFloat("alpha", 1f, 0.35f, 0.2f, 0.35f, 1f)
            )
            pulse.repeatCount = ObjectAnimator.INFINITE
            pulse.duration = 1400L

            pulseAnimation = AnimatorSet().apply {
                play(pulse)
                start()
            }
        }
    }

    private fun addHighlightView(targetView: View): View {
        if (targetView.parent !is ViewGroup) error("targetView parent should be ViewGroup")

        val highlightImageView = ImageView(targetView.context)
        highlightImageView.id = View.generateViewId()
        highlightImageView.setImageResource(R.drawable.ic_circle_pulse)
        val layoutParams = FrameLayout.LayoutParams(targetView.width, targetView.height, Gravity.CENTER)
        (targetView.parent as ViewGroup).addView(highlightImageView, 0, layoutParams)
        return highlightImageView
    }
}
