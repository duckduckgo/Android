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
import com.duckduckgo.app.onboarding.ui.page.configdriven.BindScope
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentHandle
import com.duckduckgo.app.onboarding.ui.page.configdriven.ContentInteraction
import com.duckduckgo.app.onboarding.ui.page.configdriven.CtaAction
import com.duckduckgo.app.onboarding.ui.page.configdriven.DialogConfig
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

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
        if (freshStage) content.resetStage()

        background.apply(previous?.background, config.background, animate)
        stepIndicator.apply(previous?.stepIndicator, config.stepIndicator, animate)
        cardArrow.apply(previous?.cardArrow, config.cardArrow, animate)

        if (animate) isAnimating = true

        val scope = createBindScope()
        bindScope = scope
        val handle = content.bind(stepId, config.content, BindScope(coroutineScope = scope, execute = execute))
        bound = handle

        cardStage.showCtaButtons(config.primaryCta, config.secondaryCta) { cta -> performCta(cta.action, handle) }
        if (animate) cardStage.prepareEntrance(handle.fadeTargets)

        // Anchored before the morph below starts its transition, so the card's move to its new anchor is smooth
        val settledDecoration = embellishments.transition(previous?.embellishment, config.embellishment, animate)
        cardAnchor.apply(settledDecoration)

        // Every deferred stage below bails if the bound view has changed since dispatch
        cardStage.reveal(animate) {
            if (bound !== handle) return@reveal
            cardStage.morph(animating(animate)) {
                if (bound !== handle) return@morph
                showTitle(handle, animating(animate)) {
                    if (bound !== handle) return@showTitle
                    cardStage.fadeInContent(handle.fadeTargets, animating(animate)) {
                        if (bound !== handle) return@fadeInContent
                        playAfterFade(handle, animating(animate))
                        handle.onContentReady?.invoke()
                        isAnimating = false
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
        cardStage.release()
        embellishments.release()
        stepIndicator.release()
    }

    private fun animating(animate: Boolean) = animate && !settling

    private fun unbindCurrent() {
        val handle = bound ?: return
        handle.title?.cancelAnimation()
        handle.unbind()
        bindScope?.cancel()
        bindScope = null
        content.hideBound()
        bound = null
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
