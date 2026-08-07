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

package com.duckduckgo.app.onboarding.ui.page.configdriven.engine

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.onboarding.ui.page.BrandDesignUpdateOnboardingLayoutHelper
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.OnboardingDecorationFitCorrector
import com.duckduckgo.app.onboarding.ui.page.configdriven.Embellishment
import com.duckduckgo.common.ui.view.toPx

interface EmbellishmentController {
    /**
     * Clears every decoration off the stage, so a render with no predecessor to exit does not inherit the layout
     * footprint of whichever decorations the XML leaves visible.
     */
    fun resetStage()

    /**
     * Returns what the fit check settled on, synchronously: the caller anchors the card to it in the same frame,
     * so the card's reposition is picked up by the render's card morph instead of snapping into place later.
     */
    fun transition(
        previous: Embellishment?,
        next: Embellishment,
        animate: Boolean,
    ): SettledDecoration?

    fun skipRunning()
    fun release()
}

/**
 * @param onDecorationHidden callback to the host that a decoration that used to fit no longer does
 */
class EmbellishmentControllerImpl(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
    private val onDecorationHidden: () -> Unit,
    private val cardBottomInsetPx: () -> Int,
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

    private val fitCorrector = OnboardingDecorationFitCorrector(
        root = binding.root,
        dialog = binding.daxDialogCta.root,
        cardContainer = binding.daxDialogCta.cardContainer,
        onDecorationHidden = onDecorationHidden,
        cardBottomInsetPx = cardBottomInsetPx,
    )

    private val decorations: Map<Embellishment, Decoration> = mapOf(
        Embellishment.WalkingDax to buildWalkingDax(),
        Embellishment.BottomWing to buildBottomWing(),
        Embellishment.LeftWing to buildLeftWing(),
        Embellishment.BobbingDax to buildBobbingDax(),
        Embellishment.None to buildUndecoratedBand(),
    )

    init {
        fitCorrector.enabled = true
        fitCorrector.reservesInsetAboveDecoration = true
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
        trackedAnimators.forEach { it.cancel() }
        trackedAnimators.clear()
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
        val fitHeightPx = BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(
            rootView = binding.root,
            dialogView = binding.daxDialogCta.root,
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
        val params = binding.daxDialogCta.root.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin != 0) {
            params.bottomMargin = 0
            binding.daxDialogCta.root.layoutParams = params
        }
    }

    private fun leftWingBottomOverlapPx(): Int {
        val cardBottomMargin = (binding.daxDialogCta.cardView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: return 0
        return (cardBottomMargin - LEFT_WING_CARD_GAP_DP.toPx()).coerceAtLeast(0)
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
     * comparable height rather than dropping to the parent bottom. The floor is the card's bottom inset, so
     * that a band shrunk by a tall card still covers the bottom bar; anything the band cannot cover the card
     * reserves for itself, via [OnboardingDecorationFitCorrector.reservesInsetAboveDecoration].
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
                view.setMaxProgress(WING_STOP_PROGRESS)
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
            bottomOverlapPx = { leftWingBottomOverlapPx() },
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
        const val LEFT_WING_CARD_GAP_DP = 8
        const val BOBBING_DAX_MAX_HEIGHT_DP = 156
        const val BOBBING_DAX_MIN_HEIGHT_DP = 130

        // Matches the two wings, so a screen with no decoration lands within a few dp of a wing screen.
        const val UNDECORATED_BAND_MAX_HEIGHT_DP = 199

        val WALKING_DAX_INTERPOLATOR = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    }
}
