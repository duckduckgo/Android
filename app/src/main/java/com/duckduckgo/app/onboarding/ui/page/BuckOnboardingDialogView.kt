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

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.animation.doOnEnd
import androidx.core.content.ContextCompat
import androidx.core.view.isEmpty
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import com.duckduckgo.common.ui.view.toPx

class BuckOnboardingDialogView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    init {
        alpha = 0f
        background = ContextCompat.getDrawable(context, R.drawable.background_buck_onboarding_dialog)
        clipToPadding = false
        clipChildren = true
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    fun animateEntrance() {
        if (isEmpty()) return

        val contentView = getChildAt(0)
        contentView.alpha = 0f

        post {
            val animationDuration = 800L

            val targetWidth = width
            val targetHeight = height
            val targetLayoutParamsHeight = layoutParams.height
            val targetLayoutParamsWidth = layoutParams.width
            val targetMarginTop = marginTop

            val initialWidth = 64.toPx()
            val initialHeight = 64.toPx()
            val initialContentTranslationY = 100f.toPx()

            // If the content view is a scrollable view, we want to disable the vertical scrollbar during animation
            val verticalScrollbarEnabled = (contentView as? ScrollView)?.isVerticalScrollBarEnabled

            // https://m3.material.io/styles/motion/easing-and-duration/tokens-specs#7e37d374-0c1b-4007-8187-6f29bb1fb3e7
            val standardEasingInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

            // Start small (minimal dimensions)
            updateLayoutParams {
                width = initialWidth
                height = initialHeight

                // Adjust the top margin to keep the bubble aligned to the bottom
                (this as MarginLayoutParams).topMargin = targetMarginTop + (targetHeight - initialHeight)
            }

            (contentView as? ScrollView)?.isVerticalScrollBarEnabled = false

            // Animate alpha
            val alphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animationDuration / 2
                interpolator = standardEasingInterpolator

                addUpdateListener { animator ->
                    alpha = animator.animatedValue as Float
                }
            }

            // Animate width (horizontal expansion)
            val widthAnimator = ValueAnimator.ofInt(initialWidth, targetWidth).apply {
                duration = animationDuration / 2
                interpolator = standardEasingInterpolator

                addUpdateListener { animator ->
                    updateLayoutParams {
                        width = animator.animatedValue as Int
                    }
                }
            }

            // Animate height (vertical expansion from bottom to top)
            val heightAnimator = ValueAnimator.ofInt(initialHeight, targetHeight).apply {
                duration = animationDuration / 2
                startDelay = animationDuration / 2 // begin vertical expansion after horizontal expansion is complete
                interpolator = standardEasingInterpolator

                addUpdateListener { animator ->
                    val currentHeight = animator.animatedValue as Int
                    updateLayoutParams {
                        height = currentHeight
                        (this as MarginLayoutParams).topMargin = targetMarginTop + (targetHeight - currentHeight)
                    }
                }
            }

            // Content fade-in
            val contentAlphaAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = animationDuration / 2
                startDelay = animationDuration / 2
                interpolator = standardEasingInterpolator

                addUpdateListener { animator ->
                    contentView.alpha = animator.animatedValue as Float
                }
            }

            // Content slide-up
            val contentTranslationYAnimator = ValueAnimator.ofFloat(initialContentTranslationY, 0f).apply {
                duration = animationDuration / 2
                startDelay = animationDuration / 2
                interpolator = standardEasingInterpolator

                addUpdateListener { animator ->
                    contentView.translationY = animator.animatedValue as Float
                }
            }

            AnimatorSet().apply {
                playTogether(
                    alphaAnimator,
                    widthAnimator,
                    heightAnimator,
                    contentAlphaAnimator,
                    contentTranslationYAnimator,
                )

                doOnEnd {
                    if (verticalScrollbarEnabled != null) {
                        contentView.isVerticalScrollBarEnabled = verticalScrollbarEnabled
                    }

                    updateLayoutParams {
                        width = targetLayoutParamsWidth
                        height = targetLayoutParamsHeight
                    }
                }

                start()
            }
        }
    }
}
