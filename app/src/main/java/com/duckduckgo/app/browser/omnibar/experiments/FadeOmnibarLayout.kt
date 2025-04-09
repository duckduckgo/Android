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
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updatePadding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val aiChat: ImageView by lazy { findViewById(R.id.aiChat) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val omniBarContentContainer: View by lazy { findViewById(R.id.omniBarContentContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }

    /**
     * Returns the [BrowserNavigationBarView] reference if it's embedded inside of this omnibar layout, otherwise, returns null.
     */
    var navigationBar: BrowserNavigationBarView? = null
        private set

    private val toolbarContainerPaddingTopWhenAtBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.experimentalToolbarContainerPaddingTopWhenAtBottom)
    }
    private val omnibarCardMarginHorizontal by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginHorizontal) }
    private val omnibarCardMarginTop by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginTop) }
    private val omnibarCardMarginBottom by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginBottom) }
    private val omnibarCardFocusedMarginHorizontal by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginHorizontal) }
    private val omnibarCardFocusedMarginTop by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginTop) }
    private val omnibarCardFocusedMarginBottom by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginBottom) }
    private val omnibarContentPadding by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarContentPadding) }
    private val omnibarContentFocusedPaddingHorizontal by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarContentFocusedPaddingHorizontal) }
    private val omnibarContentFocusedPaddingVertical by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarContentFocusedPaddingVertical) }
    private val omnibarOutlineWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineWidth) }
    private val omnibarOutlineFocusedWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineFocusedWidth) }

    private var focusAnimator : ValueAnimator? = null

    private var fadeOmnibarItemPressedListener: FadeOmnibarItemPressedListener? = null

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.FadeOmnibarLayout, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.FadeOmnibarLayout_omnibarPosition, 0)]
        val root = inflate(context, R.layout.view_fade_omnibar, this)

        AndroidSupportInjection.inject(this)

        val rootContainer = root.findViewById<LinearLayout>(R.id.rootContainer)
        val navBar = rootContainer.findViewById<BrowserNavigationBarView>(R.id.omnibarNavigationBar)
        if (omnibarPosition == OmnibarPosition.TOP) {
            rootContainer.removeView(navBar)
        } else {
            navigationBar = navBar

            // When omnibar is at the bottom, we're adding an additional space at the top
            toolbarContainer.updatePadding(
                top = toolbarContainerPaddingTopWhenAtBottom,
            )

            // at the same time, we remove that space from the navigation bar which now sits below the omnibar
            navBar.findViewById<LinearLayout>(R.id.rootView).updatePadding(
                top = 0,
            )
        }

        outlineProvider = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        focusAnimator?.cancel()
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

        val showBackArrow = viewState.hasFocus
        if (showBackArrow) {
            backIcon.show()
            searchIcon.gone()
            shieldIcon.gone()
            daxIcon.gone()
            globeIcon.gone()
            duckPlayerIcon.gone()
        } else {
            backIcon.gone()
        }

        omniBarContainer.isPressed = viewState.hasFocus
        if (viewState.hasFocus) {
            animateOmnibarFocusedState(focused = true)
        } else {
            animateOmnibarFocusedState(focused = false)
        }
    }

    /**
     * In focused state the Omnibar will grow 2dp in each direction
     * It will also show an outline to indicate that the Omnibar is focused
     * We need to compensate the size increase so that the icons don't move too
     */
    private fun animateOmnibarFocusedState(focused: Boolean) {
        focusAnimator?.cancel()

        val startCardMarginTop = omnibarCard.marginTop
        val startCardMarginBottom = omnibarCard.marginBottom
        val startCardMarginStart = omnibarCard.marginStart
        val startCardMarginEnd = omnibarCard.marginEnd
        val startContentPaddingTop = omniBarContentContainer.paddingTop
        val startContentPaddingBottom = omniBarContentContainer.paddingBottom
        val startContentPaddingStart = omniBarContentContainer.paddingStart
        val startContentPaddingEnd = omniBarContentContainer.paddingEnd
        val startCardStrokeWidth = omnibarCard.strokeWidth

        val endCardMarginTop: Int
        val endCardMarginBottom: Int
        val endCardMarginStart: Int
        val endCardMarginEnd: Int
        val endContentPaddingTop: Int
        val endContentPaddingBottom: Int
        val endContentPaddingStart: Int
        val endContentPaddingEnd: Int
        val endCardStrokeWidth: Int

        if (focused) {
            endCardMarginTop = omnibarCardFocusedMarginTop
            endCardMarginBottom = omnibarCardFocusedMarginBottom
            endCardMarginStart = omnibarCardFocusedMarginHorizontal
            endCardMarginEnd = omnibarCardFocusedMarginHorizontal
            endContentPaddingTop = omnibarContentFocusedPaddingVertical
            endContentPaddingBottom = omnibarContentFocusedPaddingVertical
            endContentPaddingStart = omnibarContentFocusedPaddingHorizontal
            endContentPaddingEnd = omnibarContentFocusedPaddingHorizontal
            endCardStrokeWidth = omnibarOutlineFocusedWidth
        } else {
            endCardMarginTop = omnibarCardMarginTop
            endCardMarginBottom = omnibarCardMarginBottom
            endCardMarginStart = omnibarCardMarginHorizontal
            endCardMarginEnd = omnibarCardMarginHorizontal
            endContentPaddingTop = omnibarContentPadding
            endContentPaddingBottom = omnibarContentPadding
            endContentPaddingStart = omnibarContentPadding
            endContentPaddingEnd = omnibarContentPadding
            endCardStrokeWidth = omnibarOutlineWidth
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = DEFAULT_ANIMATION_DURATION
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedValue as Float

            val animatedCardMarginTop = (startCardMarginTop + (endCardMarginTop - startCardMarginTop) * fraction).toInt()
            val animatedCardMarginBottom = (startCardMarginBottom + (endCardMarginBottom - startCardMarginBottom) * fraction).toInt()
            val animatedCardMarginStart = (startCardMarginStart + (endCardMarginStart - startCardMarginStart) * fraction).toInt()
            val animatedCardMarginEnd = (startCardMarginEnd + (endCardMarginEnd - startCardMarginEnd) * fraction).toInt()
            val animatedContentPaddingTop = (startContentPaddingTop + (endContentPaddingTop - startContentPaddingTop) * fraction).toInt()
            val animatedContentPaddingBottom = (startContentPaddingBottom + (endContentPaddingBottom - startContentPaddingBottom) * fraction).toInt()
            val animatedContentPaddingStart = (startContentPaddingStart + (endContentPaddingStart - startContentPaddingStart) * fraction).toInt()
            val animatedContentPaddingEnd = (startContentPaddingEnd + (endContentPaddingEnd - startContentPaddingEnd) * fraction).toInt()
            val animatedCardStrokeWidth = (startCardStrokeWidth + (endCardStrokeWidth - startCardStrokeWidth) * fraction).toInt()

            val params = omnibarCard.layoutParams as MarginLayoutParams
            params.leftMargin = animatedCardMarginStart
            params.topMargin = animatedCardMarginTop
            params.rightMargin = animatedCardMarginEnd
            params.bottomMargin = animatedCardMarginBottom
            omnibarCard.setLayoutParams(params)

            omniBarContentContainer.setPadding(
                animatedContentPaddingStart,
                animatedContentPaddingTop,
                animatedContentPaddingEnd,
                animatedContentPaddingBottom,
            )

            omnibarCard.strokeWidth = animatedCardStrokeWidth
        }

        animator.start()
        focusAnimator = animator
    }

    fun setFadeOmnibarItemPressedListener(itemPressedListener: FadeOmnibarItemPressedListener) {
        fadeOmnibarItemPressedListener = itemPressedListener
        aiChat.setOnClickListener {
            fadeOmnibarItemPressedListener?.onDuckChatButtonPressed()
        }
        backIcon.setOnClickListener {
            fadeOmnibarItemPressedListener?.onBackButtonPressed()
        }
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 300L
    }
}

interface FadeOmnibarItemPressedListener {
    fun onDuckChatButtonPressed()
    fun onBackButtonPressed()
}
