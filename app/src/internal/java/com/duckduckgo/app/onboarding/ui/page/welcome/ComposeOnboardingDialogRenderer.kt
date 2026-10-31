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

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingComposePageBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.BackgroundControllerImpl
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.CardAnchorResolver
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.SettledDecoration
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogState
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogStep
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogTail
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/** Hosts the Compose dialog inside the View stage. */
class ComposeOnboardingDialogRenderer(
    private val binding: ContentOnboardingComposePageBinding,
    private val contentValues: ContentValueStore,
    private val onBeforeContentBound: (LinearOnboardingStepId, ContentConfig) -> Unit,
    private val isLightMode: () -> Boolean,
    private val isAddressBarRebrandEnabled: () -> Boolean,
    isTablet: Boolean,
    cardBottomInsetPx: () -> Int,
    private val emit: (NewUserOnboardingEvent) -> Unit,
    private val execute: (ContentInteraction) -> Unit,
) {
    private val previewEditor = OnboardingPreviewEditor()
    private val dialogState = DaxOnboardingDialogState()
    private var current by mutableStateOf<BoundContent?>(null)
    private var serial = 0
    private var released = false
    private var stageConfig: DialogConfig? = null
    private var tailExtended by mutableStateOf(false)
    private val anchorResolver = CardAnchorResolver(isTablet)

    private val dismissOffsetPx = dpToPx(DaxOnboardingDialogDefaults.DismissOffset.value)

    // This local proof of concept exercises Compose inside the existing View onboarding host.
    @SuppressLint("NoComposeViewUsage")
    private val composeView = ComposeView(binding.root.context).apply {
        id = View.generateViewId()
        // The activity's default IME mode resizes only when a visible Android scroll container is registered.
        isScrollContainer = true
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        layoutParams = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            verticalBias = 0f
            constrainedHeight = true
            // The component reserves DismissOffset at its end for the dismiss button, so the end margin gives that back.
            marginStart = dpToPx(VIEW_CARD_HORIZONTAL_MARGIN_DP)
            marginEnd = marginStart - dismissOffsetPx
        }
    }
    private val background = BackgroundControllerImpl(
        OnboardingBackgroundAnimator(binding.backgroundPrimary, binding.backgroundSecondary),
    )

    // Detach callbacks still have a WindowId; Fragment.onDestroyView runs after it is cleared.
    private val boundsDetachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = endBoundsTransition()
    }
    private val embellishments = ComposeEmbellishmentController(
        binding = OnboardingStage(binding.root),
        onDecorationHidden = { applyAnchor(null) },
        cardBottomInsetPx = cardBottomInsetPx,
        cardContent = composeView,
        cardBottomMarginPx = ::tailBandHeightPx,
        canCorrectDecoration = { !dialogState.isAnimating },
        onAvailableHeightChanged = { availableHeight ->
            dialogState.availableHeightPx = availableHeight
            val height = if (availableHeight != null && current?.config?.cardArrow != CardArrowConfig.Hidden &&
                binding.daxDialogCta.layoutParams.height >= 0
            ) {
                availableHeight
            } else {
                ViewGroup.LayoutParams.WRAP_CONTENT
            }
            if (composeView.layoutParams.height != height) {
                composeView.updateLayoutParams<ConstraintLayout.LayoutParams> { this.height = height }
            }
        },
        contentBottomOverflowPx = {
            if (current?.config?.cardArrow == CardArrowConfig.Hidden || current == null) 0 else tailBandHeightPx()
        },
        unclippedContentHeightPx = {
            dialogState.contentHeightPx.takeIf { it > 0 }?.plus(dismissOffsetPx) ?: composeView.measuredHeight
        },
    )

    init {
        binding.daxDialogCta.updatePadding(top = binding.daxDialogCta.paddingTop - dismissOffsetPx)
        binding.daxDialogCta.addView(composeView)
        embellishments.resetStage()
        composeView.setContent {
            DuckDuckGoTheme(isDarkTheme = !isLightMode()) {
                OnboardingTheme {
                    val bound = current ?: return@OnboardingTheme
                    DaxOnboardingDialog(
                        contentKey = bound.contentKey,
                        title = bound.content.resolvedTitle(composeView.context),
                        state = dialogState,
                        headerImage = onboardingHeaderImage(bound.content),
                        animate = bound.animate,
                        step = bound.config.stepIndicator?.let { progress ->
                            val total = progress.total.coerceAtLeast(1)
                            val current = progress.current.coerceIn(1, total)
                            val labels = (1..total).map {
                                composeView.context.getString(R.string.onboardingStepIndicatorText, it, total)
                            }.toImmutableList()
                            DaxOnboardingDialogStep(current, labels)
                        },
                        entryDelay = if (bound.config.cardEntry == CardEntry.AfterBackgroundTransition) {
                            OnboardingBackgroundAnimator.EXIT_DURATION.milliseconds
                        } else {
                            0.milliseconds
                        },
                        boundsAnimationDuration = bound.content.boundsDuration,
                        tail = tailFor(bound.config.cardArrow),
                        tailExtended = tailExtended,
                        headerToActionsSpacing = 0.dp,
                        textHorizontalPadding = bound.content.config.titleHorizontalPadding,
                        contentHorizontalPadding = bound.content.config.contentHorizontalPadding,
                        primaryCta = bound.config.primaryCta?.let { cta(bound, it, bound.content.primaryEnabled) },
                        content = {
                            onContentReady { animated ->
                                if (current === bound && !released) {
                                    bound.content.ready(animated)
                                    if (!animated) {
                                        background.skipRunning()
                                        embellishments.skipRunning()
                                    }
                                }
                            }
                            OnboardingDialogContent(bound.content, enabled)
                        },
                    )
                    LaunchedEffect(bound, bound.config) {
                        if (current !== bound || bound.content.disposed) return@LaunchedEffect
                        if (stageConfig != null && bound.animate) {
                            androidx.transition.TransitionManager.beginDelayedTransition(
                                binding.daxDialogCta,
                                androidx.transition.ChangeBounds()
                                    .addTarget(binding.daxDialogCta)
                                    .setDuration(DaxOnboardingDialogDefaults.BoundsAnimationDuration.inWholeMilliseconds),
                            )
                        }
                        val settled = embellishments.transition(stageConfig?.embellishment, bound.config.embellishment, bound.animate)
                        applyAnchor(settled)
                        stageConfig = bound.config
                    }
                }
            }
        }
        binding.daxDialogCta.addOnAttachStateChangeListener(boundsDetachListener)
    }

    fun render(stepId: LinearOnboardingStepId, config: DialogConfig, animate: Boolean) {
        if (released) return
        val previous = current
        if (previous?.stepId == stepId && previous.config == config) return

        endBoundsTransition()

        background.apply(previous?.config?.background, config.background, animate)
        if (previous?.stepId == stepId && previous.config.content == config.content) {
            previous.config = config
            previous.animate = animate
        } else {
            previous?.content?.dispose()
            current = bind(stepId, config, animate)
        }
        binding.daxDialogCta.alpha = 1f
        binding.daxDialogCta.isVisible = true
    }

    fun skipRunningAnimations() {
        if (released) return
        endBoundsTransition()
        dialogState.skip()
        background.skipRunning()
        embellishments.skipRunning()
        current?.content?.skip()
    }

    fun release() {
        if (released) return
        released = true
        binding.daxDialogCta.removeOnAttachStateChangeListener(boundsDetachListener)
        endBoundsTransition()
        current?.content?.dispose()
        current = null
        composeView.disposeComposition()
        binding.daxDialogCta.removeView(composeView)
        background.release()
        embellishments.release()
    }

    private fun endBoundsTransition() {
        val root = binding.daxDialogCta
        if (root.windowId != null) androidx.transition.TransitionManager.endTransitions(root)
    }

    private fun bind(stepId: LinearOnboardingStepId, config: DialogConfig, animate: Boolean): BoundContent {
        onBeforeContentBound(stepId, config.content)
        return BoundContent(stepId, config, animate, "$stepId:${serial++}")
    }

    private fun cta(bound: BoundContent, config: CtaConfig, enabled: Boolean) = DaxOnboardingDialogCta(
        text = config.text.resolve(composeView.context),
        enabled = enabled,
        onClick = {
            if (current === bound && !bound.content.disposed) {
                when (val action = config.action) {
                    is CtaAction.Emit -> emit(action.event)
                    CtaAction.Submit -> bound.content.submit()?.let(emit)
                }
            }
        },
    )

    private fun applyAnchor(settled: SettledDecoration?) {
        if (released) return
        val resolution = anchorResolver.resolve(settled)
        binding.daxDialogCta.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToTop = resolution.anchorTo?.id ?: ConstraintLayout.LayoutParams.UNSET
            bottomToBottom = if (resolution.anchorTo == null) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
            verticalBias = resolution.verticalBias
        }
        tailExtended = resolution.arrowDepthFraction > 0f
    }

    private fun tailFor(arrow: CardArrowConfig) = when (arrow) {
        CardArrowConfig.Hidden -> DaxOnboardingDialogTail.None
        CardArrowConfig.AtStart -> DaxOnboardingDialogTail.BottomStartWide
        CardArrowConfig.AtEnd -> DaxOnboardingDialogTail.BottomEnd
        CardArrowConfig.AtStartMirrored -> DaxOnboardingDialogTail.BottomStartMirrored
    }

    private inner class BoundContent(
        val stepId: LinearOnboardingStepId,
        config: DialogConfig,
        animate: Boolean,
        val contentKey: String,
    ) {
        var config by mutableStateOf(config)
        var animate by mutableStateOf(animate)
        val content = OnboardingContentState(stepId, config.content, contentValues, execute, isAddressBarRebrandEnabled(), previewEditor)
    }

    private fun tailBandHeightPx(): Int {
        val tailHeight = DaxOnboardingDialogDefaults.TailBandHeight - DaxOnboardingDialogDefaults.StrokeWidth
        return (tailHeight.value * binding.root.resources.displayMetrics.density).toInt() + dpToPx(DaxOnboardingDialogDefaults.StrokeWidth.value)
    }

    private fun dpToPx(dp: Float): Int = (dp * binding.root.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val VIEW_CARD_HORIZONTAL_MARGIN_DP = 20f
    }
}
