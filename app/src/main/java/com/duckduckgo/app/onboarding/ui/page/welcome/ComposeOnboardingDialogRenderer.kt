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
import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.children
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.ContentOnboardingWelcomePageBinding
import com.duckduckgo.app.browser.databinding.PreOnboardingDaxDialogCtaBinding
import com.duckduckgo.app.onboarding.orchestrator.NewUserOnboardingEvent
import com.duckduckgo.app.onboarding.ui.page.OnboardingBackgroundAnimator
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.BackgroundControllerImpl
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.CardAnchorResolver
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.ContentControllerImpl
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.EmbellishmentControllerImpl
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.SettledDecoration
import com.duckduckgo.app.onboarding.ui.page.welcome.engine.StepIndicatorControllerImpl
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialog
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogCta
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogDefaults
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogState
import com.duckduckgo.common.ui.compose.onboarding.DaxOnboardingDialogTail
import com.duckduckgo.common.ui.compose.theme.DuckDuckGoTheme
import com.duckduckgo.common.ui.compose.theme.OnboardingTheme
import com.duckduckgo.onboarding.api.LinearOnboardingStepId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Hosts the Compose card while retaining the configured stage artwork and existing content binders. */
class ComposeOnboardingDialogRenderer(
    private val binding: ContentOnboardingWelcomePageBinding,
    private val contentValues: ContentValueStore,
    private val onBeforeContentBound: (LinearOnboardingStepId, ContentConfig) -> Unit,
    private val isLightMode: () -> Boolean,
    private val isAddressBarRebrandEnabled: () -> Boolean,
    isTablet: Boolean,
    cardBottomInsetPx: () -> Int,
    private val emit: (NewUserOnboardingEvent) -> Unit,
    private val execute: (ContentInteraction) -> Unit,
    private val coroutineScope: CoroutineScope,
) {
    private val dialogState = DaxOnboardingDialogState()
    private var current by mutableStateOf<BoundContent?>(null)
    private var serial = 0
    private var released = false
    private var stageConfig: DialogConfig? = null
    private val anchorResolver = CardAnchorResolver(isTablet)

    // This local proof of concept exercises Compose inside the existing View onboarding host.
    @SuppressLint("NoComposeViewUsage")
    private val composeView = ComposeView(binding.root.context).apply {
        id = View.generateViewId()
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val horizontalMargin = resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.keyline_4)
        layoutParams = ConstraintLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            verticalBias = 0f
            constrainedHeight = true
            marginStart = horizontalMargin
            marginEnd = horizontalMargin
        }
    }
    private val background = BackgroundControllerImpl(
        OnboardingBackgroundAnimator(binding.backgroundPrimary, binding.backgroundSecondary),
    )
    private val stepIndicator = StepIndicatorControllerImpl(binding.daxDialogCta.stepIndicator)
    // Detach callbacks still have a WindowId; Fragment.onDestroyView runs after it is cleared.
    private val boundsDetachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(view: View) = Unit
        override fun onViewDetachedFromWindow(view: View) = endBoundsTransition()
    }
    private val embellishments = EmbellishmentControllerImpl(
        binding = binding,
        onDecorationHidden = { applyAnchor(null) },
        cardBottomInsetPx = cardBottomInsetPx,
        cardContent = composeView,
        canCorrectDecoration = { !dialogState.isAnimating },
    )

    private val dialogContent: @Composable () -> Unit = content@{
        val bound = current ?: return@content
        DaxOnboardingDialog(
            contentKey = bound.contentKey,
            title = bound.title,
            state = dialogState,
            animate = bound.animate,
            entryDelay = if (bound.config.cardEntry == CardEntry.AfterBackgroundTransition) {
                OnboardingBackgroundAnimator.EXIT_DURATION.milliseconds
            } else {
                0.milliseconds
            },
            headerImage = bound.header,
            boundsAnimationDuration = bound.boundsDuration(),
            tail = tailFor(bound.config.cardArrow),
            tailExtended = bound.tailExtended,
            primaryCta = bound.config.primaryCta?.let { cta(bound, it, bound.primaryEnabled) },
            secondaryCta = bound.config.secondaryCta?.let { cta(bound, it, true) },
            content = {
                val contentEnabled = enabled
                onContentReady { animated -> if (current === bound && !released) bound.ready(animated) }
                key(bound.contentKey) {
                    AndroidView(
                        factory = { bound.gate },
                        modifier = Modifier.fillMaxWidth(),
                        onRelease = { bound.dispose() },
                        update = { bound.gate.setReady(contentEnabled) },
                    )
                }
            },
        )
        LaunchedEffect(bound, bound.config) {
            if (current !== bound || bound.disposed) return@LaunchedEffect
            if (stageConfig != null && bound.animate) {
                androidx.transition.TransitionManager.beginDelayedTransition(
                    binding.daxDialogCta.root,
                    androidx.transition.ChangeBounds()
                        .addTarget(binding.daxDialogCta.root)
                        .setDuration(DaxOnboardingDialogDefaults.BoundsAnimationDuration.inWholeMilliseconds),
                )
            }
            val settled = embellishments.transition(stageConfig?.embellishment, bound.config.embellishment, bound.animate)
            applyAnchor(settled)
            stageConfig = bound.config
        }
    }

    init {
        binding.daxDialogCta.cardView.isVisible = false
        binding.daxDialogCta.root.addView(composeView)
        embellishments.resetStage()
        composeView.setContent {
            DuckDuckGoTheme(isDarkTheme = !isLightMode()) {
                OnboardingTheme { dialogContent() }
            }
        }
        binding.daxDialogCta.root.addOnAttachStateChangeListener(boundsDetachListener)
    }

    fun render(stepId: LinearOnboardingStepId, config: DialogConfig, animate: Boolean) {
        if (released) return
        val previous = current
        if (previous?.stepId == stepId && previous.config == config) return

        endBoundsTransition()

        background.apply(previous?.config?.background, config.background, animate)
        stepIndicator.apply(previous?.config?.stepIndicator, config.stepIndicator, animate)
        if (previous?.stepId == stepId && previous.config.content == config.content) {
            previous.config = config
            previous.animate = animate
        } else {
            previous?.dispose()
            current = bind(stepId, config, animate)
        }
        binding.daxDialogCta.root.alpha = 1f
        binding.daxDialogCta.root.isVisible = true
    }

    fun skipRunningAnimations() {
        if (released) return
        endBoundsTransition()
        dialogState.skip()
        background.skipRunning()
        embellishments.skipRunning()
        stepIndicator.skipRunning()
        current?.afterFade?.end()
    }

    fun release() {
        if (released) return
        released = true
        binding.daxDialogCta.root.removeOnAttachStateChangeListener(boundsDetachListener)
        endBoundsTransition()
        current?.dispose()
        current = null
        composeView.disposeComposition()
        binding.daxDialogCta.root.removeView(composeView)
        background.release()
        embellishments.release()
        stepIndicator.release()
    }

    private fun endBoundsTransition() {
        val root = binding.daxDialogCta.root
        if (root.windowId != null) androidx.transition.TransitionManager.endTransitions(root)
    }

    private fun bind(stepId: LinearOnboardingStepId, config: DialogConfig, animate: Boolean): BoundContent {
        val contentBinding = PreOnboardingDaxDialogCtaBinding.inflate(LayoutInflater.from(composeView.context))
        val controller = ContentControllerImpl(
            binding = contentBinding,
            contentValues = contentValues,
            onBeforeContentBound = onBeforeContentBound,
            isLightMode = isLightMode,
            isAddressBarRebrandEnabled = isAddressBarRebrandEnabled,
        )
        controller.resetStage()
        val scope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))
        var boundsDuration by mutableStateOf(DaxOnboardingDialogDefaults.BoundsAnimationDuration)
        val handle = controller.bind(
            stepId,
            config.content,
            BindScope(scope, execute) { durationMs -> boundsDuration = durationMs.milliseconds },
        )
        val contentView = contentBinding.cardContainer.children.first {
            it !== contentBinding.primaryCta && it !== contentBinding.secondaryCta && it.isVisible
        }
        contentBinding.cardContainer.removeView(contentView)
        contentView.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        handle.title?.isVisible = false
        handle.preTitleFadeTargets.forEach { it.isVisible = false }
        handle.fadeTargets.forEach { it.alpha = 1f }
        contentView.alpha = 1f
        removeHeadingSpacing(contentView, handle)
        val bound = BoundContent(stepId, config, animate, "$stepId:$serial", contentView, handle, controller, scope) { boundsDuration }
        serial++
        handle.primaryCtaState?.let { ctaState ->
            scope.launch { ctaState.enabled.collect { bound.primaryEnabled = it } }
        }
        return bound
    }

    private fun cta(bound: BoundContent, config: CtaConfig, enabled: Boolean) = DaxOnboardingDialogCta(
        text = config.text.resolve(composeView.context),
        enabled = enabled,
        onClick = {
            if (current === bound && !bound.disposed) {
                when (val action = config.action) {
                    is CtaAction.Emit -> emit(action.event)
                    CtaAction.Submit -> bound.handle.result?.invoke()?.let(emit)
                }
            }
        },
    )

    private fun applyAnchor(settled: SettledDecoration?) {
        if (released) return
        val resolution = anchorResolver.resolve(settled)
        binding.daxDialogCta.root.updateLayoutParams<ConstraintLayout.LayoutParams> {
            bottomToTop = resolution.anchorTo?.id ?: ConstraintLayout.LayoutParams.UNSET
            bottomToBottom = if (resolution.anchorTo == null) ConstraintLayout.LayoutParams.PARENT_ID else ConstraintLayout.LayoutParams.UNSET
            verticalBias = resolution.verticalBias
        }
        current?.tailExtended = resolution.arrowDepthFraction > 0f
    }

    private fun tailFor(arrow: CardArrowConfig) = when (arrow) {
        CardArrowConfig.Hidden -> DaxOnboardingDialogTail.None
        CardArrowConfig.AtStart -> DaxOnboardingDialogTail.BottomStartWide
        CardArrowConfig.AtEnd -> DaxOnboardingDialogTail.BottomEnd
        CardArrowConfig.AtStartMirrored -> DaxOnboardingDialogTail.BottomStartMirrored
    }

    private fun removeHeadingSpacing(view: View, handle: ContentHandle) {
        val group = view as? ViewGroup ?: return
        val headingIndex = group.children.indexOfLast { it === handle.title || it in handle.preTitleFadeTargets }
        if (headingIndex < 0) return
        group.children.drop(headingIndex + 1).firstOrNull { it.isVisible }?.let { firstContent ->
            val params = firstContent.layoutParams as? ViewGroup.MarginLayoutParams ?: return@let
            params.topMargin = 0
            firstContent.layoutParams = params
        }
    }

    private inner class BoundContent(
        val stepId: LinearOnboardingStepId,
        config: DialogConfig,
        animate: Boolean,
        val contentKey: String,
        val view: View,
        val handle: ContentHandle,
        private val controller: ContentControllerImpl,
        private val scope: CoroutineScope,
        val boundsDuration: () -> kotlin.time.Duration,
    ) {
        var config by mutableStateOf(config)
        var animate by mutableStateOf(animate)
        var title by mutableStateOf(config.content.title.resolve(view.context))
        var header by mutableStateOf<Painter?>(null)
        var tailExtended by mutableStateOf(false)
        var primaryEnabled by mutableStateOf(handle.primaryCtaState?.defaultValue ?: true)
        val gate = NativeContentGate(view) { dialogState.skip() }
        var afterFade: Animator? = null
            private set
        var disposed = false
            private set
        private var readyDelivered = false
        private val sizingText = handle.title?.findViewById<TextView>(R.id.onboardingDialogTitleSizingText)
        private val headerView = handle.preTitleFadeTargets.filterIsInstance<ImageView>().firstOrNull()
        private var headerDrawable: Drawable? = null
        private val titleWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                title = s?.toString().orEmpty()
                // ImportComplete changes its pictogram after setting the title in the same binder callback.
                composeView.post {
                    if (current === this@BoundContent && !disposed) {
                        refreshHeader()
                        removeHeadingSpacing(view, handle)
                    }
                }
            }
        }

        init {
            title = sizingText?.text?.toString() ?: title
            sizingText?.addTextChangedListener(titleWatcher)
            refreshHeader()
            gate.setReady(!animate)
        }

        fun ready(animated: Boolean) {
            if (disposed || readyDelivered) return
            readyDelivered = true
            if (!animated) {
                background.skipRunning()
                embellishments.skipRunning()
                stepIndicator.skipRunning()
            }
            gate.setReady(true)
            val animator = handle.afterFade?.invoke()
            afterFade = animator
            animator?.addListener(
                object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        if (afterFade === animation) afterFade = null
                    }
                },
            )
            animator?.start()
            if (!animated) animator?.end()
            handle.onContentReady?.invoke()
        }

        private fun refreshHeader() {
            val drawable = headerView?.drawable
            if (drawable === headerDrawable) return
            headerDrawable = drawable
            header = drawable?.let { BitmapPainter(it.toBitmap().asImageBitmap()) }
        }

        fun dispose() {
            if (disposed) return
            disposed = true
            sizingText?.removeTextChangedListener(titleWatcher)
            afterFade?.cancel()
            afterFade = null
            handle.title?.cancelAnimation()
            handle.unbind()
            scope.cancel()
            controller.hideBound()
        }
    }

    private class NativeContentGate(content: View, private val skip: () -> Unit) : FrameLayout(content.context) {
        private val accessibility = importantForAccessibility
        private val focusability = descendantFocusability
        private var ready = true
        private var consumingGesture = false

        init {
            addView(content, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        fun setReady(ready: Boolean) {
            if (this.ready == ready) return
            this.ready = ready
            importantForAccessibility = if (ready) accessibility else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            descendantFocusability = if (ready) focusability else ViewGroup.FOCUS_BLOCK_DESCENDANTS
        }

        override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                consumingGesture = !ready
                if (consumingGesture) performClick()
            }
            return consumingGesture || super.onInterceptTouchEvent(event)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !consumingGesture && !ready) {
                consumingGesture = true
                performClick()
            }
            if (!consumingGesture) return super.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) consumingGesture = false
            return true
        }

        override fun performClick(): Boolean {
            val handled = super.performClick()
            if (!ready) {
                skip()
                return true
            }
            return handled
        }
    }
}
