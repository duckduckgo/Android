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
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.core.view.isGone
import androidx.core.view.isInvisible
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
     * [onSettled] reports what the fit check settled on. When a decoration is leaving, it fires only once that
     * exit has finished: the card must keep its anchor until the outgoing decoration is gone.
     */
    fun transition(
        previous: Embellishment?,
        next: Embellishment,
        animate: Boolean,
        onSettled: (SettledDecoration?) -> Unit,
    )

    fun skipRunning()
    fun release()
}

/**
 * Owns the embellishment axis: which Lottie decoration (walking dax, bobbing dax, either wing) accompanies the
 * current dialog, its enter and exit choreography, and the fit check that hides a declared decoration when the
 * dialog content leaves it no room.
 *
 * [onDecorationHidden] fires asynchronously, from the fit corrector's pre-draw pass rather than from
 * [transition], when a decoration that used to fit stops fitting — the keyboard opening, say. No [transition] is
 * in flight at that point, so the card has to be re-anchored straight from that callback.
 */
class EmbellishmentControllerImpl(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
    private val onDecorationHidden: () -> Unit,
    private val cardBottomInsetPx: () -> Int,
) : EmbellishmentController {

    /**
     * Every animator started here that has not finished on its own, so a superseding [transition] can end() them
     * and [release] can cancel() them. Ending an animator that already completed restarts it, re-firing the start
     * listeners that call [LottieAnimationView.playAnimation], so entries drop themselves as they complete.
     */
    private val trackedAnimators = mutableListOf<Animator>()

    /**
     * The wings leave by running their Lottie to its end, whose duration is not known up front, so completion
     * arrives through a [LottieAnimationView] listener rather than an [Animator] this controller can end() or
     * cancel() itself. Only one decoration is ever leaving at a time, so a single slot covers it.
     */
    private var pendingExit: LottieExit? = null

    /**
     * Bumped by every [transition]; each call captures the value, and every deferred continuation of that call
     * re-checks it before acting. A transition superseded before it settled sees a mismatch and no-ops, so it
     * never reports a stale [SettledDecoration].
     */
    private var generation = 0

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
    )

    init {
        fitCorrector.enabled = true
        fitCorrector.attach()
    }

    override fun transition(
        previous: Embellishment?,
        next: Embellishment,
        animate: Boolean,
        onSettled: (SettledDecoration?) -> Unit,
    ) {
        generation++
        val gen = generation

        // An earlier transition's exit may still be running, with a continuation that belongs to that older
        // generation. Draining it now keeps two exits off the stage at once, and the generation check turns the
        // drained continuation into a no-op.
        drainInFlight()

        if (previous == next) {
            // The drain may have cut this decoration's own entrance short, so snap it to where that entrance was
            // heading before reporting the fit.
            decorations[next]?.snap()
            onSettled(applyFit(next))
            return
        }

        val exiting = previous?.let { decorations[it] }

        fun applyNext(): SettledDecoration? {
            val settled = applyFit(next)
            if (settled != null) {
                val entering = decorations.getValue(next)
                if (animate) {
                    track(entering.enter())
                } else {
                    entering.snap()
                }
            } else {
                decorations[next]?.let { instantHide(it.view) }
            }
            return settled
        }

        when {
            exiting == null -> onSettled(applyNext())
            animate && exiting.view.isVisible -> {
                // The incoming decoration enters in the same frame the outgoing one starts leaving. A wing's exit
                // plays its Lottie out over several seconds, and serializing the entrance behind that leaves the
                // new decoration visibly late and out of step with the background transition. Only the card anchor
                // waits, and it settles from the exit's own completion.
                val settled = applyNext()
                track(
                    exiting.exit {
                        if (gen == generation) onSettled(settled)
                    },
                )
            }
            else -> {
                instantHide(exiting.view)
                onSettled(applyNext())
            }
        }
    }

    /**
     * [drainInFlight] ends whatever is running, so an exit's completion happens now rather than at its natural
     * end. Snapping on top of that matters for an entrance that had not started yet: ending it fires its start
     * listener, which plays the decoration's Lottie from frame 0, and the snap freezes it at its end state.
     */
    override fun skipRunning() {
        drainInFlight()
        currentDecoration?.snap()
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

    /**
     * Ends every tracked animator and finishes any pending Lottie exit. The snapshot comes first because ending an
     * animator removes it from [trackedAnimators] from inside the iteration.
     */
    private fun drainInFlight() {
        val animators = trackedAnimators.toList()
        trackedAnimators.removeAll(animators)
        animators.forEach { it.end() }
        pendingExit?.finish?.invoke()
    }

    /** Tracks [animators] and removes each again the moment it ends on its own. */
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
            maxHeightPx = decoration.maxHeightDp.toPx(),
            minHeightPx = decoration.minHeightDp.toPx(),
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
            minHeightPx = decoration.minHeightDp.toPx(),
            maxHeightPx = decoration.maxHeightDp.toPx(),
            bottomOverlapPx = decoration.bottomOverlapPx(),
        )
        return SettledDecoration(
            view = decoration.view,
            anchorsCardOnPhone = decoration.anchorsCardOnPhone,
            anchoredCardBiasPhone = decoration.anchoredCardBiasPhone,
            anchoredCardBiasTablet = decoration.anchoredCardBiasTablet,
        )
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

    private fun instantHide(view: LottieAnimationView) {
        view.cancelAnimation()
        view.isVisible = false
    }

    private fun buildWalkingDax(): Decoration {
        val view = binding.welcomeScreenWalkingDax
        return Decoration(
            view = view,
            anchorsCardOnPhone = true,
            // Bias 1 keeps the card pressed down against the dax, on phone and tablet alike.
            anchoredCardBiasPhone = 1f,
            anchoredCardBiasTablet = 1f,
            maxHeightDp = WALKING_DAX_MAX_HEIGHT_DP,
            minHeightDp = WALKING_DAX_MIN_HEIGHT_DP,
            enter = {
                val fade = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f)
                    .setDuration(WALKING_DAX_FADE_DURATION)
                val slide = ObjectAnimator.ofFloat(
                    view,
                    View.TRANSLATION_X,
                    -WALKING_DAX_START_X_DP.toPx().toFloat(),
                    -WALKING_DAX_FINAL_X_DP.toPx().toFloat(),
                ).setDuration(WALKING_DAX_SLIDE_DURATION)
                val set = AnimatorSet().apply {
                    interpolator = WALKING_DAX_INTERPOLATOR
                    startDelay = WALKING_DAX_DELAY
                    playTogether(fade, slide)
                    addListener(
                        object : AnimatorListenerAdapter() {
                            override fun onAnimationStart(animation: Animator) {
                                view.playAnimation()
                            }
                        },
                    )
                }
                set.start()
                listOf(set)
            },
            exit = { onEnd ->
                instantHide(view)
                onEnd()
                emptyList()
            },
            snap = {
                view.cancelAnimation()
                view.isVisible = true
                view.progress = 1f
                view.alpha = 1f
                view.translationX = -WALKING_DAX_FINAL_X_DP.toPx().toFloat()
            },
        )
    }

    private fun buildBottomWing(): Decoration {
        val view = binding.bottomWingAnimation
        return Decoration(
            view = view,
            anchorsCardOnPhone = true,
            anchoredCardBiasPhone = 0f,
            anchoredCardBiasTablet = 0.5f,
            maxHeightDp = BOTTOM_WING_MAX_HEIGHT_DP,
            minHeightDp = BOTTOM_WING_MIN_HEIGHT_DP,
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
            exit = { onEnd ->
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                exitViaLottie(view, onEnd = onEnd, applyFinalState = { view.isInvisible = true })
                emptyList()
            },
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
            anchorsCardOnPhone = false,
            // Anchors the card on tablet only, so the phone bias is never read.
            anchoredCardBiasPhone = 0f,
            anchoredCardBiasTablet = 0.5f,
            maxHeightDp = LEFT_WING_MAX_HEIGHT_DP,
            minHeightDp = LEFT_WING_MIN_HEIGHT_DP,
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
            exit = { onEnd ->
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                exitViaLottie(view, onEnd = onEnd, applyFinalState = { view.isGone = true })
                emptyList()
            },
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
            anchorsCardOnPhone = false,
            // Anchors the card on tablet only, so the phone bias is never read.
            anchoredCardBiasPhone = 0f,
            anchoredCardBiasTablet = 0.5f,
            maxHeightDp = BOBBING_DAX_MAX_HEIGHT_DP,
            minHeightDp = BOBBING_DAX_MIN_HEIGHT_DP,
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
            exit = { onEnd ->
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
                                onEnd()
                            }
                        },
                    )
                }
                animator.start()
                listOf(animator)
            },
            snap = {
                view.isVisible = true
                view.alpha = 1f
                view.translationX = 0f
                if (!view.isAnimating) view.playAnimation()
            },
        )
    }

    /** Plays [view]'s Lottie to its end and reports through [onEnd]. Both the listener and a drain reach the same finish, which runs once. */
    private fun exitViaLottie(
        view: LottieAnimationView,
        onEnd: () -> Unit,
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
                onEnd()
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

    /** One decoration: its view, its anchoring and fit policy, and its choreography. */
    private class Decoration(
        val view: LottieAnimationView,
        val anchorsCardOnPhone: Boolean,
        val anchoredCardBiasPhone: Float,
        val anchoredCardBiasTablet: Float,
        val maxHeightDp: Int,
        val minHeightDp: Int,
        val bottomOverlapPx: () -> Int = { 0 },
        val enter: () -> List<Animator>,
        val exit: (onEnd: () -> Unit) -> List<Animator>,
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

        val WALKING_DAX_INTERPOLATOR = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    }
}
