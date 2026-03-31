/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import kotlin.math.ceil

/**
 * A pill-shaped step indicator for the onboarding flow.
 *
 * Displays a row of dots (one active, the rest inactive) alongside an "X of Y" text label.
 * When transitioning to the next step, the current active dot shrinks and fades to the inactive
 * colour while the next dot grows and fades to the active colour. Dots maintain a fixed 4dp
 * edge-to-edge gap, so positions shift smoothly as dots change size. The text label updates
 * immediately when the animation starts.
 *
 * Usage in XML:
 * ```xml
 * <com.duckduckgo.app.onboarding.ui.view.OnboardingStepIndicatorView
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:stepCount="5"
 *     app:currentStep="1" />
 * ```
 *
 * Usage in code:
 * ```kotlin
 * stepIndicator.setSteps(totalSteps = 5, currentStep = 1)
 * stepIndicator.animateToNextStep()
 * ```
 *
 * @see totalSteps total number of onboarding steps (readable after [setSteps])
 * @see currentStep the currently active step, 1-based (readable after [setSteps] or [setCurrentStep])
 */
class OnboardingStepIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val state = OnboardingStepState()
    private val dotsView: StepIndicatorDotsView
    private val textLabel: DaxTextView
    private val contentLayout: LinearLayout

    val totalSteps: Int get() = state.totalSteps
    val currentStep: Int get() = state.currentStep

    init {
        val horizontalPaddingPx = HORIZONTAL_PADDING_DP.toPx(context)
        val verticalPaddingPx = VERTICAL_PADDING_DP.toPx(context)
        val endPaddingPx = END_PADDING_DP.toPx(context)
        setPadding(horizontalPaddingPx, verticalPaddingPx, endPaddingPx, verticalPaddingPx)
        outlineProvider = null

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        dotsView = StepIndicatorDotsView(context).apply {
            onAnimationEnd = {
                syncDotViewState()
            }
        }

        textLabel = DaxTextView(context).apply {
            setTypography(DaxTextView.Typography.BodyOnboarding)
            textSize = 12f
        }

        val gapPx = DOT_TEXT_GAP_DP.toPx(context)
        contentLayout.addView(dotsView)
        contentLayout.addView(
            textLabel,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = gapPx
            },
        )

        addView(
            contentLayout,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER),
        )

        parseAttributes(attrs)
    }

    private fun parseAttributes(attrs: AttributeSet?) {
        if (attrs == null) return
        context.obtainStyledAttributes(attrs, R.styleable.OnboardingStepIndicatorView, 0, 0).apply {
            val stepCount = getInt(R.styleable.OnboardingStepIndicatorView_stepCount, 0)
            val currentStep = getInt(R.styleable.OnboardingStepIndicatorView_currentStep, 1)
            if (stepCount > 0) {
                setSteps(stepCount, currentStep)
            }
            recycle()
        }
    }

    /**
     * Configure the total number of steps and the initial active step.
     *
     * @param totalSteps Total number of onboarding steps. Must be >= 1.
     * @param currentStep The initially active step, 1-based. Defaults to 1.
     */
    fun setSteps(totalSteps: Int, currentStep: Int = 1) {
        state.setSteps(totalSteps, currentStep)
        syncDotViewState()
        updateText()
        pinTextLabelWidth()
    }

    /**
     * Jump directly to a specific step without animation.
     *
     * @param step The step to jump to, 1-based. Clamped to [1, totalSteps].
     */
    fun setCurrentStep(step: Int) {
        state.setCurrentStep(step)
        syncDotViewState()
        updateText()
    }

    /**
     * Animate the active dot transitioning to the next step.
     *
     * The current dot shrinks and fades to inactive while the next dot grows and fades to active.
     * The text label ("X of Y") updates immediately when the animation starts.
     * Does nothing if already at the last step.
     */
    fun animateToNextStep() {
        if (!state.hasNextStep) return
        state.advanceToNextStep()
        updateText()
        dotsView.animateToNextStep()
    }

    private fun syncDotViewState() {
        dotsView.totalSteps = state.totalSteps
        dotsView.activeStep = state.activeStepIndex
    }

    private fun updateText() {
        textLabel.text = context.getString(R.string.onboardingStepIndicatorText, state.currentStep, state.totalSteps)
    }

    private fun pinTextLabelWidth() {
        val paint = textLabel.paint
        var maxWidth = 0f
        for (step in 1..state.totalSteps) {
            val text = context.getString(R.string.onboardingStepIndicatorText, step, state.totalSteps)
            val width = paint.measureText(text)
            if (width > maxWidth) maxWidth = width
        }
        textLabel.minWidth = ceil(maxWidth.toDouble()).toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        setBackgroundResource(R.drawable.background_onboarding_step_indicator)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dotsView.cancelAnimation()
    }

    companion object {
        private const val HORIZONTAL_PADDING_DP = 8
        private const val END_PADDING_DP = 10
        private const val VERTICAL_PADDING_DP = 6
        private const val DOT_TEXT_GAP_DP = 20
    }
}
