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

package com.duckduckgo.app.onboarding.ui.page

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.addListener
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import kotlin.math.roundToInt

/**
 * Per-step configuration for onboarding background images.
 *
 * @param backgroundRes Drawable resource for this step's background image.
 * @param maxHeightDp Maximum display height for the background image, in dp.
 */
sealed class OnboardingBackgroundStep(
    @DrawableRes val backgroundRes: Int,
    val maxHeightDp: Int,
) {
    data object Welcome : OnboardingBackgroundStep(
        backgroundRes = R.drawable.onboarding_welcome_screen_background,
        maxHeightDp = 404,
    )
}

/**
 * Animates background image transitions between onboarding steps
 *
 * Uses two ping-ponging ImageViews: one displays the current background while the other
 * is prepared off-screen for the next transition. On each [transitionTo] call, the active
 * view exits (slides left + fades) while the idle view enters (slides in from right).
 */
class OnboardingBackgroundAnimator(
    private val backgroundPrimary: ImageView,
    private val backgroundSecondary: ImageView,
) {
    private var enterExitAnimatorSet: AnimatorSet? = null
    private var activeView: ImageView = backgroundPrimary

    /**
     * Transitions the background to the given [step].
     *
     * Must be called after the view hierarchy has been laid out (e.g., inside [View.doOnLayout]).
     *
     * The current active view exits (slides left + fades) while the new background enters
     * from the right.
     *
     * @param step The background step to transition to.
     * @param enterStartX Optional starting X translation for the entering view. When non-null,
     *   overrides the default off-screen start position. Use this to start from a shorter
     *   distance on large screens.
     * @param onAnimationStarted Callback invoked when the transition animation starts.
     * @param onAnimationEnd Callback invoked when the transition animation completes.
     */
    fun transitionTo(
        step: OnboardingBackgroundStep,
        enterStartX: Float? = null,
        onAnimationStarted: () -> Unit = {},
        onAnimationEnd: () -> Unit = {},
    ) {
        val inView = if (activeView == backgroundPrimary) backgroundSecondary else backgroundPrimary
        val outView = activeView

        cancel()

        val density = inView.resources.displayMetrics.density
        val screenWidth = inView.rootView.width.toFloat()

        with(inView) {
            updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight = (step.maxHeightDp * density).roundToInt()
            }
            setImageResource(step.backgroundRes)

            val startX = enterStartX ?: (screenWidth + centerCropOverflow(inView, screenWidth))
            translationX = startX
            alpha = 0f
            isVisible = true

            val exitAnimator = buildExitAnimator(outView, screenWidth)
            val enterAnimator = buildEnterAnimator(inView, startX)
            enterExitAnimatorSet = AnimatorSet().apply {
                playTogether(exitAnimator, enterAnimator)
                addListener(
                    onStart = {
                        onAnimationStarted()
                    },
                    onEnd = {
                        onAnimationEnd()
                    },
                )
                start()
            }
        }

        activeView = inView
    }

    /**
     * Immediately sets the background to the given [step] without animation.
     *
     * Used to restore the correct background state after configuration changes (e.g., rotation).
     */
    fun snapTo(step: OnboardingBackgroundStep) {
        cancel()

        val density = backgroundSecondary.resources.displayMetrics.density

        with(backgroundSecondary) {
            updateLayoutParams<ConstraintLayout.LayoutParams> {
                matchConstraintMaxHeight = (step.maxHeightDp * density).roundToInt()
            }
            setImageResource(step.backgroundRes)
            translationX = 0f
            alpha = 1f
            isVisible = true
        }

        backgroundPrimary.alpha = 0f
        backgroundPrimary.translationX = 0f
        backgroundPrimary.isVisible = false

        activeView = backgroundSecondary
    }

    fun cancel() {
        enterExitAnimatorSet?.cancel()
        enterExitAnimatorSet = null
        backgroundPrimary.animate().cancel()
        backgroundSecondary.animate().cancel()
    }

    private fun buildExitAnimator(outView: View, screenWidth: Float): ValueAnimator {
        val overflow = centerCropOverflow(outView as ImageView, screenWidth)
        val maxSlideDistance = screenWidth + overflow

        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EXIT_DURATION
            interpolator = EASE_IN_OUT
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                outView.translationX = -maxSlideDistance * progress
                // Fade out at 4x speed: fully transparent at 25% of the slide
                outView.alpha = maxOf(0f, 1f - progress * 4f)
            }
        }
    }

    private fun buildEnterAnimator(inView: View, startX: Float): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ENTER_DURATION
            interpolator = EASE_IN_OUT
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                inView.translationX = startX * (1f - progress)
                // Fade in during last 25% — inverse of exit's first-25% fade-out
                inView.alpha = minOf(1f, maxOf(0f, (progress - 0.75f) * 4f))
            }
        }
    }

    /**
     * Returns how far the centerCrop-scaled image extends beyond the view's left or right edge.
     * With clipChildren=false on the parent, this overflow is drawn on-screen and must
     * be accounted for when positioning the view off-screen.
     */
    private fun centerCropOverflow(view: ImageView, viewWidth: Float): Float {
        val drawable = view.drawable ?: return 0f
        val intrinsicW = drawable.intrinsicWidth.takeIf { it > 0 } ?: return 0f
        val intrinsicH = drawable.intrinsicHeight.takeIf { it > 0 } ?: return 0f
        val viewHeight = view.height.toFloat().takeIf { it > 0f } ?: return 0f
        val scale = maxOf(viewWidth / intrinsicW, viewHeight / intrinsicH)
        val scaledWidth = intrinsicW * scale
        return maxOf(0f, (scaledWidth - viewWidth) / 2f)
    }

    companion object {
        private const val EXIT_DURATION = 1500L
        private const val ENTER_DURATION = 1000L
        private val EASE_IN_OUT = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    }
}
