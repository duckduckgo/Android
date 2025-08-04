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

package com.duckduckgo.app.onboarding.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import kotlin.time.Duration.Companion.milliseconds

class BbProgressDotIndicator @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var dotSizePx: Int = DOT_DIAMETER_DP.toPx()
    private var dotSpacingPx: Int = DOT_SPACING_DP.toPx()
    private var stepCount: Int = 1
    private var currentStep: Int = 1

    init {
        orientation = HORIZONTAL
    }

    fun setCurrentStep(step: Int) {
        require(step in (1..stepCount))
        if (step != currentStep) {
            animateDotChange(currentStep, step)
            currentStep = step
        }
    }

    fun setStepCount(count: Int) {
        require(count > 0)
        if (count != stepCount) {
            stepCount = count
            if (currentStep > stepCount) currentStep = stepCount
            updateDots()
        }
    }

    private fun updateDots() {
        removeAllViews()
        repeat(stepCount) { index ->
            val dot = View(context).apply {
                layoutParams = LayoutParams(dotSizePx, dotSizePx).apply {
                    marginStart = if (index > 0) dotSpacingPx else 0
                }
                background = createDotDrawable()
                alpha = if (index + 1 == currentStep) ACTIVE_DOT_ALPHA else INACTIVE_DOT_ALPHA
            }
            addView(dot)
        }
    }

    private fun animateDotChange(
        oldStep: Int,
        newStep: Int,
    ) {
        val oldDot = getChildAt(oldStep - 1)
        val newDot = getChildAt(newStep - 1)
        oldDot?.animate()?.alpha(INACTIVE_DOT_ALPHA)?.setDuration(ANIMATION_DURATION.inWholeMilliseconds)
        newDot?.animate()?.alpha(ACTIVE_DOT_ALPHA)?.setDuration(ANIMATION_DURATION.inWholeMilliseconds)
    }

    private fun createDotDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.bbSurfaceColor))
        }

    private companion object {
        const val DOT_DIAMETER_DP = 8
        const val DOT_SPACING_DP = 4
        val ANIMATION_DURATION = 200.milliseconds
        const val INACTIVE_DOT_ALPHA = 0.3f
        const val ACTIVE_DOT_ALPHA = 1.0f
    }
}
