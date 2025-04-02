/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar.experiments

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.PathInterpolator
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.Command
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.experiments.FadeOmnibarLayout.TransitionType.CompleteCurrentTransition
import com.duckduckgo.app.browser.omnibar.experiments.FadeOmnibarLayout.TransitionType.TransitionToTarget
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import kotlin.math.abs

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val minibarText: DaxTextView by lazy { findViewById(R.id.minibarText) }
    private val aiChat: ImageView by lazy { findViewById(R.id.aiChat) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val transitionedOmnibarBackground: View by lazy { findViewById(R.id.transitionedOmnibarBackground) }
    private val omniBarContainerWrapper: View by lazy { findViewById(R.id.omniBarContainerWrapper) }
    private val endIconsContainer: View by lazy { findViewById(R.id.endIconsContainer) }
    private val minibarClickSurface: View by lazy { findViewById(R.id.minibarClickSurface) }

    private val toolbarHeight: Int by lazy { context.resources.getDimension(CommonR.dimen.experimentalToolbarSize).toInt() }
    private val minibarHeight: Int by lazy { context.resources.getDimension(CommonR.dimen.experimentalMinibarSize).toInt() }
    private val omnibarContainerHeight: Int by lazy { context.resources.getDimension(CommonR.dimen.experimentalOmnibarCardSize).toInt() }
    private val cardStrokeWidth: Int by lazy { omnibarCard.strokeWidth }
    private val cardElevation: Float by lazy { omnibarCard.elevation }

    private val omnibarTextInputSize: Float by lazy { omnibarTextInput.textSize }
    private val minibarTextSize: Float by lazy { minibarText.textSize }

    private var transitionProgress = 0f
    private var maximumTextInputWidth: Int = 0

    private var isGestureInProgress: Boolean = false
    private var scrollYOnGestureStart = 0

    // ease-in-out interpolation
    private val interpolator = PathInterpolator(0.42f, 0f, 0.58f, 1f)

    private val excludedCommandsWhileMinibarVisible = setOf(
        Command.StartTrackersAnimation::class,
        Command.StartCookiesAnimation::class,
    )

    private var fadeOmnibarItemPressedListener: FadeOmnibarItemPressedListener? = null

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.FadeOmnibarLayout, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.FadeOmnibarLayout_omnibarPosition, 0)]
        inflate(context, R.layout.view_fade_omnibar, this)

        minibarClickSurface.setOnClickListener {
            revealToolbar(animated = true)
        }

        AndroidSupportInjection.inject(this)
    }

    override fun render(viewState: ViewState) {
        val experimentalViewState = viewState.copy(
            showBrowserMenu = false,
            showFireIcon = false,
            showTabsMenu = false,
        )
        super.render(experimentalViewState)

        val showChatMenu = viewState.viewMode !is ViewMode.CustomTab
        aiChat.isVisible = showChatMenu
        aiChatDivider.isVisible = viewState.showVoiceSearch || viewState.showClearButton
        spacer.isVisible = false

        minibarText.text = viewState.omnibarText.extractDomain()?.removePrefix("www.") ?: viewState.omnibarText
        omniBarContainer.isPressed = viewState.hasFocus
        if (viewState.hasFocus) {
            omnibarCard.strokeColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)
        } else {
            omnibarCard.strokeColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorOmnibarStroke)
        }
    }

    override fun processCommand(command: Command) {
        if (transitionProgress > 0 && excludedCommandsWhileMinibarVisible.contains(command::class)) {
            return
        }

        super.processCommand(command)
    }

    fun resetTransitionDelayed() {
        postDelayed(delayInMillis = 100) {
            revealToolbar(animated = false)
        }
    }

    fun onScrollViewMotionEvent(
        scrollableView: View,
        motionEvent: MotionEvent,
    ) {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                isGestureInProgress = true
                scrollYOnGestureStart = scrollableView.scrollY
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isGestureInProgress = false

                // Most of user gestures will end with a little bit of fling, so users will not be gesturing anymore once the views stop scrolling,
                // and logic from #onScrollChanged takes over.
                // However, in cases where user releases the gesture without any acceleration, we need to reconsider all the cases here as well.
                applyTopOrBottomPageConditionOrElse(scrollableView, isGestureInProgress = false) {
                    // if user released the gesture in the middle of a transition, without any direction, complete it based on progress
                    if (isTransitioning()) {
                        animateTransition(transitionType = CompleteCurrentTransition)
                    }
                }
            }
        }
    }

    fun onScrollChanged(
        scrollableView: View,
        scrollY: Int,
        oldScrollY: Int,
    ) {
        animator?.cancel()
        applyTopOrBottomPageConditionOrElse(scrollableView, isGestureInProgress) {
            val scrollDelta = scrollY - oldScrollY

            // always allow to continue the transition if it's already started
            val isTransitioning = isTransitioning()

            // always allow the transition to minibar if scrolling down
            val isScrollingDown = scrollDelta > 0

            // only allow the transition back to toolbar if the scroll since start of the gesture is past a threshold
            val scrollDeltaSinceStartOfGesture = scrollYOnGestureStart - scrollY
            val isScrollingUpPastThreshold = scrollDeltaSinceStartOfGesture > SCROLL_UP_THRESHOLD_TO_START_TRANSITION_DP.toPx(context)

            if (isTransitioning || isScrollingDown || isScrollingUpPastThreshold) {
                val changeRatio = scrollDelta / FULL_TRANSITION_SCROLL_DP.toPx(context)
                val progress = (transitionProgress + changeRatio).coerceIn(0f, 1f)
                evaluateTransition(progress)

                // schedule an animation to finish the transition in the current direction, but only if user is not gesturing anymore
                if (!isGestureInProgress) {
                    val target = if (scrollDelta > 0) {
                        1f
                    } else {
                        0f
                    }
                    animateTransition(transitionType = TransitionToTarget(target = target))
                }
            }
        }
    }

    private fun animateTransition(transitionType: TransitionType) {
        animator?.cancel()
        val currentProgress = transitionProgress

        val targetProgress = when (transitionType) {
            is CompleteCurrentTransition -> {
                if (currentProgress > 0.5f) 1f else 0f
            }

            is TransitionToTarget -> {
                transitionType.target
            }
        }

        if (currentProgress != targetProgress) {
            animator = ValueAnimator.ofFloat(currentProgress, targetProgress).apply {
                val remainingTransitionPercentage = abs(targetProgress - currentProgress)
                duration = (MAX_TRANSITION_DURATION_MS * remainingTransitionPercentage).toLong()
                interpolator = DecelerateInterpolator()
                addUpdateListener { evaluateTransition(it.animatedValue as Float) }
                start()
            }
        }
    }

    private fun isTransitioning(): Boolean {
        return transitionProgress > 0f && transitionProgress < 1f
    }

    /**
     * Checks whether the view can still be scrolled in either direction.
     * If not, reveals the toolbar (top of the page) or minibar (bottom of the page).
     * If yes, runs the logic provided in [ifNotTopOrBottomFun].
     */
    private fun applyTopOrBottomPageConditionOrElse(
        scrollableView: View,
        isGestureInProgress: Boolean,
        ifNotTopOrBottomFun: () -> Unit,
    ) {
        if (!isGestureInProgress && !scrollableView.canScrollVertically(-1)) { // top of the page condition
            revealToolbar(animated = true)
        } else if (!isGestureInProgress && !scrollableView.canScrollVertically(1)) { // bottom of the page condition
            revealMinibar()
        } else {
            ifNotTopOrBottomFun()
        }
    }

    private fun revealToolbar(animated: Boolean) {
        if (animated) {
            animateTransition(transitionType = TransitionToTarget(target = 0f))
        } else {
            animator?.cancel()
            evaluateTransition(0f)
        }
    }

    private fun revealMinibar() {
        animateTransition(transitionType = TransitionToTarget(target = 1f))
    }

    private fun evaluateTransition(progress: Float) {
        if (transitionProgress == 0f) {
            // the maximum input text width is only available after the layout is evaluated because it occupies all available space on screen
            // on top of that, icons in the toolbar can show/hide dynamically depending on the state and enabled features
            // to work around this problem, we re-measure the maximum width whenever the toolbar is fully visible
            maximumTextInputWidth = omnibarTextInput.width
        }

        val wasToolbar = transitionProgress <= 0
        val isToolbar = progress <= 0
        transitionProgress = progress
        val transitionInterpolation = interpolator.getInterpolation(transitionProgress)
        val justStartedTransitioning = wasToolbar && !isToolbar

        if (justStartedTransitioning) {
            // cancel animations at minibar starts showing
            viewModel.onStartedTransforming()
            // when the minibar is expanded, capture clicks
            setMinibarClickCaptureState(enabled = true)
        } else if (isToolbar) {
            // when the toolbar is expanded, forward clicks to the underlying views
            setMinibarClickCaptureState(enabled = false)
        }

        // hide toolbar views
        val toolbarViewsAlpha = 1f - transitionInterpolation
        omnibarTextInput.alpha = toolbarViewsAlpha
        endIconsContainer.alpha = toolbarViewsAlpha

        // show minibar views
        minibarText.alpha = transitionInterpolation
        // we fade in a background that matches toolbar's color to effectively hide the card's background
        transitionedOmnibarBackground.alpha = transitionInterpolation

        // shrink the omnibar so that the input text's width matches minibar text's width
        val textViewsWidthDifference = maximumTextInputWidth - minibarText.width
        val newInputTextWidth = toolbar.width - (textViewsWidthDifference * transitionInterpolation).toInt()
        omniBarContainerWrapper.updateLayoutParams {
            width = newInputTextWidth
        }

        // As the omnibar shrinks, offset it to compensate for buttons that are on the end side.
        // These buttons fade out but still impact the horizontal alignment, so we're compensating for it with a horizontal omnibar translation.
        val endIconsHalfWidth = endIconsContainer.width / 2f
        omniBarContainerWrapper.translationX = endIconsHalfWidth * transitionInterpolation

        // We want the minibar text to be positioned horizontally in its final location to begin with.
        // Therefore, the minibar text starts with the target translation and compensates for the omnibar's movement.
        minibarText.translationX = endIconsHalfWidth - (endIconsHalfWidth * transitionInterpolation)

        // As the transition progresses, we remove the stroke and elevation of the omnibar card.
        omnibarCard.strokeWidth = (cardStrokeWidth - (cardStrokeWidth * transitionInterpolation)).toInt()
        omnibarCard.elevation = (cardElevation - (cardElevation * transitionInterpolation))

        // Gradually scale down the input text so that as it fades out it also trends towards minibar text's size.
        val textScaleDifference = 1f - (minibarTextSize / omnibarTextInputSize)
        val targetTextScale = 1f - (textScaleDifference * transitionInterpolation)
        omnibarTextInput.scaleY = targetTextScale
        omnibarTextInput.scaleX = targetTextScale

        // Shrink the toolbar's height to match the height of the minibar.
        val toolbarMinibarHeightDifference = toolbarHeight - minibarHeight
        toolbarContainer.updateLayoutParams {
            height = toolbarHeight - (toolbarMinibarHeightDifference * transitionInterpolation).toInt()
        }

        // At the same time shrink the omnibar card so that contents start scaling down as soon as the transition starts,
        // instead of waiting until there's not enough space in the toolbar.
        val omnibarMinibarHeightDifference = omnibarContainerHeight - minibarHeight
        omniBarContainer.updateLayoutParams {
            height = omnibarContainerHeight - (omnibarMinibarHeightDifference * transitionProgress).toInt()
        }
    }

    var animator: ValueAnimator? = null

    private fun setMinibarClickCaptureState(enabled: Boolean) {
        minibarClickSurface.isClickable = enabled
        minibarClickSurface.isLongClickable = enabled
        minibarClickSurface.focusable = if (enabled) FOCUSABLE_AUTO else NOT_FOCUSABLE
    }

    /**
     * Returns a percentage (0.0 to 1.0) of how much the omnibar has shifted into the minibar.
     */
    fun getShiftRatio(): Float {
        return transitionProgress
    }

    fun setFadeOmnibarItemPressedListener(itemPressedListener: FadeOmnibarItemPressedListener) {
        fadeOmnibarItemPressedListener = itemPressedListener
        aiChat.setOnClickListener {
            fadeOmnibarItemPressedListener?.onDuckChatButtonPressed()
        }
    }

    private sealed class TransitionType {
        data object CompleteCurrentTransition : TransitionType()
        data class TransitionToTarget(val target: Float) : TransitionType()
    }

    private companion object {
        private const val MAX_TRANSITION_DURATION_MS = 300L

        // We define that scrolling by 76dp should fully expand or fully collapse the toolbar
        private const val FULL_TRANSITION_SCROLL_DP = 76f

        // We transition to minibar as soon as users starts scrolling
        // but we require a least 4 times as much of up scroll to start the transition back to the toolbar
        private const val SCROLL_UP_THRESHOLD_TO_START_TRANSITION_DP = FULL_TRANSITION_SCROLL_DP * 4
    }
}

interface FadeOmnibarItemPressedListener {
    fun onDuckChatButtonPressed()
}
