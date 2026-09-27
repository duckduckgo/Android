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
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.CardEntry
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaAction
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfig
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DialogRenderEngine(
    private val content: ContentController,
    private val cardStage: CardStage,
    private val background: BackgroundController,
    private val embellishments: EmbellishmentController,
    private val cardAnchor: CardAnchorController,
    private val cardArrow: CardArrowController,
    private val stepIndicator: StepIndicatorController,
    private val emit: (NewUserOnboardingEvent) -> Unit,
    private val execute: (ContentInteraction) -> Unit,
    private val onAnimatingChanged: (Boolean) -> Unit = {},
) {

    private var previousStepId: LinearOnboardingStepId? = null
    private var previous: DialogConfig? = null
    private var bound: ContentHandle? = null
    private var bindScope: CoroutineScope? = null
    private var afterFadeAnimator: Animator? = null

    /**
     * The bound render's animation policy while its entrance is in flight; null once the entrance has run its
     * course, animated, snapped or skipped, and the policy has expired.
     */
    private var entrancePolicy: Boolean? = null

    /**
     * While set, every remaining stage runs snapped. Settling an in-flight entrance unwinds the rest of the
     * chain synchronously, and those stages must land on their end state rather than start fresh animations.
     */
    private var settling = false

    private var isAnimating = false
        set(value) {
            if (field == value) return
            field = value
            onAnimatingChanged(value)
        }

    /**
     * Renders [config] for [stepId], animated when [animate].
     *
     * Re-rendering the [stepId] + [config] that is currently bound is a no-op, [animate] included, so a caller that can fire more
     * than once for one state does not need to de-duplicate itself.
     */
    fun render(
        stepId: LinearOnboardingStepId,
        config: DialogConfig,
        animate: Boolean,
    ) {
        if (stepId == previousStepId && config == previous && bound != null) return

        val freshStage = previous == null
        skipRunningAnimations()
        unbindCurrent()
        if (freshStage) {
            content.resetStage()
            embellishments.resetStage()
        }

        background.apply(previous?.background, config.background, animate)
        stepIndicator.apply(previous?.stepIndicator, config.stepIndicator, animate)
        cardArrow.apply(previous?.cardArrow, config.cardArrow, animate)

        if (animate) isAnimating = true
        entrancePolicy = animate

        val scope = createBindScope()
        bindScope = scope
        val handle = content.bind(
            stepId,
            config.content,
            BindScope(coroutineScope = scope, execute = execute, animateCardBounds = ::animateCardBounds),
        )
        bound = handle

        cardStage.setPrimaryCtaEnabled(handle.primaryCtaState?.defaultValue ?: true)
        handle.primaryCtaState?.enabled?.onEach {
            cardStage.setPrimaryCtaEnabled(it)
        }?.launchIn(scope)
        cardStage.showCtaButtons(config.primaryCta, config.secondaryCta) { cta -> performCta(cta.action, handle) }

        if (animate) cardStage.prepareEntrance(handle.preTitleFadeTargets + handle.fadeTargets)

        // Anchored before the morph below starts its transition, so the card's move to its new anchor is smooth
        val settledDecoration = embellishments.transition(previous?.embellishment, config.embellishment, animate)
        cardAnchor.apply(settledDecoration)

        // A snapped background is already in place, so there is nothing for the card to wait on.
        val revealDelayMs = if (config.cardEntry == CardEntry.AfterBackgroundTransition && animate) {
            OnboardingBackgroundAnimator.EXIT_DURATION
        } else {
            0L
        }

        // Every deferred stage below bails if the bound view has changed since dispatch
        cardStage.reveal(animate, revealDelayMs) {
            if (bound !== handle) return@reveal
            cardStage.morph(animating(animate)) {
                if (bound !== handle) return@morph
                fadeInPreTitle(handle, animating(animate)) {
                    if (bound !== handle) return@fadeInPreTitle
                    showTitle(handle, animating(animate)) {
                        if (bound !== handle) return@showTitle
                        cardStage.fadeInContent(handle.fadeTargets, animating(animate)) {
                            if (bound !== handle) return@fadeInContent
                            playAfterFade(handle, animating(animate))
                            handle.onContentReady?.invoke()
                            entrancePolicy = null
                            isAnimating = false
                        }
                    }
                }
            }
        }

        previousStepId = stepId
        previous = config
    }

    /** Tap-to-skip and reduced motion: settles the whole stage in one call. */
    fun skipRunningAnimations() {
        if (settling) return
        settling = true
        try {
            bound?.title?.finishTyping()
            cardStage.settle()
            afterFadeAnimator?.end()
            embellishments.skipRunning()
            background.skipRunning()
            stepIndicator.skipRunning()
            cardArrow.skipRunning()
            isAnimating = false
        } finally {
            settling = false
        }
    }

    /** Teardown: suppresses everything still pending instead of settling it. */
    fun release() {
        isAnimating = false
        afterFadeAnimator?.cancel()
        afterFadeAnimator = null
        unbindCurrent()
        background.release()
        cardStage.release()
        embellishments.release()
        stepIndicator.release()
        cardArrow.release()
    }

    private fun animating(animate: Boolean) = animate && !settling

    /**
     * While the entrance is running, a resize the bound screen drives follows the render's own policy, so a
     * snapped or skipped entrance stays snapped. Once the screen is on stage, the entrance policy has expired:
     * an interaction resizes the card the same way whether the screen animated in or was drawn in place.
     */
    private fun animateCardBounds(durationMs: Long) {
        if (settling || entrancePolicy == false) return
        cardStage.beginBoundsTransition(durationMs)
    }

    private fun unbindCurrent() {
        val handle = bound ?: return
        handle.title?.cancelAnimation()
        handle.unbind()
        bindScope?.cancel()
        bindScope = null
        content.hideBound()
        bound = null
    }

    /** The CTAs stay out of this phase: they belong with the content that fades in once the title has typed. */
    private fun fadeInPreTitle(
        handle: ContentHandle,
        animate: Boolean,
        onEnd: () -> Unit,
    ) {
        if (handle.preTitleFadeTargets.isEmpty()) {
            onEnd()
            return
        }
        cardStage.fadeInContent(handle.preTitleFadeTargets, animate, withCtas = false, onEnd = onEnd)
    }

    private fun showTitle(
        handle: ContentHandle,
        animate: Boolean,
        onEnd: () -> Unit,
    ) {
        val title = handle.title
        if (title == null) {
            onEnd()
        } else if (animate) {
            title.typeTitle(onEnd)
        } else {
            title.snapTitle()
            onEnd()
        }
    }

    /**
     * A snapped render starts the animator before ending it: `start()` fires the listeners a screen relies on to
     * put its views in their final state, and an `AnimatorSet`'s `end()` is a no-op while it is unstarted.
     */
    private fun playAfterFade(handle: ContentHandle, animate: Boolean) {
        val animator = handle.afterFade?.invoke() ?: return
        afterFadeAnimator = animator
        // Ending an animator that already finished restarts it, re-firing its start listeners, so drop the
        // reference the moment it completes on its own.
        animator.addListener(
            object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (afterFadeAnimator === animation) afterFadeAnimator = null
                }
            },
        )
        animator.start()
        if (!animate) animator.end()
    }

    private fun performCta(action: CtaAction, handle: ContentHandle) {
        when (action) {
            is CtaAction.Emit -> emit(action.event)
            CtaAction.Submit -> handle.result?.invoke()?.let(emit)
        }
    }

    /**
     * One bind-scoped scope per render, cancelled at unbind. This is a view-layer collaborator built directly by
     * the fragment rather than an injected class, so there is no `DispatcherProvider` to take.
     */
    @Suppress("NoHardcodedCoroutineDispatcher")
    private fun createBindScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
