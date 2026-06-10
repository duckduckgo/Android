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

package com.duckduckgo.app.tabs.ui

import android.animation.ValueAnimator
import android.content.Context
import android.text.Spanned
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.text.HtmlCompat
import java.text.NumberFormat
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds

private const val TRACKER_COUNT_LOWER_THRESHOLD_PERCENTAGE = 0.75f
private const val TRACKER_COUNT_UPPER_THRESHOLD_PERCENTAGE = 0.85f
private const val TRACKER_COUNT_UPPER_THRESHOLD = 40
private const val TRACKER_TOTAL_MAX_LIMIT = 9999
private val TRACKER_COUNT_LOWER_THRESHOLD_ANIMATION_DURATION = 1.seconds
private val TRACKER_COUNT_UPPER_THRESHOLD_ANIMATION_DURATION = 1.seconds

class TrackerCountAnimator @Inject constructor() {

    @StringRes private var stringRes: Int? = null
    private var trackerTextView: TextView? = null
    private lateinit var context: Context
    private val trackerCountNumberFormatter by lazy(LazyThreadSafetyMode.NONE) { NumberFormat.getNumberInstance() }

    private val animator = ValueAnimator().apply {
        interpolator = AccelerateDecelerateInterpolator()
        addUpdateListener { animation ->
            val newTrackerCount = animation.animatedValue as Int
            trackerTextView?.text = getFormattedText(context, stringRes!!, newTrackerCount)
        }
    }

    fun animateTrackersBlockedCountView(
        context: Context,
        @StringRes stringRes: Int,
        totalTrackerCount: Int,
        trackerTextView: TextView,
    ) {
        this.context = context
        this.stringRes = stringRes
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

        trackerTextView.text = getFormattedText(context, stringRes, startCount)

        animator.setIntValues(startCount, endCount)
        animator.duration = animationDuration.inWholeMilliseconds
        animator.start()
    }

    private fun getFormattedText(context: Context, @StringRes stringRes: Int, trackerCount: Int): Spanned {
        val formattedTrackerCount = trackerCountNumberFormatter.format(trackerCount)
        val text = context.getString(stringRes, formattedTrackerCount)
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
