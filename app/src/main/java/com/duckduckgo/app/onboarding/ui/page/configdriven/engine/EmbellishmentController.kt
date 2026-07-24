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

/**
 * The decoration [view] the engine settled on for the current dialog (fit veto already resolved),
 * and whether this decoration is allowed to anchor the dax card on phones — on tablets every
 * decoration anchors the card. `null` means no decoration is showing (either [Embellishment.None]
 * or the declared decoration lost the fit veto), so the card anchors to the parent bottom.
 */
data class SettledDecoration(
    val view: View,
    val anchorsCardOnPhone: Boolean,
    /** Card vertical bias while anchored above this decoration — legacy sets these per dialog branch, see each build site. */
    val anchoredCardBiasPhone: Float,
    val anchoredCardBiasTablet: Float,
)

/**
 * Owns the embellishment axis: which Lottie stage decoration (walking dax, bobbing dax, either
 * wing) accompanies the current dialog, its enter/exit choreography, and the fit veto that can
 * hide a declared decoration at runtime when the dialog content leaves it no room
 * ([BrandDesignUpdateOnboardingLayoutHelper] + [OnboardingDecorationFitCorrector], ported from
 * `BrandDesignUpdateWelcomePage`).
 *
 * [onDecorationHidden] fires asynchronously (from the fit corrector's pre-draw pass, not from
 * [transition]) when a previously-fitting decoration stops fitting — e.g. the keyboard opens.
 * There is no in-flight [transition] call to report through at that point, so the engine/fragment
 * must re-anchor the card from this callback directly (typically `CardAnchorController.apply(null, ...)`).
 */
class EmbellishmentController(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
    private val onDecorationHidden: () -> Unit = {},
    private val cardBottomInsetPx: () -> Int = { 0 },
) {

    /** Every animator this controller starts, so [skipRunning] can end() and [release] can cancel() them all. */
    private val trackedAnimators = mutableListOf<Animator>()

    /**
     * The wing dismiss animations are driven by Lottie's own animator (its real duration isn't
     * known ahead of time, unlike the delay-then-fade enters), so completion is reported through
     * [LottieAnimationView.addAnimatorListener] rather than a tracked [Animator] we can end()/cancel()
     * directly. This is the single in-flight slot for that — only one decoration is ever exiting
     * at a time.
     */
    private var pendingDismiss: LottieDismiss? = null

    /**
     * Bumped on every [transition] call; each call captures its own value and every deferred
     * continuation of that call (`enterNext`, an exit's `onEnd`, a dismiss's `finish` body) checks
     * it against the current value before doing anything. A transition superseded by a newer one
     * before it settled sees a mismatch and no-ops — it never enters its decoration and never
     * fires its [onSettled] a second (stale) time.
     */
    private var generation = 0

    /**
     * True for the duration of [skipRunning]. While set, [transition]'s enter step must take the
     * same snap path used for `animate == false` instead of starting a new enter animator/Lottie
     * playback — otherwise an enter kicked off by a chained exit-onEnd during the skip's drain
     * would escape the snapshot loop and keep animating after [skipRunning] returns.
     */
    private var skipping = false

    private val fitCorrector = OnboardingDecorationFitCorrector(
        root = binding.root,
        dialog = binding.daxDialogCta.root,
        cardContainer = binding.daxDialogCta.cardContainer,
        onDecorationHidden = { onDecorationHidden() },
        cardBottomInsetPx = cardBottomInsetPx,
    )

    init {
        // This engine has no develop-parity "onboardingImprovementsV2Enabled" era to gate on — the fit
        // veto is the only behaviour, so the corrector is always on (legacy's v2-enabled branch).
        fitCorrector.enabled = true
        fitCorrector.attach()
    }

    private val decorations: Map<Embellishment, Decoration> = mapOf(
        Embellishment.WalkingDax to buildWalkingDax(),
        Embellishment.BottomWing to buildBottomWing(),
        Embellishment.LeftWing to buildLeftWing(),
        Embellishment.BobbingDax to buildBobbingDax(),
    )

    /**
     * Transitions [previous] -> [next]. Runs the fit veto for [next] and reports the settled
     * decoration through [onSettled] (null if vetoed or [Embellishment.None]) so the card can
     * anchor. When there is a previous decoration on screen, [onSettled] fires only once its exit
     * has finished — the card anchor must hold until the exiting embellishment is gone.
     */
    fun transition(
        previous: Embellishment?,
        next: Embellishment,
        animate: Boolean,
        onSettled: (SettledDecoration?) -> Unit,
    ) {
        generation++
        val gen = generation

        // Settle-then-supersede: a previous transition's exit may still be in flight (its onEnd is a
        // now-stale enterNext closure). Drain it now so the stage never has two exits running at once;
        // the gen check inside that stale enterNext (below) makes sure it no-ops instead of entering.
        drainInFlight()

        if (previous == next) {
            // the drain may have superseded this decoration's own entrance mid-flight; snap() is
            // idempotent and guarantees the settled end state.
            decorations[next]?.let { it.snap() }
            onSettled(applyFit(next))
            return
        }

        val exiting = previous?.let { decorations[it] }

        fun enterNext() {
            if (gen != generation) return // superseded by a newer transition before we got here.
            val settled = applyFit(next)
            if (settled != null) {
                val entering = decorations.getValue(next)
                if (animate && !skipping) {
                    trackedAnimators += entering.enter()
                } else {
                    entering.snap()
                }
            } else {
                // Declared but vetoed (or Embellishment.None, which has no map entry at all).
                decorations[next]?.let { instantHide(it.view) }
            }
            onSettled(settled)
        }

        when {
            exiting == null -> enterNext()
            animate && exiting.view.isVisible -> trackedAnimators += exiting.exit { enterNext() }
            else -> {
                instantHide(exiting.view)
                enterNext()
            }
        }
    }

    fun skipRunning() {
        if (skipping) return // reentrant call from a drain cascade; the outer invocation finishes the drain
        // While skipping, transition()'s enterNext takes the snap path instead of starting a new enter
        // animator, so nothing re-escapes the drain below.
        skipping = true
        try {
            // Loop until settled: draining can chain exit -> onEnd -> enterNext, and — belt-and-braces —
            // keep draining rather than assume a single pass always empties both slots.
            while (trackedAnimators.isNotEmpty() || pendingDismiss != null) {
                drainInFlight()
            }
        } finally {
            skipping = false
        }
    }

    // Ends every tracked animator and finishes any pending Lottie dismiss in one pass. Snapshot-and-remove
    // before ending: an end() listener/finish callback can synchronously chain into the next decoration's
    // enter() (exit -> onEnd -> enterNext()), adding fresh animators mid-loop — clearing the list afterwards
    // would wipe those out from under the animation that just started. Shared by [transition] (settle-then-
    // supersede) and [skipRunning] (full drain).
    private fun drainInFlight() {
        val animators = trackedAnimators.toList()
        trackedAnimators.removeAll(animators)
        animators.forEach { it.end() }
        pendingDismiss?.finish?.invoke()
    }

    fun release() {
        trackedAnimators.forEach { it.cancel() }
        trackedAnimators.clear()
        pendingDismiss?.let {
            it.view.removeAnimatorListener(it.listener)
            it.view.cancelAnimation()
        }
        pendingDismiss = null
        fitCorrector.clear()
        fitCorrector.detach()
    }

    /** Runs the fit veto for [embellishment], sizing and tracking the winning view. Ported from `applyDecorationLayout`/`applyWalkingDaxLayout`. */
    private fun applyFit(embellishment: Embellishment): SettledDecoration? {
        val decoration = decorations[embellishment] ?: return null
        releaseCardBottomInset()
        val fitHeightPx = BrandDesignUpdateOnboardingLayoutHelper.calculateDecorationHeight(
            rootView = binding.root,
            dialogView = binding.daxDialogCta.root,
            decorationView = decoration.view,
            maxHeightPx = decoration.maxHeightDp.toPx(),
            minHeightPx = decoration.minHeightDp.toPx(),
            bottomOverlapPx = decoration.bottomOverlapPx(),
        )
        return if (fitHeightPx != null) {
            decoration.view.updateLayoutParams { height = fitHeightPx }
            fitCorrector.track(
                decoration.view,
                minHeightPx = decoration.minHeightDp.toPx(),
                maxHeightPx = decoration.maxHeightDp.toPx(),
                bottomOverlapPx = decoration.bottomOverlapPx(),
            )
            SettledDecoration(
                view = decoration.view,
                anchorsCardOnPhone = decoration.anchorsCardOnPhone,
                anchoredCardBiasPhone = decoration.anchoredCardBiasPhone,
                anchoredCardBiasTablet = decoration.anchoredCardBiasTablet,
            )
        } else {
            fitCorrector.clear()
            null
        }
    }

    // A bottom-anchored predecessor can leave a bottom inset on the card; clear it before measuring a
    // decoration's fit so it does not count against the decoration's room. The corrector re-applies it
    // if this dialog ends up bottom-anchored too. Ported from `releaseCardBottomInset` (:2544-2550).
    private fun releaseCardBottomInset() {
        val params = binding.daxDialogCta.root.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        if (params.bottomMargin != 0) {
            params.bottomMargin = 0
            binding.daxDialogCta.root.layoutParams = params
        }
    }

    /** Ported from `leftWingBottomOverlapPx` (:2557-2560). */
    private fun leftWingBottomOverlapPx(): Int {
        val cardBottomMargin = (binding.daxDialogCta.cardView.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: return 0
        return (cardBottomMargin - LEFT_WING_CARD_GAP_DP.toPx()).coerceAtLeast(0)
    }

    /** Legacy always hides a decoration this way — instantly, no animation — whether via fit veto or a non-animated transition. */
    private fun instantHide(view: LottieAnimationView) {
        view.cancelAnimation()
        view.isVisible = false
    }

    private fun buildWalkingDax(): Decoration {
        val view = binding.welcomeScreenWalkingDax
        return Decoration(
            view = view,
            anchorsCardOnPhone = true,
            // Welcome keeps the card XML's bias 1 (pressed down against the dax) on both device classes:
            // legacy's welcome branch never rewrites the anchor when the dax fits (applyWalkingDaxLayout :2465-2476).
            anchoredCardBiasPhone = 1f,
            anchoredCardBiasTablet = 1f,
            maxHeightDp = WALKING_DAX_MAX_HEIGHT_DP,
            minHeightDp = WALKING_DAX_MIN_HEIGHT_DP,
            // Ported from `playWalkingDaxAnimation` (:2562-2586).
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
                    interpolator = WELCOME_DAX_INTERPOLATOR
                    startDelay = WALKING_DAX_DELAY
                    playTogether(fade, slide)
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            view.playAnimation()
                        }
                    })
                }
                set.start()
                listOf(set)
            },
            // Legacy hides walking dax instantly on every transition away from it (no dismiss animation exists for it).
            exit = { onEnd ->
                instantHide(view)
                onEnd()
                emptyList()
            },
            // Ported from the snap path (:1721-1729).
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
            // Legacy quick-setup/comparison anchor blocks (:1057-1067, :1681-1691): top-biased on phone, centered on tablet.
            anchoredCardBiasPhone = 0f,
            anchoredCardBiasTablet = 0.5f,
            maxHeightDp = BOTTOM_WING_MAX_HEIGHT_DP,
            minHeightDp = BOTTOM_WING_MIN_HEIGHT_DP,
            // Ported from `playBottomWingAnimation` (:2791-2804).
            enter = {
                view.isVisible = true
                view.alpha = 0f
                view.setMaxProgress(WING_STOP_PROGRESS)
                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    startDelay = WING_START_DELAY
                    duration = WING_FADE_IN_DURATION
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            view.playAnimation()
                        }
                    })
                }
                fadeIn.start()
                listOf(fadeIn)
            },
            // Ported from `dismissBottomWingAnimation` (:2806-2820) — ends isInvisible (space reserved).
            exit = { onEnd ->
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                dismissViaLottie(view, onEnd = onEnd, applyFinalState = { view.isInvisible = true })
                emptyList()
            },
            // Ported from the snap path (:1794-1799 / :2176-2181).
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
            // Anchors on tablet only, centered (:1257-1267, :1430-1440); the phone value is never read.
            anchoredCardBiasPhone = 0f,
            anchoredCardBiasTablet = 0.5f,
            maxHeightDp = LEFT_WING_MAX_HEIGHT_DP,
            minHeightDp = LEFT_WING_MIN_HEIGHT_DP,
            bottomOverlapPx = { leftWingBottomOverlapPx() },
            // Ported from `playLeftWingAnimation` (:2838-2851).
            enter = {
                view.isVisible = true
                view.alpha = 0f
                view.setMinAndMaxProgress(0f, WING_STOP_PROGRESS)
                val fadeIn = ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f).apply {
                    startDelay = WING_START_DELAY
                    duration = WING_FADE_IN_DURATION
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator) {
                            view.playAnimation()
                        }
                    })
                }
                fadeIn.start()
                listOf(fadeIn)
            },
            // Ported from `dismissLeftWingAnimation` (:2822-2836) — ends isGone, unlike the bottom wing's isInvisible.
            exit = { onEnd ->
                view.setMinProgress(WING_STOP_PROGRESS)
                view.setMaxProgress(1f)
                view.speed = 1f
                dismissViaLottie(view, onEnd = onEnd, applyFinalState = { view.isGone = true })
                emptyList()
            },
            // Ported from the snap path (:1920-1929 / :2076-2085).
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
            // Anchors on tablet only, centered (:1288-1298); the phone value is never read.
            anchoredCardBiasPhone = 0f,
            anchoredCardBiasTablet = 0.5f,
            maxHeightDp = BOBBING_DAX_MAX_HEIGHT_DP,
            minHeightDp = BOBBING_DAX_MIN_HEIGHT_DP,
            // Ported from `animateBobbingDaxIn` (:2588-2619).
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
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                        }
                        override fun onAnimationEnd(animation: Animator) {
                            if (!cancelled) view.playAnimation()
                        }
                    })
                }
                animator.start()
                listOf(animator)
            },
            // Ported from `animateBobbingDaxOut` (:2621-2651).
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
                    addListener(object : AnimatorListenerAdapter() {
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
                    })
                }
                animator.start()
                listOf(animator)
            },
            // Ported from the snap path (:2029-2043).
            snap = {
                view.isVisible = true
                view.alpha = 1f
                view.translationX = 0f
                if (!view.isAnimating) view.playAnimation()
            },
        )
    }

    private fun dismissViaLottie(view: LottieAnimationView, onEnd: () -> Unit, applyFinalState: () -> Unit) {
        var finished = false
        lateinit var listener: Animator.AnimatorListener
        val finish = {
            if (!finished) {
                finished = true
                view.removeAnimatorListener(listener)
                applyFinalState()
                pendingDismiss = null
                onEnd()
            }
        }
        listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                finish()
            }
        }
        view.addAnimatorListener(listener)
        pendingDismiss = LottieDismiss(view, listener, finish)
        view.playAnimation()
    }

    /** One map entry: the decoration's view, its per-decoration anchoring/fit policy, and its choreography. */
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

    private class LottieDismiss(
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

        val WELCOME_DAX_INTERPOLATOR = PathInterpolator(0.33f, 0f, 0.67f, 1f)
    }
}
