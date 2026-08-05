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
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.FontAssetDelegate
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.fonts.R as FontsR
import com.duckduckgo.mobile.android.R as CommonR

/**
 * One-time welcome intro/outro choreography for [ConfigDrivenWelcomePageFragment].
 *
 * Drives `backgroundPrimary` until [clearForDialog] hands that view to the render engine's `BackgroundController`,
 * which owns it from then on, so the two never animate it at the same time.
 */
class OnboardingIntroChoreographer(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
) {

    private var introAnimatorSet: AnimatorSet? = null
    private var outroAnimatorSet: AnimatorSet? = null
    private var backgroundIntroAnimatorSet: AnimatorSet? = null

    private val state = OnboardingIntroState()

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
        // duckAiIntroAnimation starts invisible and is only ever shown on a withDuckAi run, whether it played
        // or was snapped, so its current visibility is exactly the signal for whether to fade it out too.
        if (binding.duckAiIntroAnimation.isVisible) {
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

    fun play(withDuckAi: Boolean, onFinished: () -> Unit) {
        state.play()
        // The animators measure the title's laid-out width and the screen height.
        binding.root.doOnLayout {
            if (state.canStart()) startIntroAnimation(withDuckAi, onFinished)
        }
    }

    /**
     * Snaps the intro visuals to their end state.
     *
     * @return true when this call put visuals on screen, false when they are there already.
     */
    fun restore(withDuckAi: Boolean): Boolean {
        if (!state.restore()) return false
        snapIntroViews()
        if (withDuckAi) {
            prepareDuckAiIntroAnimation()
            with(binding.duckAiIntroAnimation) {
                isVisible = true
                alpha = 1f
                progress = 1f
            }
        }
        return true
    }

    /**
     * Hands `backgroundPrimary` to an arriving dialog: fades the intro visuals out when they are on screen, snaps them
     * away when this view never showed them, and does nothing once an earlier dialog has taken over. Safe to call for
     * every dialog.
     *
     * @return true when the dialog's background can cross-fade from what is on screen
     */
    fun clearForDialog(): Boolean {
        val handover = state.handOverToDialog()
        when (handover) {
            OnboardingIntroState.Handover.FadeOut -> {
                settleRunningIntro()
                playOutro()
            }
            OnboardingIntroState.Handover.SnapAway -> snapToOutroEndState()
            OnboardingIntroState.Handover.AlreadyDismissed,
            OnboardingIntroState.Handover.AlreadyHandedOver,
            -> Unit
        }
        return handover.canCrossFadeBackground
    }

    private fun settleRunningIntro() {
        introAnimatorSet?.removeAllListeners()
        binding.logoAnimation.apply {
            removeAllAnimatorListeners()
            removeAllUpdateListeners()
            cancelAnimation()
        }
        binding.duckAiIntroAnimation.apply {
            removeAllAnimatorListeners()
            cancelAnimation()
        }
        snapIntroViews()
    }

    /**
     * Clears the intro views if they were never shown.
     * Does nothing if the intro was already on screen or has been cleared.
     */
    fun dismissUnplayed() {
        if (state.dismissUnplayed()) snapToOutroEndState()
    }

    private fun startIntroAnimation(withDuckAi: Boolean, onFinished: () -> Unit) {
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
            // Every interruption ([settleRunningIntro], [release]) detaches this listener before cancelling,
            // so it only ever observes a natural end.
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
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

    private fun snapIntroViews() {
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

        if (binding.duckAiIntroAnimation.isVisible) {
            binding.duckAiIntroAnimation.progress = 1f
        }
    }

    private fun snapToOutroEndState() {
        snapIntroViews()
        binding.welcomeTitle.alpha = 0f
        binding.logoAnimation.alpha = 0f
        binding.duckAiIntroAnimation.alpha = 0f
    }

    private fun playOutro() {
        outroAnimatorSet = buildOutroAnimatorSet().apply { start() }
    }

    fun release() {
        state.release()
        introAnimatorSet?.removeAllListeners()
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
