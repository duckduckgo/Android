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

package com.duckduckgo.app.onboarding.ui.page.configdriven

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.FontAssetDelegate
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.fonts.R as FontsR
import com.duckduckgo.mobile.android.R as CommonR

/**
 * One-time welcome intro/outro choreography for [ConfigDrivenWelcomePage], mechanically ported from
 * `BrandDesignUpdateWelcomePage` (:237-505 + constants :3044-3067) for this fragment alone — legacy keeps its
 * own copy, this is not a shared extraction.
 *
 * Unlike legacy, this class has no VM/flow coupling (`introInProgress`, `viewModel.onIntroAnimationFinished()`
 * calls): [playIntroAnimation] and [playOutro] take plain `onFinished`/`onEnd` callbacks instead, and the last
 * `withDuckAi` value passed to [playIntroAnimation] (default `false` until a real call happens) stands in for
 * legacy's `viewModel.viewState.value.isCustomAiOnboardingFlow` read in [snapToIntroEndState] — the new VM's
 * `ViewState` does not carry that flag. A fragment recreated mid-flow (rotation) for a custom-AI plan therefore
 * skips the `duckAiIntroAnimation` snap branch in [snapToIntroEndState], since this fresh instance never called
 * [playIntroAnimation]. Documented POC gap: low severity, as that view sits behind the dialog card once any real
 * dialog is showing.
 *
 * Also deliberately drops legacy's `backgroundAnimator: OnboardingBackgroundAnimator` constructor dependency
 * (present in the task brief's sketch): none of the ported methods below call into it — only legacy's
 * `playOutroAnimation` (which also drove the background step transition) did, and that responsibility now
 * belongs to [com.duckduckgo.app.onboarding.ui.page.configdriven.engine.DialogRenderEngine] /
 * [com.duckduckgo.app.onboarding.ui.page.configdriven.engine.BackgroundController] once the engine's own
 * `render()` runs. [playOutro] here only plays the intro-views fade (title/logo/duck.ai) and reports its own
 * completion — the engine's subsequent `render(config, animate = true)` call handles the background image
 * transition to the first dialog's step as part of its normal per-axis diffing.
 */
class OnboardingIntroChoreographer(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
) {

    private var introAnimatorSet: AnimatorSet? = null
    private var outroAnimatorSet: AnimatorSet? = null
    private var backgroundIntroAnimatorSet: AnimatorSet? = null

    /** Last `withDuckAi` passed to [playIntroAnimation]; see class doc for why [snapToIntroEndState] relies on it. */
    private var withDuckAi = false

    init {
        binding.logoAnimation.apply {
            enableMergePathsForKitKatAndAbove(true)
            setMaxFrame(60) // If we go past frame 60 the logo disappears
            repeatCount = 0
        }
        binding.backgroundPrimary.enableMergePathsForKitKatAndAbove(true)
    }

    private fun buildIntroAnimatorSet(): AnimatorSet {
        val layout = binding.welcomeTitle.layout
        val maxLineWidth = (0 until layout.lineCount).maxOf { layout.getLineWidth(it) }
        val textIntroScale = (binding.welcomeTitle.width.toFloat() / maxLineWidth).coerceAtMost(MAX_TEXT_INTRO_SCALE)

        with(binding.logoAnimation) {
            scaleX = LOGO_INTRO_SCALE
            scaleY = LOGO_INTRO_SCALE
        }

        with(binding.welcomeTitle) {
            scaleX = textIntroScale
            scaleY = textIntroScale
        }

        val textFadeInterpolator = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)
        val textSlideInterpolator = PathInterpolator(0.40f, 0.00f, 0.74f, 1.00f)
        val scaleInterpolator = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        val alphaAnimator = ObjectAnimator.ofFloat(binding.welcomeTitle, View.ALPHA, 0f, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_OPACITY_DURATION
            interpolator = textFadeInterpolator
        }

        val guidelineAnimator = ObjectAnimator.ofFloat(
            binding.textGuideline,
            "guidelinePercent",
            GUIDELINE_START_PERCENT,
            GUIDELINE_END_PERCENT,
        ).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        val logoScaleX = ObjectAnimator.ofFloat(binding.logoAnimation, View.SCALE_X, LOGO_INTRO_SCALE, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = LOGO_SCALE_DURATION
            interpolator = scaleInterpolator
        }

        val logoScaleY = ObjectAnimator.ofFloat(binding.logoAnimation, View.SCALE_Y, LOGO_INTRO_SCALE, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = LOGO_SCALE_DURATION
            interpolator = scaleInterpolator
        }

        val textScaleX = ObjectAnimator.ofFloat(binding.welcomeTitle, View.SCALE_X, textIntroScale, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        val textScaleY = ObjectAnimator.ofFloat(binding.welcomeTitle, View.SCALE_Y, textIntroScale, 1f).apply {
            startDelay = TEXT_INTRO_DELAY
            duration = TEXT_INTRO_TRANSLATE_DURATION
            interpolator = textSlideInterpolator
        }

        return AnimatorSet().apply {
            playTogether(alphaAnimator, guidelineAnimator, logoScaleX, logoScaleY, textScaleX, textScaleY)
        }
    }

    private fun buildBackgroundIntroAnimatorSet(): AnimatorSet {
        val slideDistance = binding.root.resources.displayMetrics.heightPixels * BACKGROUND_SLIDE_UP_SCREEN_PERCENT
        val easing = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        with(binding.backgroundPrimary) {
            translationY = slideDistance
            scaleX = BACKGROUND_INTRO_SCALE
            scaleY = BACKGROUND_INTRO_SCALE
        }

        val slideUp = ObjectAnimator.ofFloat(binding.backgroundPrimary, View.TRANSLATION_Y, slideDistance, 0f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }
        val scaleX = ObjectAnimator.ofFloat(binding.backgroundPrimary, View.SCALE_X, BACKGROUND_INTRO_SCALE, 1f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }
        val scaleY = ObjectAnimator.ofFloat(binding.backgroundPrimary, View.SCALE_Y, BACKGROUND_INTRO_SCALE, 1f).apply {
            duration = BACKGROUND_SLIDE_UP_DURATION
            interpolator = easing
        }

        return AnimatorSet().apply {
            playTogether(slideUp, scaleX, scaleY)
        }
    }

    private fun buildOutroAnimatorSet(): AnimatorSet {
        val fadeEasing = PathInterpolator(0.33f, 0.00f, 0.67f, 1.00f)

        val logoFade = ObjectAnimator.ofFloat(binding.logoAnimation, View.ALPHA, 1f, 0f).apply {
            duration = OUTRO_FADE_DURATION
            interpolator = fadeEasing
        }

        val textFade = ObjectAnimator.ofFloat(binding.welcomeTitle, View.ALPHA, 1f, 0f).apply {
            duration = OUTRO_FADE_DURATION
            interpolator = fadeEasing
        }

        val animators = mutableListOf<Animator>(logoFade, textFade)
        if (withDuckAi) {
            val duckAiIntroFade = ObjectAnimator.ofFloat(binding.duckAiIntroAnimation, View.ALPHA, 1f, 0f).apply {
                duration = OUTRO_FADE_DURATION
                interpolator = fadeEasing
            }
            animators += duckAiIntroFade
        }

        return AnimatorSet().apply {
            playTogether(animators)
        }
    }

    /** Plays the one-time welcome intro (logo drop, title type-in, background slide-up, optional duck.ai extension). */
    fun playIntroAnimation(withDuckAi: Boolean, onFinished: () -> Unit) {
        this.withDuckAi = withDuckAi

        binding.backgroundPrimary.setMinFrame(BACKGROUND_MIN_FRAME)
        backgroundIntroAnimatorSet = buildBackgroundIntroAnimatorSet()

        binding.logoAnimation.apply {
            var bgStarted = false
            addAnimatorUpdateListener {
                // Start background animation once when logo reaches the "drop" frame
                if (!bgStarted && frame >= BACKGROUND_TRIGGER_LOGO_FRAME) {
                    bgStarted = true
                    binding.backgroundPrimary.playAnimation()
                    backgroundIntroAnimatorSet?.start()
                }
            }
            addAnimatorListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (!withDuckAi) onFinished()
                }
            })
            playAnimation()
        }
        introAnimatorSet = buildIntroAnimatorSet().apply {
            addListener(object : AnimatorListenerAdapter() {
                private var cancelled = false

                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (cancelled) {
                        if (withDuckAi) onFinished()
                        return
                    }
                    if (!withDuckAi) return
                    prepareDuckAiIntroAnimation()
                    binding.duckAiIntroAnimation.isVisible = true
                    binding.duckAiIntroAnimation.addAnimatorListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = onFinished()
                    })
                    binding.duckAiIntroAnimation.playAnimation()
                }
            })
            start()
        }
    }

    private fun prepareDuckAiIntroAnimation() {
        binding.duckAiIntroAnimation.apply {
            // compute the view height so that it scales correctly with font size
            val targetTextPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP,
                DUCK_AI_INTRO_TEXT_SP,
                resources.displayMetrics,
            )
            val viewHeightPx = (targetTextPx * DUCK_AI_INTRO_CANVAS_H / DUCK_AI_INTRO_TEXT_CANVAS_UNITS).toInt()
            updateLayoutParams {
                height = viewHeightPx
            }

            setFontAssetDelegate(object : FontAssetDelegate() {
                override fun fetchFont(fontFamily: String): Typeface {
                    return ResourcesCompat.getFont(context, FontsR.font.ducksansdisplay_regular)
                        ?: Typeface.DEFAULT
                }
            })

            val textColor = resolveOnboardingTextPrimary(context)
            addValueCallback(KeyPath("**", "Duck.ai"), LottieProperty.COLOR) { textColor }
            addValueCallback(KeyPath("**", "+"), LottieProperty.COLOR) { textColor }
        }
    }

    private fun resolveOnboardingTextPrimary(context: Context): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(CommonR.attr.onboardingTextPrimary, typedValue, true)
        return if (typedValue.resourceId != 0) {
            ContextCompat.getColor(context, typedValue.resourceId)
        } else {
            typedValue.data
        }
    }

    /**
     * Cancels any in-flight intro animators and settles the intro-only views (logo/title/background overlay,
     * plus the duck.ai extension if [withDuckAi]) at their post-intro appearance. Used both for a mid-flow
     * fragment recreation (this instance never played the intro live) and as a defensive settle if called while
     * this instance's own intro is still mid-flight.
     */
    fun snapToIntroEndState() {
        introAnimatorSet?.cancel()
        backgroundIntroAnimatorSet?.cancel()

        with(binding.welcomeTitle) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
        }
        binding.textGuideline.setGuidelinePercent(GUIDELINE_END_PERCENT)

        with(binding.logoAnimation) {
            alpha = 1f
            scaleX = 1f
            scaleY = 1f
            setMinFrame(BACKGROUND_MIN_FRAME)
            progress = 1f
        }

        with(binding.backgroundPrimary) {
            alpha = 1f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
            setMinFrame(BACKGROUND_MIN_FRAME)
            progress = 1f
        }
        if (withDuckAi) {
            prepareDuckAiIntroAnimation()
            with(binding.duckAiIntroAnimation) {
                isVisible = true
                progress = 1f
            }
        }
    }

    /**
     * Fades the intro-only views out (title/logo/duck.ai) and reports completion via [onEnd]. Unlike legacy's
     * `playOutroAnimation`, this does not also drive the background step transition — see class doc.
     */
    fun playOutro(onEnd: () -> Unit) {
        outroAnimatorSet = buildOutroAnimatorSet().apply {
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
            start()
        }
    }

    /** Fragment onDestroyView: cancel every animator this choreographer owns and release its Lottie listeners. */
    fun releaseIntroAnimators() {
        introAnimatorSet?.cancel()
        introAnimatorSet = null
        outroAnimatorSet?.cancel()
        outroAnimatorSet = null
        backgroundIntroAnimatorSet?.cancel()
        backgroundIntroAnimatorSet = null

        binding.logoAnimation.apply {
            removeAllAnimatorListeners()
            removeAllUpdateListeners()
            cancelAnimation()
        }
        binding.backgroundPrimary.cancelAnimation()
        binding.duckAiIntroAnimation.apply {
            removeAllAnimatorListeners()
            cancelAnimation()
        }
    }

    private companion object {
        const val GUIDELINE_START_PERCENT = 0.5f
        const val GUIDELINE_END_PERCENT = 0.39125f

        const val DUCK_AI_INTRO_TEXT_SP = 24f
        const val DUCK_AI_INTRO_CANVAS_H = 260f
        const val DUCK_AI_INTRO_TEXT_CANVAS_UNITS = 69f

        const val TEXT_INTRO_DELAY = 400L
        const val TEXT_INTRO_OPACITY_DURATION = 400L
        const val TEXT_INTRO_TRANSLATE_DURATION = 600L
        const val MAX_TEXT_INTRO_SCALE = 1.3f

        const val LOGO_INTRO_SCALE = 2.5f
        const val LOGO_SCALE_DURATION = 600L

        const val BACKGROUND_MIN_FRAME = 27
        const val BACKGROUND_TRIGGER_LOGO_FRAME = 6
        const val BACKGROUND_SLIDE_UP_DURATION = 500L
        const val BACKGROUND_SLIDE_UP_SCREEN_PERCENT = 0.15f
        const val BACKGROUND_INTRO_SCALE = 2.5f

        const val OUTRO_FADE_DURATION = 300L
    }
}
