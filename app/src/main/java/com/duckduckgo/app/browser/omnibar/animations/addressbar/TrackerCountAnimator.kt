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

package com.duckduckgo.app.browser.omnibar.animations.addressbar

import android.animation.Animator
import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val TRACKER_COUNT_MINIMUM_ANIMATION_TRIGGER_THRESHOLD = 5
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

    /**
     * Animates the tracker count from a start value to an end value in the provided text view.
     *
     * For counts below [TRACKER_COUNT_MINIMUM_ANIMATION_TRIGGER_THRESHOLD], the final count is displayed immediately
     * without animation. For higher counts, the animation starts from a percentage of the final count and animates up.
     *
     * @param context Android context for resources
     * @param totalTrackerCount The total number of trackers blocked
     * @param trackerTextView The text view that will display the animated count
     * @param onAnimationEnd Callback invoked when the animation completes
     */
    fun animateTrackersBlockedCountView(
        context: Context,
        totalTrackerCount: Int,
        trackerTextView: DaxTextView,
        onAnimationEnd: () -> Unit = {},
    ) {
        this.context = context
        this.trackerTextView = trackerTextView

        val endCount = getTrackerAnimationEndCount(totalTrackerCount = totalTrackerCount)

        val animationDuration =
            if (endCount >= TRACKER_COUNT_UPPER_THRESHOLD) {
                TRACKER_COUNT_UPPER_THRESHOLD_ANIMATION_DURATION
            } else {
                TRACKER_COUNT_LOWER_THRESHOLD_ANIMATION_DURATION
            }

        if (endCount < TRACKER_COUNT_MINIMUM_ANIMATION_TRIGGER_THRESHOLD) {
            trackerTextView.text = endCount.toString()
            trackerTextView.show()
            trackerTextView.postDelayed({
                onAnimationEnd()
            }, animationDuration.inWholeMilliseconds)
            return
        }

        val startCount = getTrackerAnimationStartCount(totalTrackerCount = totalTrackerCount)

        trackerTextView.text = startCount.toString()
        trackerTextView.show()

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

    /**
     * Calculates the starting count for the tracker animation.
     *
     * The start count is a percentage of the end count to create a smooth counting effect:
     * - For counts >= [TRACKER_COUNT_UPPER_THRESHOLD]: starts at 85% of final count
     * - For counts < [TRACKER_COUNT_UPPER_THRESHOLD]: starts at 75% of final count
     *
     * @param totalTrackerCount The total number of trackers blocked
     * @return The calculated starting count for the animation
     */
    fun getTrackerAnimationStartCount(totalTrackerCount: Int): Int {
        val endCount = totalTrackerCount.coerceAtMost(TRACKER_TOTAL_MAX_LIMIT)

        val startPercentage =
            if (endCount >= TRACKER_COUNT_UPPER_THRESHOLD) {
                TRACKER_COUNT_UPPER_THRESHOLD_PERCENTAGE
            } else {
                TRACKER_COUNT_LOWER_THRESHOLD_PERCENTAGE
            }

        return (endCount * startPercentage).roundToInt()
    }

    /**
     * Calculates the ending count for the tracker animation.
     *
     * The end count is capped at [TRACKER_TOTAL_MAX_LIMIT] to prevent excessively large numbers from being displayed.
     *
     * @param totalTrackerCount The total number of trackers blocked
     * @return The calculated ending count, capped at [TRACKER_TOTAL_MAX_LIMIT]
     */
    fun getTrackerAnimationEndCount(totalTrackerCount: Int): Int = totalTrackerCount.coerceAtMost(TRACKER_TOTAL_MAX_LIMIT)
}
