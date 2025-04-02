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

import android.content.Context
import android.util.AttributeSet
import android.view.View
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
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection

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
            revealToolbar()
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
            revealToolbar()
        }
    }

    fun onScrollChanged(
        scrollableView: View,
        scrollY: Int,
        oldScrollY: Int,
    ) {
        if (!scrollableView.canScrollVertically(-1)) { // top of the page condition
            revealToolbar()
        } else if (!scrollableView.canScrollVertically(1)) { // bottom of the page condition
            revealMinibar()
        } else {
            val scrollDelta = scrollY - oldScrollY
            // We define that scrolling by 76dp should fully expand or fully collapse the toolbar
            val changeRatio = scrollDelta / 76.toPx(context).toFloat()
            val progress = (transitionProgress + changeRatio).coerceIn(0f, 1f)
            evaluateTransition(progress)
        }
    }

    private fun revealToolbar() {
        evaluateTransition(progress = 0f)
    }

    private fun revealMinibar() {
        evaluateTransition(progress = 1f)
    }

    private fun evaluateTransition(progress: Float) {
        if (maximumTextInputWidth == 0) {
            // the maximum input text width is only available after the layout is evaluated because it occupies all available space on screen
            maximumTextInputWidth = omnibarTextInput.width
        }

        val wasTransitioning = transitionProgress > 0
        val isTransitioning = progress > 0
        transitionProgress = progress
        val transitionInterpolation = interpolator.getInterpolation(transitionProgress)
        val justStartedTransitioning = !wasTransitioning && isTransitioning

        if (justStartedTransitioning) {
            // cancel animations at minibar starts showing
            viewModel.onStartedTransforming()
            // when the minibar is expanded, capture clicks
            setMinibarClickCaptureState(enabled = true)
        } else if (!isTransitioning) {
            // when the toolbar is expanded, forward clicks to the underlying views
            setMinibarClickCaptureState(enabled = false)
        }

        // hide toolbar views
        val toolbarViewsAlpha = 1f - transitionInterpolation
        omnibarTextInput.alpha = toolbarViewsAlpha
        aiChatDivider.alpha = toolbarViewsAlpha
        aiChat.alpha = toolbarViewsAlpha

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
}

interface FadeOmnibarItemPressedListener {
    fun onDuckChatButtonPressed()
}
