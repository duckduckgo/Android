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

package com.duckduckgo.app.browser.omnibar.animations

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import com.duckduckgo.common.ui.view.text.DaxTextView
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val TRACKER_COUNT_LOWER_THRESHOLD_PERCENTAGE = 0.75f
private const val TRACKER_COUNT_UPPER_THRESHOLD_PERCENTAGE = 0.85f
private const val TRACKER_COUNT_UPPER_THRESHOLD = 40
private const val TRACKER_TOTAL_MAX_LIMIT = 9999
private val TRACKER_COUNT_LOWER_THRESHOLD_ANIMATION_DURATION = 0.5.seconds
private val TRACKER_COUNT_UPPER_THRESHOLD_ANIMATION_DURATION = 0.5.seconds

class TrackerCountAnimator @Inject constructor() {

    private var trackerTextView: TextView? = null
    private lateinit var context: Context

    private val animator = ValueAnimator().apply {
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            val newTrackerCount = animation.animatedValue as Int
            trackerTextView?.text = newTrackerCount.toString()
        }
    }

    fun animateTrackersBlockedCountView(
        context: Context,
        totalTrackerCount: Int,
        trackerTextView: DaxTextView,
        onAnimationEnd: () -> Unit = {},
    ) {
        this.context = context
        this.trackerTextView = trackerTextView

        val endCount = totalTrackerCount.coerceAtMost(TRACKER_TOTAL_MAX_LIMIT)

        val startPercentage = if (endCount >= TRACKER_COUNT_UPPER_THRESHOLD) {
            TRACKER_COUNT_UPPER_THRESHOLD_PERCENTAGE
        } else {
            TRACKER_COUNT_LOWER_THRESHOLD_PERCENTAGE
        }

        val startCount = (endCount * startPercentage).roundToInt()

        val animationDuration = if (endCount >= TRACKER_COUNT_UPPER_THRESHOLD) {
            TRACKER_COUNT_UPPER_THRESHOLD_ANIMATION_DURATION
        } else {
            TRACKER_COUNT_LOWER_THRESHOLD_ANIMATION_DURATION
        }

        trackerTextView.text = startCount.toString()

        animator.removeAllListeners()
        animator.addListener(
            object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onAnimationEnd()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            },
        )

        animator.setIntValues(startCount, endCount)
        animator.duration = animationDuration.inWholeMilliseconds
        animator.start()
    }
}
