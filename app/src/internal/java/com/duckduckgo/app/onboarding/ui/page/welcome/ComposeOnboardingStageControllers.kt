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

package com.duckduckgo.app.onboarding.ui.page.welcome

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.PathInterpolator
import androidx.annotation.VisibleForTesting
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.FontAssetDelegate
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.model.KeyPath
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.OnboardingLayoutHelper
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.EmbellishmentController
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.EmbellishmentPlacement
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.SettledDecoration
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.fonts.R as FontsR
import com.duckduckgo.mobile.android.R as CommonR

/*
 * Proof of concept, internal builds only.
 *
 * Copies of OnboardingIntroChoreographer, EmbellishmentControllerImpl and OnboardingDecorationFitCorrector,
 * adapted so the Compose onboarding dialog sample can run inside the real onboarding flow without touching the
 * production View onboarding. They exist to demonstrate the Compose dialog working; they are not a migration
 * plan. Moving the onboarding to Compose on develop would still need its own changes to the production classes.
 */

/**
 * One-time welcome intro/outro choreography for [WelcomePageFragment].
 *
 * Drives `backgroundPrimary` until [clearForDialog] hands that view to the render engine's `BackgroundController`,
 * which owns it from then on, so the two never animate it at the same time.
 */
class ComposeOnboardingIntroChoreographer(
    private val binding: OnboardingStage,
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
     */
    fun clearForDialog() {
        when (state.handOverToDialog()) {
            OnboardingIntroState.Handover.FadeOut -> {
                settleRunningIntro()
                playOutro()
            }
            OnboardingIntroState.Handover.SnapAway -> snapToOutroEndState()
            OnboardingIntroState.Handover.AlreadyGone -> Unit
        }
    }

    private fun settleRunningIntro() {
        introAnimatorSet?.removeAllListeners()
        binding.logoAnimation.apply {
            removeAllAnimatorListeners()
            removeAllUpdateListeners()
        }
        binding.duckAiIntroAnimation.removeAllAnimatorListeners()
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

        // A Lottie view replays itself on restore, possibly after this snap. Only cancelling drops that pending play,
        // and it has to come first because it also clears frame calls waiting on a composition.
        binding.logoAnimation.cancelAnimation()
        binding.backgroundPrimary.cancelAnimation()
        binding.duckAiIntroAnimation.cancelAnimation()

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

/**
 * @param onDecorationHidden callback to the host that a decoration that used to fit no longer does
 */
class ComposeEmbellishmentController(
    private val binding: OnboardingStage,
    private val onDecorationHidden: () -> Unit,
    private val cardBottomInsetPx: () -> Int,
    cardContent: View,
    private val cardBottomMarginPx: () -> Int = { 0 },
    canCorrectDecoration: () -> Boolean = { true },
    unclippedContentHeightPx: (() -> Int)? = null,
    onAvailableHeightChanged: (Int?) -> Unit = {},
    contentBottomOverflowPx: () -> Int = { 0 },
) : EmbellishmentController {

    /**
     * Every animator started here that has not finished on its own, so a superseding [transition] can end() them
     * and [release] can cancel() them.
     */
    private val trackedAnimators = mutableListOf<Animator>()

    /**
     * The wings leave by running their Lottie to its end, whose duration is not known up front, so completion
     * arrives through a [LottieAnimationView] listener rather than an [Animator] this controller can end() or
     * cancel() itself. Only one decoration is ever leaving at a time, so a single slot covers it.
     */
    private var pendingExit: LottieExit? = null

    /** The fit-approved decoration on stage, or null when the current screen shows none. What [skipRunning] snaps. */
    private var currentDecoration: Decoration? = null

    private val fitCorrector = ComposeDecorationFitCorrector(
        root = binding.root,
        dialog = binding.dialog,
        cardContainer = cardContent,
        onDecorationHidden = onDecorationHidden,
        cardBottomInsetPx = cardBottomInsetPx,
        canCorrectDecoration = canCorrectDecoration,
        unclippedContentHeightPx = unclippedContentHeightPx,
        onAvailableHeightChanged = onAvailableHeightChanged,
        contentBottomOverflowPx = contentBottomOverflowPx,
    )

    private val decorations: Map<Embellishment, Decoration> = mapOf(
        Embellishment.WalkingDax to buildWalkingDax(),
        Embellishment.BottomWing to buildBottomWing(),
        Embellishment.LeftWing to buildLeftWing(),
        Embellishment.RightWing to buildRightWing(),
        Embellishment.BobbingDax to buildBobbingDax(),
        Embellishment.None to buildUndecoratedBand(),
    )

    init {
        fitCorrector.attach()
    }

    override fun resetStage() {
        decorations.values.forEach { it.hide() }
    }

    override fun transition(
        previous: Embellishment?,
        next: Embellishment,
        animate: Boolean,
    ): SettledDecoration? {
        // An earlier transition's exit may still be running. Draining it now keeps two exits off the stage at once.
        drainInFlight()

        if (previous == next) {
            // The drain may have cut this decoration's own entrance short, so snap it to where that entrance was
            // heading before reporting the fit.
            decorations[next]?.snap?.invoke()
            val settled = applyFit(next)
            // A reused decoration can stop fitting when the incoming card is taller, and the card anchors to the
            // parent bottom without it, so it has to leave the stage too.
            if (settled == null) decorations[next]?.hide?.invoke()
            return settled
        }

        val exiting = previous?.let { decorations[it] }
        val animatedExit = exiting?.takeIf { animate && it.view.isVisible }
        if (exiting != null && animatedExit == null) exiting.hide()

        val settled = applyFit(next)
        if (settled != null) {
            val entering = decorations.getValue(next)
            if (animate) {
                track(entering.enter())
            } else {
                entering.snap()
            }
        } else {
            decorations[next]?.hide?.invoke()
        }

        // Started last so the outgoing decoration begins leaving in the same frame the incoming one enters, which
        // keeps both in sync with the background.
        animatedExit?.let { track(it.exit()) }
        return settled
    }

    override fun skipRunning() {
        drainInFlight()
        currentDecoration?.snap?.invoke()
    }

    override fun release() {
        val animators = trackedAnimators.toList()
        trackedAnimators.clear()
        animators.forEach { it.cancel() }

        pendingExit?.let {
            it.view.removeAnimatorListener(it.listener)
            it.view.cancelAnimation()
        }
        pendingExit = null
        fitCorrector.clear()
        fitCorrector.detach()
    }

    private fun drainInFlight() {
        val animators = trackedAnimators.toList()
        trackedAnimators.removeAll(animators)
        animators.forEach { it.end() }
        pendingExit?.finish?.invoke()
    }

    private fun track(animators: List<Animator>) {
        trackedAnimators += animators
        animators.forEach { animator ->
            animator.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        trackedAnimators.remove(animation)
                    }
                },
            )
        }
    }

    /**
     * Sizes [embellishment]'s view to the room the dialog leaves it and hands it to the fit corrector, or returns
     * null when it does not fit at all and the card should anchor to the parent bottom instead.
     */
    private fun applyFit(embellishment: Embellishment): SettledDecoration? {
        val decoration = decorations[embellishment]
        currentDecoration = decoration
        if (decoration == null) return null

        releaseCardBottomInset()
        val fitHeightPx = OnboardingLayoutHelper.calculateDecorationHeight(
            rootView = binding.root,
            dialogView = binding.dialog,
            decorationView = decoration.view,
            maxHeightPx = decoration.maxHeightPx(),
            minHeightPx = decoration.minHeightPx(),
            bottomOverlapPx = decoration.bottomOverlapPx(),
        )
        if (fitHeightPx == null) {
            fitCorrector.clear()
            currentDecoration = null
            return null
        }

        decoration.view.updateLayoutParams { height = fitHeightPx }
        fitCorrector.track(
            decoration.view,
            minHeightPx = decoration.minHeightPx(),
            maxHeightPx = decoration.maxHeightPx(),
            bottomOverlapPx = decoration.bottomOverlapPx(),
        )
        return SettledDecoration(view = decoration.view, placement = decoration.placement)
    }

    // A bottom-anchored predecessor can leave a bottom inset on the card. Clear it before measuring so it does not
    // count against the decoration's room; the fit corrector re-applies it if this dialog is bottom-anchored too.
    private fun releaseCardBottomInset() {
        val params = binding.dialog.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin != 0) {
            params.bottomMargin = 0
            binding.dialog.layoutParams = params
        }
    }

    private fun sideWingBottomOverlapPx(): Int {
        return (cardBottomMarginPx() - SIDE_WING_CARD_GAP_DP.toPx()).coerceAtLeast(0)
    }

    private fun buildWalkingDax(): Decoration {
        val view = binding.welcomeScreenWalkingDax
        val hide = instantHideOf(view)
        return Decoration(
            view = view,
            placement = EmbellishmentPlacement.of(Embellishment.WalkingDax),
            maxHeightPx = { WALKING_DAX_MAX_HEIGHT_DP.toPx() },
            minHeightPx = { WALKING_DAX_MIN_HEIGHT_DP.toPx() },
            enter = {
                view.isVisible = true
                view.alpha = 0f
                val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    interpolator = WALKING_DAX_INTERPOLATOR
                    startDelay = WALKING_DAX_DELAY
                    duration = WALKING_DAX_FADE_DURATION
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                view.playAnimation()
                            }
                        },
                    )
                }
                val slide = ObjectAnimator.ofFloat(
                    view,
                    View.TRANSLATION_X,
                    -WALKING_DAX_START_X_DP.toPx().toFloat(),
                    -WALKING_DAX_FINAL_X_DP.toPx().toFloat(),
                ).apply {
                    interpolator = WALKING_DAX_INTERPOLATOR
                    startDelay = WALKING_DAX_DELAY
                    duration = WALKING_DAX_SLIDE_DURATION
                }
                fade.start()
                slide.start()
                listOf(fade, slide)
            },
            exit = {
                hide()
                emptyList()
            },
            hide = hide,
            snap = {
                view.cancelAnimation()
                view.isVisible = true
                view.progress = 1f
                view.alpha = 1f
                view.translationX = -WALKING_DAX_FINAL_X_DP.toPx().toFloat()
            },
        )
    }

    /** Cancels [view]'s animation and drops its layout footprint, for a snapped render or a fit veto. */
    private fun instantHideOf(view: LottieAnimationView): () -> Unit = {
        view.cancelAnimation()
        view.isVisible = false
    }

    /**
     * A screen with no decoration still reserves the room one would have taken, so its card sits at a
     * comparable height rather than dropping to the parent bottom. The floor is the card's bottom inset, so that a
     * band shrunk by a tall card still covers the bottom bar; the card reserves whatever the band cannot cover
     * (see [ComposeDecorationFitCorrector]).
     */
    private fun buildUndecoratedBand(): Decoration {
        val view = binding.undecoratedBand
        val show = { view.isVisible = true }
        val hide = { view.isVisible = false }
        return Decoration(
            view = view,
            placement = EmbellishmentPlacement.of(Embellishment.None),
            maxHeightPx = { UNDECORATED_BAND_MAX_HEIGHT_DP.toPx() },
            minHeightPx = cardBottomInsetPx,
            enter = {
                show()
                emptyList()
            },
            exit = {
                hide()
                emptyList()
            },
            hide = hide,
            snap = show,
        )
    }

    private fun buildBottomWing(): Decoration {
        val view = binding.bottomWingAnimation
        return Decoration(
            view = view,
            placement = EmbellishmentPlacement.of(Embellishment.BottomWing),
            maxHeightPx = { BOTTOM_WING_MAX_HEIGHT_DP.toPx() },
            minHeightPx = { BOTTOM_WING_MIN_HEIGHT_DP.toPx() },
            enter = {
                view.isVisible = true
                view.alpha = 0f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    startDelay = WING_START_DELAY
                    duration = WING_FADE_IN_DURATION
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                view.playAnimation()
                            }
                        },
                    )
                }
                fadeIn.start()
                listOf(fadeIn)
            },
            exit = {
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                exitViaLottie(view, applyFinalState = { view.isGone = true })
                emptyList()
            },
            hide = instantHideOf(view),
            snap = {
                view.cancelAnimation()
                view.isVisible = true
                view.alpha = 1f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                view.progress = WING_STOP_PROGRESS
            },
        )
    }

    private fun buildLeftWing(): Decoration {
        val view = binding.leftWingAnimation
        return Decoration(
            view = view,
            placement = EmbellishmentPlacement.of(Embellishment.LeftWing),
            maxHeightPx = { LEFT_WING_MAX_HEIGHT_DP.toPx() },
            minHeightPx = { LEFT_WING_MIN_HEIGHT_DP.toPx() },
            bottomOverlapPx = { sideWingBottomOverlapPx() },
            enter = {
                view.isVisible = true
                view.alpha = 0f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    startDelay = WING_START_DELAY
                    duration = WING_FADE_IN_DURATION
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                view.playAnimation()
                            }
                        },
                    )
                }
                fadeIn.start()
                listOf(fadeIn)
            },
            exit = {
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                exitViaLottie(view, applyFinalState = { view.isGone = true })
                emptyList()
            },
            hide = instantHideOf(view),
            snap = {
                view.cancelAnimation()
                view.isVisible = true
                view.alpha = 1f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                view.progress = WING_STOP_PROGRESS
            },
        )
    }

    private fun buildRightWing(): Decoration {
        val view = binding.rightWingAnimation
        return Decoration(
            view = view,
            placement = EmbellishmentPlacement.of(Embellishment.RightWing),
            maxHeightPx = { RIGHT_WING_MAX_HEIGHT_DP.toPx() },
            minHeightPx = { RIGHT_WING_MIN_HEIGHT_DP.toPx() },
            bottomOverlapPx = { sideWingBottomOverlapPx() },
            enter = {
                view.isVisible = true
                view.alpha = 0f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    startDelay = WING_START_DELAY
                    duration = WING_FADE_IN_DURATION
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                view.playAnimation()
                            }
                        },
                    )
                }
                fadeIn.start()
                listOf(fadeIn)
            },
            exit = {
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                exitViaLottie(view, applyFinalState = { view.isGone = true })
                emptyList()
            },
            hide = instantHideOf(view),
            snap = {
                view.cancelAnimation()
                view.isVisible = true
                view.alpha = 1f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                view.progress = WING_STOP_PROGRESS
            },
        )
    }

    private fun buildBobbingDax(): Decoration {
        val view = binding.bobbingDaxAnimation
        return Decoration(
            view = view,
            placement = EmbellishmentPlacement.of(Embellishment.BobbingDax),
            maxHeightPx = { BOBBING_DAX_MAX_HEIGHT_DP.toPx() },
            minHeightPx = { BOBBING_DAX_MIN_HEIGHT_DP.toPx() },
            enter = {
                val screenWidth = binding.root.rootView.width.toFloat()
                view.isVisible = true
                view.alpha = 0f
                view.translationX = screenWidth
                var cancelled = false
                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = OnboardingBackgroundAnimator.ENTER_DURATION
                    interpolator = OnboardingBackgroundAnimator.EASE_IN_OUT
                    addUpdateListener {
                        val progress = it.animatedValue as Float
                        view.translationX = screenWidth * (1f - progress)
                        view.alpha = OnboardingBackgroundAnimator.enterAlpha(progress)
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationCancel(animation: Animator) {
                                cancelled = true
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                if (!cancelled) view.playAnimation()
                            }
                        },
                    )
                }
                animator.start()
                listOf(animator)
            },
            exit = {
                val screenWidth = binding.root.rootView.width.toFloat()
                var cancelled = false
                val animator = ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = OnboardingBackgroundAnimator.EXIT_DURATION
                    interpolator = OnboardingBackgroundAnimator.EASE_IN_OUT
                    addUpdateListener {
                        val progress = it.animatedValue as Float
                        view.translationX = -screenWidth * progress
                        view.alpha = OnboardingBackgroundAnimator.exitAlpha(progress)
                    }
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationCancel(animation: Animator) {
                                cancelled = true
                            }

                            override fun onAnimationEnd(animation: Animator) {
                                if (cancelled) return
                                view.isVisible = false
                                view.cancelAnimation()
                                view.translationX = 0f
                            }
                        },
                    )
                }
                animator.start()
                listOf(animator)
            },
            hide = instantHideOf(view),
            snap = {
                view.isVisible = true
                view.alpha = 1f
                view.translationX = 0f
                if (!view.isAnimating) view.playAnimation()
            },
        )
    }

    /** Plays [view]'s Lottie to its end. Both the listener and a drain reach the same finish, which runs once. */
    private fun exitViaLottie(
        view: LottieAnimationView,
        applyFinalState: () -> Unit,
    ) {
        var finished = false
        lateinit var listener: Animator.AnimatorListener
        val finish = {
            if (!finished) {
                finished = true
                view.removeAnimatorListener(listener)
                applyFinalState()
                pendingExit = null
            }
        }
        listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish()
            }
        }
        view.addAnimatorListener(listener)
        pendingExit = LottieExit(view, listener, finish)
        view.playAnimation()
    }

    private class Decoration(
        val view: View,
        val placement: EmbellishmentPlacement.Placement,
        val maxHeightPx: () -> Int,
        val minHeightPx: () -> Int,
        val bottomOverlapPx: () -> Int = { 0 },
        val enter: () -> List<Animator>,
        val exit: () -> List<Animator>,
        /** Removes the view and its layout footprint at once, for a snapped render or a fit veto. */
        val hide: () -> Unit,
        val snap: () -> Unit,
    )

    private class LottieExit(
        val view: LottieAnimationView,
        val listener: Animator.AnimatorListener,
        val finish: () -> Unit,
    )

    private companion object {
        const val WING_START_DELAY = 300L
        const val WING_FADE_IN_DURATION = 150L
        const val WING_STOP_PROGRESS = 0.5f

        const val WALKING_DAX_DELAY = 400L
        const val WALKING_DAX_FADE_DURATION = 100L
        const val WALKING_DAX_SLIDE_DURATION = 600L
        const val WALKING_DAX_START_X_DP = 48
        const val WALKING_DAX_FINAL_X_DP = 22
        const val WALKING_DAX_MAX_HEIGHT_DP = 274
        const val WALKING_DAX_MIN_HEIGHT_DP = 174
        const val BOTTOM_WING_MAX_HEIGHT_DP = 199
        const val BOTTOM_WING_MIN_HEIGHT_DP = 130
        const val LEFT_WING_MAX_HEIGHT_DP = 196
        const val LEFT_WING_MIN_HEIGHT_DP = 130
        const val RIGHT_WING_MAX_HEIGHT_DP = 196
        const val RIGHT_WING_MIN_HEIGHT_DP = 130
        const val SIDE_WING_CARD_GAP_DP = 8
        const val BOBBING_DAX_MAX_HEIGHT_DP = 156
        const val BOBBING_DAX_MIN_HEIGHT_DP = 130

        // Matches the two wings, so a screen with no decoration lands within a few dp of a wing screen.
        const val UNDECORATED_BAND_MAX_HEIGHT_DP = 199

        val WALKING_DAX_INTERPOLATOR = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    }
}

class ComposeDecorationFitCorrector(
    private val root: View,
    private val dialog: View,
    private val cardContainer: View,
    private val onDecorationHidden: () -> Unit = {},
    private val cardBottomInsetPx: () -> Int = { 0 },
    private val canCorrectDecoration: () -> Boolean = { true },
    private val unclippedContentHeightPx: (() -> Int)? = null,
    private val onAvailableHeightChanged: (Int?) -> Unit = {},
    private val contentBottomOverflowPx: () -> Int = { 0 },
) {

    private var decoration: View? = null
    private var minHeightPx = 0
    private var maxHeightPx = 0
    private var bottomOverlapPx = 0
    private var preDrawListener: ViewTreeObserver.OnPreDrawListener? = null

    fun attach() {
        if (preDrawListener != null) return
        val listener = ViewTreeObserver.OnPreDrawListener { correctOnce() }
        preDrawListener = listener
        root.viewTreeObserver.addOnPreDrawListener(listener)
    }

    fun detach() {
        preDrawListener?.let { root.viewTreeObserver.removeOnPreDrawListener(it) }
        preDrawListener = null
    }

    fun track(
        decoration: View,
        minHeightPx: Int,
        maxHeightPx: Int,
        bottomOverlapPx: Int = 0,
    ) {
        this.decoration = decoration
        this.minHeightPx = minHeightPx
        this.maxHeightPx = maxHeightPx
        this.bottomOverlapPx = bottomOverlapPx
    }

    fun clear() {
        decoration = null
    }

    @VisibleForTesting
    fun correctOnce(): Boolean {
        if (root.height == 0) return true

        val deco = decoration
        val decorationShown = deco != null && !deco.isGone
        val insetChanged = syncCardBottomInset(decorationShown)
        val availableSpan = if (OnboardingLayoutHelper.isInScrollableContainer(dialog, root)) null else availableCardSpan()
        onAvailableHeightChanged(
            availableSpan?.let { (it - dialog.paddingTop - dialog.paddingBottom + contentBottomOverflowPx()).coerceAtLeast(0) },
        )
        if (insetChanged) return false

        if (deco == null) return !syncCardClamp()
        if (deco.isGone) return unclippedContentHeightPx == null || !syncCardClamp()
        if (OnboardingLayoutHelper.isInScrollableContainer(dialog, root)) return true
        // Fitting a bounded viewport before its decoration can collapse the Android focus owner to zero height.
        if (unclippedContentHeightPx == null && syncCardClamp()) return false
        // Compose card morphs report intermediate measured bounds; their initial fit already used the incoming content.
        if (!canCorrectDecoration()) return true

        val viewport = if (unclippedContentHeightPx != null) cardContainer else cardContainer.parent as? View ?: return true

        // Use measured (settled) heights, not laid-out heights: during an inter-dialog ChangeBounds the
        // laid-out height is mid-animation and still reports the previous, taller dialog, which would
        // wrongly hide the decoration. measuredHeight reflects the post-transition target throughout.
        val dialogHeight = dialog.measuredHeight.takeIf { it > 0 } ?: dialog.height
        val viewportHeight = viewport.measuredHeight.takeIf { it > 0 } ?: viewport.height
        val cardContainerHeight = unclippedContentHeightPx?.invoke()
            ?: cardContainer.measuredHeight.takeIf { it > 0 } ?: cardContainer.height
        if (unclippedContentHeightPx == null && (dialogHeight == 0 || viewportHeight == 0)) return true

        val overflow = (cardContainerHeight - viewportHeight).coerceAtLeast(0)
        val available = root.height - root.paddingTop - root.paddingBottom
        val dialogParams = dialog.layoutParams as ViewGroup.MarginLayoutParams
        // The card's bottom margin never counts against the decoration's room. With a decoration shown it is
        // either zero (synced above) or a reserved inset, and the keyboard that inset clears overlays the
        // window rather than shrinking it, so `available` already covers the room the card gave up. Counting
        // it would shrink the decoration, which grows the inset, which shrinks it again, until it is gone.
        val dialogSpace = dialogHeight + overflow + dialogParams.topMargin
        val decorationParams = deco.layoutParams as ViewGroup.MarginLayoutParams

        val target = OnboardingLayoutHelper.computeDecorationHeight(
            availableContentHeight = available,
            dialogSpace = dialogSpace,
            decorationBottomMargin = decorationParams.bottomMargin,
            maxHeightPx = maxHeightPx,
            minHeightPx = minHeightPx,
            bottomOverlapPx = bottomOverlapPx,
        )

        if (target == null) {
            deco.isGone = true
            onDecorationHidden()
            return false
        }

        if (target < decorationParams.height) {
            deco.updateLayoutParams { height = target }
            return false
        }

        if (unclippedContentHeightPx != null && syncCardClamp()) return false
        return true
    }

    private fun syncCardBottomInset(decorationShown: Boolean): Boolean {
        val params = dialog.layoutParams as? ConstraintLayout.LayoutParams ?: return false
        val desired = desiredCardBottomInset(params, decorationShown)
        if (params.bottomMargin == desired) return false
        dialog.updateLayoutParams<ConstraintLayout.LayoutParams> { bottomMargin = desired }
        return true
    }

    private fun desiredCardBottomInset(
        params: ConstraintLayout.LayoutParams,
        decorationShown: Boolean,
    ): Int {
        // A decoration that leaves the card bottom-anchored sits beside or over it, covering nothing below it,
        // so the card still has to clear the inset — the keyboard.
        if (params.bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID) return cardBottomInsetPx()
        // A card anchored above a decoration reserves the part of its bottom inset the decoration does not cover:
        // the undecorated band can be shorter than an inset like the keyboard.
        val deco = decoration?.takeIf { decorationShown } ?: return 0
        return (cardBottomInsetPx() - roomBelowCard(deco)).coerceAtLeast(0)
    }

    private fun roomBelowCard(decoration: View): Int {
        val params = decoration.layoutParams as ViewGroup.MarginLayoutParams
        // The applied height, not the laid-out one: the corrector runs before the frame that lays the shrink out.
        val height = params.height.takeIf { it > 0 } ?: decoration.measuredHeight
        return height + params.bottomMargin
    }

    private fun availableCardSpan(): Int? {
        val params = dialog.layoutParams as? ConstraintLayout.LayoutParams ?: return null
        val roomBelowCard = when {
            params.bottomToBottom == ConstraintLayout.LayoutParams.PARENT_ID -> 0
            params.bottomToTop != ConstraintLayout.LayoutParams.UNSET ->
                decoration?.takeIf { !it.isGone }?.let { roomBelowCard(it) } ?: return null
            else -> return null
        }
        return root.height - root.paddingTop - root.paddingBottom - params.topMargin - params.bottomMargin - roomBelowCard
    }

    // Clamp only on overflow: constrainedHeight rounds a fitting wrap down a pixel → a permanent scrollbar.
    private fun syncCardClamp(): Boolean {
        val params = dialog.layoutParams as? ConstraintLayout.LayoutParams ?: return false
        val span = availableCardSpan() ?: return false
        val content = (unclippedContentHeightPx?.invoke() ?: cardContainer.measuredHeight).takeIf { it > 0 } ?: return false
        val shouldClamp = dialog.paddingTop + content - contentBottomOverflowPx() > span
        val maxHeight = if (shouldClamp) span.coerceAtLeast(0) else 0
        val maxHeightChanged = unclippedContentHeightPx != null && params.matchConstraintMaxHeight != maxHeight
        val height = if (shouldClamp) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
        val heightChanged = unclippedContentHeightPx != null && params.height != height
        if (params.constrainedHeight == shouldClamp && !maxHeightChanged && !heightChanged) return false
        dialog.updateLayoutParams<ConstraintLayout.LayoutParams> {
            constrainedHeight = shouldClamp
            if (unclippedContentHeightPx != null) {
                matchConstraintMaxHeight = maxHeight
                // A wrap-content probe temporarily expands Compose's viewport and resets its scroll offset before the final constrained measure.
                this.height = height
            }
        }
        return true
    }
}
