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

import android.animation.Animator
import android.animation.ObjectAnimator
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ProgressBar
import androidx.core.animation.addListener

class SmoothProgressAnimator(private val pageLoadingIndicator: ProgressBar) {

    private var progressBarAnimation: ObjectAnimator =
        ObjectAnimator.ofInt(pageLoadingIndicator, "progress", 0)

    fun onNewProgress(newProgress: Int, onAnimationEnd: (Animator?) -> Unit) {
        progressBarAnimation.pause()
        pageLoadingIndicator.apply {
            if (progress > newProgress) {
                progress = 0
            }
            progressBarAnimation.apply {
                removeAllListeners()
                setIntValues(newProgress)
                duration =
                    if (newProgress < MIN_PROGRESS_BAR) ANIM_DURATION_PROGRESS_LONG
                    else ANIM_DURATION_PROGRESS_SHORT
                interpolator = AccelerateDecelerateInterpolator()
                addListener(onEnd = onAnimationEnd)
                start()
            }
        }
    }

    companion object {
        private const val MIN_PROGRESS_BAR = 75
        private const val ANIM_DURATION_PROGRESS_LONG = 1500L
        private const val ANIM_DURATION_PROGRESS_SHORT = 200L
    }
}
