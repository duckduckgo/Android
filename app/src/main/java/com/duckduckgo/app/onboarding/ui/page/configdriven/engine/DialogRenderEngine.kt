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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.transition.ChangeBounds
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionManager
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageUpdateBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.BoundContent
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentBinder
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaAction
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfig
import com.duckduckgo.app.onboarding.ui.page.configdriven.EntranceScope
import com.duckduckgo.common.ui.view.button.DaxButton
import com.duckduckgo.common.ui.view.toPx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * The one render pipeline for every brand-design onboarding dialog screen. [render] plays the exact same
 * sequence of steps whether [DialogConfig] changes are animated or snapped straight to their end state
 * ([animate] picks the mode); it never branches on which screen preceded or follows another, only on the
 * per-axis diffs each sub-controller already knows how to resolve (background/embellishment/step-indicator/
 * card-anchor). The sole screen-type exception, carried over from Task 6, is the input-screen-preview card's
 * hidden bubble arrow (see [showArrowFor]).
 *
 * Composition, not inheritance: [contentBinder] dispatches to the per-screen dialog binder (see
 * `com.duckduckgo.app.onboarding.ui.page.configdriven.DialogBinder`/`StatefulDialogBinder`), and
 * [background]/[embellishments]/[cardAnchor]/[stepIndicator] each own one visual axis. This class only
 * orchestrates their calls, owns the shared CTA views, and drives title-typing/fade/entrance choreography that
 * doesn't belong to any single axis.
 */
class DialogRenderEngine(
    private val binding: ContentOnboardingWelcomePageUpdateBinding,
    private val contentBinder: ContentBinder,
    private val background: BackgroundController,
    private val embellishments: EmbellishmentController,
    private val cardAnchor: CardAnchorController,
    private val stepIndicator: StepIndicatorController,
    private val isTablet: Boolean,
    private val emit: (NewUserOnboardingEvent) -> Unit,
    private val execute: (ContentInteraction) -> Unit,
) {

    private var previous: DialogConfig? = null
    private var bound: BoundContent? = null

    /** The current bind's coroutine scope, cancelled at [unbindCurrent]. See [createBindScope]. */
    private var bindScope: CoroutineScope? = null

    /** Every animator this engine itself created (entrance + uniform fade), so a skip/release can settle them. */
    private val runningAnimators = mutableListOf<Animator>()

    /**
     * The [morphCard] `onEnd` continuation for whichever render is currently mid-morph, if any — set when
     * [morphCard] schedules its `ChangeBounds` transition, cleared the moment it runs. Only one morph is
     * ever in flight at a time (every [render] settles the previous one via [skipRunningAnimations] before
     * scheduling its own), so this single slot mirrors [EmbellishmentController]'s own `pendingDismiss` -
     * one in-flight non-[Animator] continuation, tracked outside [runningAnimators]. Letting
     * [skipRunningAnimations] run this early (instead of only ever firing from the transition's own
     * `onTransitionEnd`) is what lets one tap settle a render that's still mid-morph.
     */
    private var pendingMorphContinuation: (() -> Unit)? = null

    /**
     * Bumped at the top of every [render] and in [release]. [morphCard] captures this value when it sets
     * up the card's `ChangeBounds` transition; that transition's `onTransitionEnd` checks its captured
     * value against the current one before running its title/fade/entrance continuation. A mismatch means
     * a newer [render] (or [release]) has already superseded it, so it no-ops instead of replaying a stale
     * chain (old `handle`/`entrance`/`config`) against the new render's live views. Mirrors
     * [EmbellishmentController]'s identical `generation` idiom.
     *
     * [skipRunningAnimations] deliberately never bumps this: a skip settles the *current* render in place,
     * it does not supersede it, so [pendingMorphContinuation] must still run from there — just synchronously
     * instead of at the transition's natural end.
     */
    private var generation = 0

    /**
     * True for the duration of [skipRunningAnimations]. While set, [fadeIn] takes the same snap path used by
     * the non-animated branch of [render] instead of starting a new (200ms) fade — otherwise a fade kicked off
     * synchronously by [skipRunningAnimations]'s own `finishTyping()` call would only be addable to
     * [runningAnimators] (and therefore end()-able) on a *second* skip call. See [skipRunningAnimations].
     */
    private var skipping = false

    /**
     * Renders [config], animated if [animate] is true — the engine obeys [animate] verbatim and has no
     * animate policy of its own. The spec's "an empty stage always animates its entrance" rule is the VM's to
     * express through [animate]: a fresh VM (every activity entry — first show, return from a full-screen
     * detour) publishes each step's first render with `animateEntry = true`, while a rotation keeps the VM
     * whose `animateEntry` already flipped false for the showing step, so the recreated fragment/engine snaps.
     * (An earlier revision forced `previous == null` to animate here, which made rotation replay the whole
     * entrance — POC results gap 4, resolved as proposed there by making it the VM's decision.)
     *
     * Re-emission of the same [config] while already bound is a no-op — the orchestrator can re-emit its
     * current state (e.g. on a VM restart) without this engine replaying an entrance every time.
     */
    fun render(config: DialogConfig, animate: Boolean) {
        if (config == previous && bound != null) return

        generation++ // supersedes any prior render's still in-flight morph continuation - see [generation].

        val emptyStage = previous == null

        skipRunningAnimations() // never let a new render race a still-settling previous one
        unbindCurrent()
        // Fresh stage: content includes sit at their XML defaults (welcome defaults *visible*), and unbind only
        // ever hides the include this engine itself bound — reset them all before the first bind. See
        // [ContentBinder.hideAll].
        if (emptyStage) contentBinder.hideAll()

        // Per-axis diffs only: each controller sees just its own previous -> next value, never the screens.
        background.apply(previous?.background, config.background, animate)
        stepIndicator.apply(previous?.stepIndicator, config.stepIndicator, animate)

        val scope = createBindScope()
        bindScope = scope
        val newBound = contentBinder.bind(config.content, BindScope(coroutineScope = scope, emit = emit, execute = execute))
        bound = newBound
        val handle = newBound.handle
        newBound.view.isVisible = true
        bindCtaAppearance(config)

        val entrance = EntranceCollector().also { collector -> handle.entrance?.invoke(collector) }

        // Screen data, applied synchronously: legacy swaps the arrow at the same moment it begins the card's
        // bounds transition (:1535-1537). Riding the embellishment settle below instead would delay it behind
        // a previous decoration's whole exit animation (input -> input-preview hides the arrow seconds late).
        binding.daxDialogCta.cardView.setShowArrow(showArrowFor(config.content))
        applyArrowPosition(config, animate)

        embellishments.transition(previous?.embellishment, config.embellishment, animate) { settled ->
            cardAnchor.apply(settled, isTablet)
        }

        if (animate) {
            handle.fadeTargets.forEach { it.alpha = 0f }
            ctaViews(config).forEach { it.alpha = 0f }
            // Captured so the reveal/fade onEnd continuations below (fired async, possibly well after this
            // render() call returns) can tell whether they're still describing the *current* render before
            // continuing its chain / arming CTA listeners for it - see [attachCtaListeners] call site and
            // [generation]'s own doc.
            val gen = generation
            revealCard {
                if (gen != generation) return@revealCard // superseded (new render or release()) while the card was still fading in.
                morphCard {
                    handle.title?.type {
                        runAndTrack(entrance.afterTitleAnimators)
                        fadeIn(handle.fadeTargets + ctaViews(config)) {
                            runAndTrack(entrance.afterFadeAnimators)
                            // Finding 2: CTAs are armed only once their fade-in has actually completed (real time,
                            // or settled early by skipRunningAnimations()/settlePendingMorph()) - never at bind time -
                            // so a CTA sitting at alpha 0 during the entrance can't be clicked before it's visible.
                            // The generation check guards against a superseded render's fade being end()ed by a
                            // newer render's own skipRunningAnimations() call: that must not arm listeners for a
                            // config/handle this engine has already unbound.
                            if (gen == generation) attachCtaListeners(config, handle)
                        }
                    }
                }
            }
        } else {
            binding.daxDialogCta.root.isVisible = true
            binding.daxDialogCta.root.alpha = 1f
            handle.title?.snap()
            handle.fadeTargets.forEach { it.alpha = 1f }
            ctaViews(config).forEach { it.alpha = 1f }
            // Snap path: apply (not suppress) every entrance animator's final state. end() on a never-started
            // ValueAnimator fires onAnimationStart synchronously before jumping to end values; for the
            // engine-owned trigger animators binders declare (AVD/check-icon finals, the input-screen Lottie
            // loop trigger) that IS the desired settled state - legacy's own snap path also leaves the
            // input-screen Lottie loop playing (BrandDesignUpdateWelcomePage.kt:2130-2131). cancel() would
            // SUPPRESS the trigger instead (that's what release() wants) which would be wrong here.
            (entrance.afterTitleAnimators + entrance.afterFadeAnimators).forEach { it().end() }
            // Nothing async between here and bind time on this branch, so no generation race to guard against -
            // arm directly, matching the animate path's end state.
            attachCtaListeners(config, handle)
        }

        previous = config
    }

    /**
     * Tap-to-skip / reduced motion: settles everything mid-render in one call. `finishTyping()` runs
     * synchronously (see `DialogTitleController.finishTyping`'s `performClick()` idiom) all the way through
     * this render's afterTitle -> fade -> afterFade chain when typing was in flight, so by the time the drain
     * below runs, every animator that chain created is already in [runningAnimators] and gets end()ed in this
     * same call - no second tap needed. [skipping] makes [fadeIn] snap straight to the end state instead of
     * starting a real fade as part of that synchronous unwind, so there is nothing further to end() for it.
     * [drainRunningAnimators] additionally loops (snapshot-and-remove before ending, like
     * [EmbellishmentController]'s own drain) to also settle the case where this is called once a fade was
     * already genuinely running in real time before the tap: ending it fires its own onAnimationEnd, which
     * queues fresh afterFade animators outside the first snapshot - the loop's next pass ends those too.
     *
     * [settlePendingMorph] covers the earlier case, where typing hasn't even started yet because the
     * card's `ChangeBounds` morph itself is still running: it runs that render's `onEnd` continuation right
     * here instead of waiting for `onTransitionEnd`. This method never bumps [generation] itself - doing so
     * would mark the render this call is settling as superseded, when it is the opposite: this settles the
     * *current* render (or, from [release], suppresses it) in place, so its own deferred continuations must
     * still run from here, just synchronously instead of at their natural async completion.
     */
    fun skipRunningAnimations() {
        if (skipping) return
        skipping = true
        try {
            bound?.handle?.title?.finishTyping()
            settlePendingMorph()
            drainRunningAnimators { it.end() }
            // A [revealCard] fade end()ed by the drain above schedules this render's morph from its own
            // onAnimationEnd - mid-drain, after the first settlePendingMorph() already ran. Settle that morph
            // too (it finishes the typing its continuation starts), then drain the afterTitle/afterFade
            // animators that chain just created, so one tap still settles everything. Both calls are no-ops
            // when no reveal was in flight.
            settlePendingMorph()
            drainRunningAnimators { it.end() }
            embellishments.skipRunning()
            stepIndicator.skipRunning()
        } finally {
            skipping = false
        }
    }

    /**
     * Runs [morphCard]'s pending `onEnd` continuation early, if one is still waiting on the transition's
     * natural `onTransitionEnd`. Invoked under [skipping], so the [fadeIn] this continuation triggers snaps
     * instead of animating. `type()` starts [DialogTitleController]'s typing coroutine synchronously (its
     * `Job` is active the instant `launch` returns, before the coroutine's body ever runs), so the
     * immediate follow-up `finishTyping()` call below finds it already started and finishes it the same
     * way the top-of-function `finishTyping()` call finishes typing that was already in flight - one tap
     * fully settles a render that's still mid-morph. [morphCard]'s own `onTransitionEnd` finds
     * [pendingMorphContinuation] already null (or, if a newer render has since superseded it, a
     * [generation] mismatch) whenever it eventually fires for real, and no-ops either way.
     */
    private fun settlePendingMorph() {
        val continuation = pendingMorphContinuation ?: return
        pendingMorphContinuation = null
        continuation()
        bound?.handle?.title?.finishTyping()
    }

    /** Fragment onDestroyView: suppress (never apply) anything still pending, then tear down. */
    fun release() {
        generation++ // supersede any in-flight morph's onTransitionEnd - release suppresses it, it never settles it.
        pendingMorphContinuation = null
        bound?.handle?.title?.cancel()
        drainRunningAnimators { it.cancel() }
        unbindCurrent()
        embellishments.release()
        stepIndicator.release()
        // background is left alone: the fragment owns that animator instance, per spec.
    }

    private fun unbindCurrent() {
        bound?.handle?.title?.cancel() // stop a still-typing title before its handle is discarded below.
        bound?.handle?.let { it.unbind() }
        bindScope?.cancel()
        bindScope = null
        bound?.view?.isVisible = false
        bound = null
    }

    /**
     * POC exception to the no-hardcoded-coroutine-dispatcher lint rule (NoHardcodedCoroutineDispatcher):
     * this engine is a view-layer collaborator constructed directly by the fragment (see e.g.
     * [com.duckduckgo.common.ui.view.TypeAnimationTextView], suppressed the same way for the same reason),
     * not a DI-injected class that could take a `DispatcherProvider` - one bind-scoped [CoroutineScope] per
     * [render] call, cancelled at [unbindCurrent].
     */
    @Suppress("NoHardcodedCoroutineDispatcher")
    private fun createBindScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Only screen-specific branch in this engine: legacy hides the card's bubble arrow for input-screen-preview
    // alone (BrandDesignUpdateWelcomePage.kt:1537/:2230). Documented POC gap carried over from Task 6.
    private fun showArrowFor(content: ContentConfig): Boolean = content !is ContentConfig.InputScreenPreview

    // Same documented-exception status as [showArrowFor]: legacy leaves the arrow at its XML start offset only
    // on the welcome dialog and parks it ARROW_TARGET_OFFSET_END_DP from the card's end edge on every later
    // screen (:1128/:1781 et al., snap and animated branches alike).
    private fun arrowAtEndFor(content: ContentConfig): Boolean = content !is ContentConfig.Welcome

    /**
     * Horizontal arrow position, diff-driven like every other axis: legacy slides the arrow start -> end
     * across the one transition where it actually moves (welcome -> next: :1043-1051 quick setup,
     * :1668-1675 comparison chart; 400ms FastOutSlowIn, parallel with the card morph) and snaps the fraction
     * everywhere else, including all showDialogWithoutAnimation branches. The slide animator is tracked in
     * [runningAnimators], so tap-to-skip end()s it to its final fraction like any engine-owned animator.
     */
    private fun applyArrowPosition(config: DialogConfig, animate: Boolean) {
        val cardView = binding.daxDialogCta.cardView
        cardView.setArrowAnimationTarget(ARROW_TARGET_OFFSET_END_DP.toPx().toFloat())
        val atEnd = arrowAtEndFor(config.content)
        val slideAcross = animate && !skipping && atEnd && previous?.let { arrowAtEndFor(it.content) } == false
        if (slideAcross) {
            val slide = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = CARD_MORPH_DURATION_MS
                interpolator = FastOutSlowInInterpolator()
                addUpdateListener { cardView.setArrowAnimationFraction(it.animatedValue as Float) }
            }
            runningAnimators += slide
            slide.start()
        } else {
            cardView.setArrowAnimationFraction(if (atEnd) 1f else 0f)
        }
    }

    /**
     * Finding 2 fix, step 1 of 2: text/visibility only, at bind time. Deliberately leaves both CTA views
     * unclickable (`isClickable = false`, no listener) - [attachCtaListeners] arms them once the entrance's
     * fade-in has actually settled, so a CTA still fading in from alpha 0 can never be clicked early (Android
     * delivers touch events regardless of a view's alpha).
     */
    private fun bindCtaAppearance(config: DialogConfig) {
        bindCtaAppearance(binding.daxDialogCta.primaryCta, config.primaryCta)
        bindCtaAppearance(binding.daxDialogCta.secondaryCta, config.secondaryCta)
    }

    private fun bindCtaAppearance(view: DaxButton, cta: CtaConfig?) {
        view.setOnClickListener(null) // avoid retaining a listener closure over a now-unbound handle
        view.isClickable = false
        if (cta == null) {
            view.isGone = true
            return
        }
        view.isVisible = true
        view.text = cta.text.resolve(view.context)
    }

    /**
     * Finding 2 fix, step 2 of 2: arms both CTA views' real click behavior. Called only once a render's pipeline
     * has actually reached its settled/visible end state - see the two call sites in [render] for exactly when
     * that is for the animate vs. snap paths.
     */
    private fun attachCtaListeners(config: DialogConfig, handle: ContentHandle) {
        attachCtaListener(binding.daxDialogCta.primaryCta, config.primaryCta, handle)
        attachCtaListener(binding.daxDialogCta.secondaryCta, config.secondaryCta, handle)
    }

    private fun attachCtaListener(view: DaxButton, cta: CtaConfig?, handle: ContentHandle) {
        if (cta == null) return
        view.isClickable = true
        view.setOnClickListener { ctaListener(cta.action, handle)() }
    }

    private fun ctaListener(action: CtaAction, handle: ContentHandle): () -> Unit = {
        when (action) {
            is CtaAction.Emit -> emit(action.event)
            CtaAction.Submit -> handle.result?.invoke()?.let(emit)
        }
    }

    private fun ctaViews(config: DialogConfig): List<View> = listOfNotNull(
        binding.daxDialogCta.primaryCta.takeIf { config.primaryCta != null },
        binding.daxDialogCta.secondaryCta.takeIf { config.secondaryCta != null },
    )

    /**
     * Reveals the card root itself, which starts `alpha="0"` + `visibility="invisible"` in XML
     * (pre_onboarding_dax_dialog_cta_brand_design_update.xml) and is revealed by nothing else in this arm -
     * every other step of the pipeline (title typing, content/CTA fades) plays inside it. Port of legacy's
     * `fadeInDialog` (BrandDesignUpdateWelcomePage.kt:2679-2689): visible immediately, alpha fades in after
     * [CARD_FADE_IN_START_DELAY_MS]; the snap branch of [render] sets both directly, mirroring legacy's
     * `showDialogWithoutAnimation` counterpart (:1732-1733).
     *
     * Alpha already 1 means the card is already on stage (every animated render after the first), so [onEnd]
     * runs synchronously and this is a no-op - only an empty stage actually fades. Under [skipping] this snaps
     * to the end state like [fadeIn], for the same reason.
     */
    private fun revealCard(onEnd: () -> Unit) {
        val card = binding.daxDialogCta.root
        card.isVisible = true
        if (card.alpha == 1f) {
            onEnd()
            return
        }
        if (skipping) {
            card.alpha = 1f
            onEnd()
            return
        }
        val reveal = ObjectAnimator.ofFloat(card, View.ALPHA, 1f).apply {
            startDelay = CARD_FADE_IN_START_DELAY_MS
            duration = CARD_FADE_IN_DURATION_MS
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
        }
        runningAnimators += reveal
        reveal.start()
    }

    /**
     * ChangeBounds card morph, mirroring legacy's quick-setup expansion (BrandDesignUpdateWelcomePage.kt:998-1040).
     *
     * [onEnd] is deferred until the transition's own `onTransitionEnd` fires (naturally, ~[CARD_MORPH_DURATION_MS]
     * later) or until [skipRunningAnimations] runs it early via [pendingMorphContinuation] - see both for why a
     * [generation] check alone is not enough (a superseding [render] settles this render's continuation
     * synchronously rather than merely discarding it, so [pendingMorphContinuation] must also be checked here to
     * avoid running it a second time when the real `onTransitionEnd` eventually fires).
     *
     * Known limitation: a same-frame double [render] can strand a continuation until the next skip/render
     * (TransitionManager absorbs a second `beginDelayedTransition` for a root with a pending transition);
     * recovered by the next [render]/[skipRunningAnimations] - acceptable POC limitation.
     */
    private fun morphCard(onEnd: () -> Unit) {
        if (!binding.root.isLaidOut) {
            // beginDelayedTransition no-ops on a not-yet-laid-out root and onTransitionEnd never fires;
            // run the continuation directly — the first layout pass places everything, only the 400ms bounds
            // tween is lost.
            onEnd()
            return
        }
        val gen = generation
        val transition: Transition = ChangeBounds().setDuration(CARD_MORPH_DURATION_MS)
        transition.addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                if (gen != generation) return // superseded by a newer render (or release()) before this morph naturally finished.
                if (pendingMorphContinuation == null) return // already run synchronously by skipRunningAnimations().
                pendingMorphContinuation = null
                onEnd()
            }
        })
        pendingMorphContinuation = onEnd
        // ViewBinding types root as View (multiple layout variants); the page root is always a ViewGroup.
        TransitionManager.beginDelayedTransition(binding.root as ViewGroup, transition)
        // Defensive: guarantees a layout pass is observed even if none of this render's view mutations
        // happened to trigger one on their own.
        binding.root.requestLayout()
    }

    /** One AnimatorSet fading every view in [views] to alpha 1, tracked so a skip can end() it early. */
    private fun fadeIn(views: List<View>, onEnd: () -> Unit) {
        if (skipping) {
            views.forEach { it.alpha = 1f }
            onEnd()
            return
        }
        if (views.isEmpty()) {
            onEnd()
            return
        }
        val fade = AnimatorSet().apply {
            playTogether(views.map { view -> ObjectAnimator.ofFloat(view, View.ALPHA, 1f).setDuration(DIALOG_CONTENT_FADE_DURATION_MS) })
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = onEnd()
            })
        }
        runningAnimators += fade
        fade.start()
    }

    /** Creates each entrance animator, tracks it, and starts it for real - the engine owns start()/end()/cancel(). */
    private fun runAndTrack(factories: List<() -> Animator>) {
        factories.forEach { factory ->
            val animator = factory()
            runningAnimators += animator
            animator.start()
        }
    }

    // Shared by skipRunningAnimations() (end(), settle mid-render) and release() (cancel(), suppress on teardown).
    // Snapshot-and-remove *before* invoking [operation]: an end()/cancel() listener can synchronously chain into
    // creating further animators (e.g. a fade's onAnimationEnd running runAndTrack for its afterFade animators) -
    // clearing first means those land in a fresh list the while loop drains on its next pass, rather than being
    // silently dropped by a later unconditional clear().
    private fun drainRunningAnimators(operation: (Animator) -> Unit) {
        while (runningAnimators.isNotEmpty()) {
            val animators = runningAnimators.toList()
            runningAnimators.removeAll(animators)
            animators.forEach(operation)
        }
    }

    /** Collects [EntranceScope.afterFade]/[EntranceScope.afterTitle] factories declared by a bound screen's [ContentHandle.entrance]. */
    private class EntranceCollector : EntranceScope {
        val afterFadeAnimators = mutableListOf<() -> Animator>()
        val afterTitleAnimators = mutableListOf<() -> Animator>()

        override fun afterFade(animator: () -> Animator) {
            afterFadeAnimators += animator
        }

        override fun afterTitle(animator: () -> Animator) {
            afterTitleAnimators += animator
        }
    }

    private companion object {
        // Ported from BrandDesignUpdateWelcomePage.kt: DIALOG_TRANSITION_DURATION (:3078) / DIALOG_CONTENT_FADE_IN_DURATION (:3070) /
        // DIALOG_FADE_IN_DURATION (:3069) + fadeInDialog's 200ms start delay (:2684) / ARROW_TARGET_OFFSET_END_DP (:3084).
        const val CARD_MORPH_DURATION_MS = 400L
        const val DIALOG_CONTENT_FADE_DURATION_MS = 200L
        const val CARD_FADE_IN_DURATION_MS = 400L
        const val CARD_FADE_IN_START_DELAY_MS = 200L
        const val ARROW_TARGET_OFFSET_END_DP = 80
    }
}
