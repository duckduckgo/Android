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

package com.duckduckgo.app.onboarding.ui.page

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updatePadding
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R
import com.google.android.material.textview.MaterialTextView

class BbOnboardingProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : MaterialTextView(context, attrs, defStyleAttr) {

    private var currentStep: Int = 1
    private var stepCount: Int = 1

    private val currentStepColor: Int = ContextCompat.getColor(context, R.color.bbColorPrimaryText)
    private val defaultTextColor: Int = ContextCompat.getColor(context, R.color.bbColorSecondaryText)

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 30f.toPx()
            setColor(ContextCompat.getColor(context, R.color.bbSurfaceColor))
        }

        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        typeface = ResourcesCompat.getFont(context, R.font.pangea_regular)
        includeFontPadding = false
        gravity = Gravity.CENTER

        val paddingHorizontalPx = PADDING_HORIZONTAL_DP.toPx()
        val paddingVerticalPx = PADDING_VERTICAL_DP.toPx()
        updatePadding(
            left = paddingHorizontalPx,
            top = paddingVerticalPx,
            right = paddingHorizontalPx,
            bottom = paddingVerticalPx,
        )

        minHeight = MIN_HEIGHT_DP.toPx()
        minWidth = calculateMinWidthPx()

        updateProgressText()
    }

    fun setCurrentStep(step: Int) {
        if (step != currentStep && step > 0) {
            currentStep = step
            updateProgressText()
        }
    }

    fun setStepCount(count: Int) {
        if (count != stepCount && count > 0) {
            stepCount = count
            updateProgressText()
        }
    }

    private fun updateProgressText() {
        val progressText = "$currentStep / $stepCount"
        val spannableString = SpannableString(progressText)

        // Apply color to current step number only
        val currentStepLength = currentStep.toString().length
        spannableString.setSpan(
            ForegroundColorSpan(currentStepColor),
            0,
            currentStepLength,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        // Apply default color to separator and total steps
        spannableString.setSpan(
            ForegroundColorSpan(defaultTextColor),
            currentStepLength,
            progressText.length,
            SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE,
        )

        text = spannableString
    }

    // ensures the view doesn't resize when progress is updated from 1 to 2
    private fun calculateMinWidthPx(): Int =
        (paint.measureText("2 / 3") + 2 * PADDING_HORIZONTAL_DP.toPx()).toInt()

    private companion object {
        const val PADDING_VERTICAL_DP = 8
        const val PADDING_HORIZONTAL_DP = 16
        const val MIN_HEIGHT_DP = 40
    }
}
