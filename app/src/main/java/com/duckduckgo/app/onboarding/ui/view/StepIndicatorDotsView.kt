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

import android.animation.AnimatorListenerAdapter
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.mobile.android.R as CommonR

/**
 * A View that draws step indicator dots on a Canvas.
 *
 * Each dot is drawn at its actual diameter (active=12dp, inactive=6dp) with a fixed
 * 4dp edge-to-edge gap between adjacent dots. Dot positions shift during transitions
 * to maintain the 4dp gap as dots grow and shrink.
 *
 * The active dot is drawn at full size in [CommonR.attr.onboardingAccentPrimary].
 * Inactive dots are drawn smaller in [CommonR.attr.onboardingAccentAltPrimary].
 *
 * When transitioning, both size and colour are animated simultaneously:
 * the current active dot shrinks and fades to the inactive colour while
 * the next dot grows and fades to the active colour.
 */
internal class StepIndicatorDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val activeDotDiameterPx = ACTIVE_DOT_DIAMETER_DP.toPx(context)
    private val inactiveDotDiameterPx = INACTIVE_DOT_DIAMETER_DP.toPx(context)
    private val dotSpacingPx = DOT_SPACING_DP.toPx(context)

    private val activeColor = context.getColorFromAttr(CommonR.attr.onboardingAccentPrimary)
    private val inactiveColor = context.getColorFromAttr(CommonR.attr.onboardingAccentAltPrimary)
    private val argbEvaluator = ArgbEvaluator()

    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = activeColor
        style = Paint.Style.FILL
    }

    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = inactiveColor
        style = Paint.Style.FILL
    }

    /** Reusable paint whose colour is set per-frame during animation. */
    private val animatingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    var totalSteps: Int = 0
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    var activeStep: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    // Animation state
    private var isAnimating = false
    private var animationProgress = 0f
    private var animatingFromStep = 0
    private var animatingToStep = 0
    private var animator: ValueAnimator? = null
    var onAnimationEnd: (() -> Unit)? = null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val steps = totalSteps
        val desiredWidth = if (steps <= 0) {
            0
        } else {
            // 1 active dot + (N-1) inactive dots + (N-1) gaps
            // During animation the total width stays the same (one grows while one shrinks)
            (activeDotDiameterPx + (steps - 1) * inactiveDotDiameterPx + (steps - 1) * dotSpacingPx).toInt()
        }
        val desiredHeight = activeDotDiameterPx.toInt()

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val steps = totalSteps
        if (steps <= 0) return

        val centerY = height / 2f
        var x = 0f

        for (i in 0 until steps) {
            val diameter = getDotDiameter(i)
            val radius = diameter / 2f
            val cx = x + radius

            when {
                isAnimating && i == animatingFromStep -> {
                    canvas.drawCircle(cx, centerY, radius, activePaint)
                }

                isAnimating && i == animatingToStep -> {
                    animatingPaint.color = argbEvaluator.evaluate(animationProgress, inactiveColor, activeColor) as Int
                    canvas.drawCircle(cx, centerY, radius, animatingPaint)
                }

                i == activeStep && !isAnimating -> {
                    canvas.drawCircle(cx, centerY, activeDotDiameterPx / 2f, activePaint)
                }

                else -> {
                    val paint = if (i < activeStep) activePaint else inactivePaint
                    canvas.drawCircle(cx, centerY, inactiveDotDiameterPx / 2f, paint)
                }
            }

            x = cx + radius + dotSpacingPx
        }
    }

    private fun getDotDiameter(index: Int): Float {
        if (!isAnimating) {
            return if (index == activeStep) activeDotDiameterPx else inactiveDotDiameterPx
        }
        return when (index) {
            animatingFromStep -> lerp(activeDotDiameterPx, inactiveDotDiameterPx, animationProgress)
            animatingToStep -> lerp(inactiveDotDiameterPx, activeDotDiameterPx, animationProgress)
            else -> inactiveDotDiameterPx
        }
    }

    fun animateToNextStep() {
        if (activeStep >= totalSteps - 1) return
        if (isAnimating) return

        animatingFromStep = activeStep
        animatingToStep = activeStep + 1
        isAnimating = true
        animationProgress = 0f

        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIMATION_DURATION_MS
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener {
                animationProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    isAnimating = false
                    activeStep = animatingToStep
                    onAnimationEnd?.invoke()
                }
            })
            start()
        }
    }

    fun cancelAnimation() {
        animator?.cancel()
        isAnimating = false
    }

    companion object {
        private const val ACTIVE_DOT_DIAMETER_DP = 12f
        private const val INACTIVE_DOT_DIAMETER_DP = 6f
        private const val DOT_SPACING_DP = 4f
        private const val ANIMATION_DURATION_MS = 300L

        private fun lerp(start: Float, end: Float, fraction: Float): Float {
            return start + (end - start) * fraction
        }
    }
}
