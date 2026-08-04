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
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaConfig
import com.duckduckgo.common.ui.view.button.DaxButton

interface CardStage {
    /** Fades the card root in. Nothing to do once the card is on stage, so only the first render of a run fades. */
    fun reveal(animate: Boolean, onEnd: () -> Unit)

    /** Tweens the card's bounds from the outgoing screen's size to the newly bound one's. */
    fun morph(animate: Boolean, onEnd: () -> Unit)

    fun showCtaButtons(primary: CtaConfig?, secondary: CtaConfig?, onClick: (CtaConfig) -> Unit)

    /** Hides [contentTargets] and the visible CTAs so an entrance can fade them in. */
    fun prepareEntrance(contentTargets: List<View>)

    fun fadeInContent(contentTargets: List<View>, animate: Boolean, onEnd: () -> Unit)

    /** Ends whatever is in flight, running its continuation now rather than at its natural completion. */
    fun settle()

    fun release()
}

class CardStageImpl(private val binding: ContentOnboardingWelcomePageUpdateBinding) : CardStage {

    private val runningAnimators = mutableListOf<Animator>()

    /**
     * The morph continuation waiting on a `ChangeBounds` that has not ended yet. Held so [settle] can run it
     * early, and nulled first so the transition's own `onTransitionEnd` does not run it a second time.
     */
    private var pendingMorph: (() -> Unit)? = null

    private var ctaViews = emptyList<View>()

    override fun reveal(animate: Boolean, onEnd: () -> Unit) {
        val card = binding.daxDialogCta.root
        card.isVisible = true
        // Alpha already 1 means the card is on stage from an earlier render, so there is nothing to fade.
        if (card.alpha == 1f) {
            onEnd()
            return
        }
        if (!animate) {
            card.alpha = 1f
            onEnd()
            return
        }
        val reveal = ObjectAnimator.ofFloat(card, View.ALPHA, 1f).apply {
            startDelay = CARD_FADE_IN_START_DELAY_MS
            duration = CARD_FADE_IN_DURATION_MS
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = onEnd()
                },
            )
        }
        track(reveal)
        reveal.start()
    }

    override fun morph(animate: Boolean, onEnd: () -> Unit) {
        // A delayed transition no-ops on a root that has not been laid out and its end callback never fires, so
        // the continuation has to run directly; the first layout pass places everything anyway.
        if (!animate || !binding.root.isLaidOut) {
            onEnd()
            return
        }
        val transition: Transition = ChangeBounds().setDuration(CARD_MORPH_DURATION_MS)
        transition.addListener(
            object : TransitionListenerAdapter() {
                override fun onTransitionEnd(transition: Transition) {
                    val continuation = pendingMorph ?: return
                    pendingMorph = null
                    continuation()
                }
            },
        )
        pendingMorph = onEnd
        // ViewBinding types the root as View because the layout has multiple variants; the page root is always
        // a ViewGroup.
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)
        // Guarantees a layout pass is observed even if none of this render's view mutations triggered one.
        binding.root.requestLayout()
    }

    override fun showCtaButtons(
        primary: CtaConfig?,
        secondary: CtaConfig?,
        onClick: (CtaConfig) -> Unit,
    ) {
        bindCta(binding.daxDialogCta.primaryCta, primary, onClick)
        bindCta(binding.daxDialogCta.secondaryCta, secondary, onClick)
        ctaViews = listOfNotNull(
            binding.daxDialogCta.primaryCta.takeIf { primary != null },
            binding.daxDialogCta.secondaryCta.takeIf { secondary != null },
        )
    }

    override fun prepareEntrance(contentTargets: List<View>) {
        (contentTargets + ctaViews).forEach { it.alpha = 0f }
    }

    override fun fadeInContent(
        contentTargets: List<View>,
        animate: Boolean,
        onEnd: () -> Unit,
    ) {
        val targets = contentTargets + ctaViews
        if (!animate) {
            targets.forEach { it.alpha = 1f }
            onEnd()
            return
        }
        if (targets.isEmpty()) {
            onEnd()
            return
        }
        val fade = AnimatorSet().apply {
            playTogether(targets.map { view -> ObjectAnimator.ofFloat(view, View.ALPHA, 1f).setDuration(CONTENT_FADE_DURATION_MS) })
            addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) = onEnd()
                },
            )
        }
        track(fade)
        fade.start()
    }

    override fun settle() {
        // Ends the transition: a superseded ChangeBounds is paused and resumed by the
        // next beginDelayedTransition rather than ended, so it would otherwise fire into the next render's slot.
        TransitionManager.endTransitions(binding.root as ViewGroup)
        pendingMorph?.let { continuation ->
            pendingMorph = null
            continuation()
        }
        drain { it.end() }
    }

    override fun release() {
        pendingMorph = null
        drain { it.cancel() }
    }

    private fun bindCta(
        view: DaxButton,
        cta: CtaConfig?,
        onClick: (CtaConfig) -> Unit,
    ) {
        // Cleared first so a listener closing over an already-unbound handle is never retained.
        view.setOnClickListener(null)
        if (cta == null) {
            view.isGone = true
            return
        }
        view.text = cta.text.resolve(view.context)
        view.isVisible = true
        view.setOnClickListener { onClick(cta) }
    }

    private fun track(animator: Animator) {
        runningAnimators += animator
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runningAnimators -= animation
                }
            },
        )
    }

    /**
     * Snapshot-and-remove before running [operation]: ending an animator can synchronously create the next one
     * in the chain, and those must be drained by the next pass rather than dropped.
     */
    private fun drain(operation: (Animator) -> Unit) {
        while (runningAnimators.isNotEmpty()) {
            val animators = runningAnimators.toList()
            runningAnimators.removeAll(animators)
            animators.forEach(operation)
        }
    }

    private companion object {
        const val CARD_FADE_IN_START_DELAY_MS = 200L
        const val CARD_FADE_IN_DURATION_MS = 400L
        const val CARD_MORPH_DURATION_MS = 400L
        const val CONTENT_FADE_DURATION_MS = 200L
    }
}
