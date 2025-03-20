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
import android.widget.ImageView
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.fade
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import kotlin.math.abs

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val minibar: View by lazy { findViewById(R.id.minibar) }
    private val minibarText: DaxTextView by lazy { findViewById(R.id.minibarText) }
    private val aiChat: ImageView by lazy { findViewById(R.id.aiChat) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }

    private var fadeOmnibarItemPressedListener: FadeOmnibarItemPressedListener? = null

    private var targetHeight: Int = 0
    private var currentHeight: Int = 0
    private var targetToolbarAlpha: Float = 1f
    private var currentToolbarAlpha: Float = 1f
    private var targetMinibarAlpha: Float = 0f
    private var currentMinibarAlpha: Float = 0f

    init {
        val attr =
            context.theme.obtainStyledAttributes(attrs, R.styleable.FadeOmnibarLayout, defStyle, 0)
        omnibarPosition =
            OmnibarPosition.entries[attr.getInt(R.styleable.FadeOmnibarLayout_omnibarPosition, 0)]

        val layout = if (omnibarPosition == OmnibarPosition.BOTTOM) {
            R.layout.view_fade_omnibar_bottom
        } else {
            R.layout.view_fade_omnibar
        }
        inflate(context, layout, this)

        minibar.setOnClickListener {
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

        minibarText.text = viewState.url.extractDomain()
        omniBarContainer.isPressed = viewState.hasFocus
        if (viewState.hasFocus) {
            omnibarCard.strokeColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorAccentBlue)
        } else {
            omnibarCard.strokeColor = context.getColorFromAttr(com.duckduckgo.mobile.android.R.attr.daxColorOmnibarStroke)
        }
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
        if (currentHeight == 0) {
            currentHeight = layoutParams.height
        }

        if (!scrollableView.canScrollVertically(-1)) { // top of the page condition
            revealToolbar()
            return
        } else if (!scrollableView.canScrollVertically(1)) { // bottom of the page condition
            revealMinibar()
            return
        }

        val isScrollingDown = scrollY > oldScrollY
        val scrollDelta = abs(scrollY - oldScrollY)
        val transitionStepRatio = scrollDelta / 76.toPx(context).toFloat()

        // update layout alpha
        if (isScrollingDown) {
            targetToolbarAlpha = 0f
            targetMinibarAlpha = 1f
        } else {
            targetToolbarAlpha = 1f
            targetMinibarAlpha = 0f
        }

        val toolbarAlphaDifference = (targetToolbarAlpha - currentToolbarAlpha)
        val toolbarAlphaChange = (toolbarAlphaDifference * transitionStepRatio)
        currentToolbarAlpha += toolbarAlphaChange
        currentToolbarAlpha = currentToolbarAlpha.coerceIn(0f, 1f)
        fadeToolbar(currentToolbarAlpha)

        val textAlphaDifference = (targetMinibarAlpha - currentMinibarAlpha)
        val textAlphaChange = (textAlphaDifference * transitionStepRatio)
        currentMinibarAlpha += textAlphaChange
        currentMinibarAlpha = currentMinibarAlpha.coerceIn(0f, 1f)
        fadeMinibar(currentMinibarAlpha)

        // update layout height
        targetHeight = if (isScrollingDown) {
            minibar.height
        } else {
            toolbar.height
        }
        if (targetHeight != currentHeight) {
            updateLayoutHeight(transitionStepRatio)
        }
    }

    private fun revealToolbar() {
        // update layout alpha
        targetToolbarAlpha = 1f
        targetMinibarAlpha = 0f
        currentToolbarAlpha = 1f
        currentMinibarAlpha = 0f
        fadeToolbar(targetToolbarAlpha)
        fadeMinibar(targetMinibarAlpha)

        // update layout height
        targetHeight = toolbar.height
        currentHeight = toolbar.height
        layoutParams.height = targetHeight
        this.layoutParams = layoutParams
    }

    private fun revealMinibar() {
        // update layout alpha
        targetToolbarAlpha = 0f
        targetMinibarAlpha = 1f
        currentToolbarAlpha = 0f
        currentMinibarAlpha = 1f
        fadeToolbar(targetToolbarAlpha)
        fadeMinibar(targetMinibarAlpha)

        // update layout height
        targetHeight = minibar.height
        currentHeight = minibar.height
        layoutParams.height = targetHeight
        this.layoutParams = layoutParams
    }

    private fun updateLayoutHeight(transitionStepRatio: Float) {
        val heightDifference = targetHeight - currentHeight
        val heightChange = (heightDifference * transitionStepRatio).toInt()
        currentHeight += heightChange
        currentHeight = currentHeight.coerceIn(minibar.height, toolbar.height)
        layoutParams.height = currentHeight
        this.layoutParams = layoutParams
    }

    /**
     * Returns a percentage (0.0 to 1.0) of how much the omnibar has shifted into the minibar.
     */
    fun getShiftRatio(): Float {
        return currentMinibarAlpha
    }

    private fun fadeToolbar(alpha: Float) {
        toolbar.fade(alpha)
        pageLoadingIndicator.fade(alpha)
        fireIconMenu.fade(alpha)
        tabsMenu.fade(alpha)
        browserMenu.fade(alpha)
        aiChat.fade(alpha)
    }

    private fun fadeMinibar(alpha: Float) {
        minibar.fade(alpha)
        minibar.alpha = alpha
        if (alpha == 0f) {
            minibar.isInvisible = true
        } else if (minibar.isInvisible) {
            minibar.isInvisible = false
        }
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
