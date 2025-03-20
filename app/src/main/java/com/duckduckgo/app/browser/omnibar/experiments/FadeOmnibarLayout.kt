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
import androidx.core.view.updateLayoutParams
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.fade
import com.duckduckgo.common.ui.view.getColorFromAttr
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.text.DaxTextView
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.di.scopes.FragmentScope
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import timber.log.Timber
import kotlin.math.abs

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val minibarContainer: View by lazy { findViewById(R.id.minibarContainer) }
    private val minibarText: DaxTextView by lazy { findViewById(R.id.minibarText) }
    private val aiChat: ImageView by lazy { findViewById(R.id.aiChat) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val sharedShieldIcon: View by lazy { findViewById(R.id.minibarShield) }
    private val transitionedOmnibarBackground: View by lazy { findViewById(R.id.transitionedOmnibarBackground) }
    private val omniBarContainerWrapper: View by lazy { findViewById(R.id.omniBarContainerWrapper) }

    private var fadeOmnibarItemPressedListener: FadeOmnibarItemPressedListener? = null

    private val toolbarHeight: Int by lazy { context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.experimentalToolbarSize).toInt() }
    private val minibarHeight: Int by lazy { context.resources.getDimension(com.duckduckgo.mobile.android.R.dimen.experimentalMinibarSize).toInt() }

    private var targetHeight: Int = 0
    private var currentHeight: Int = 0
    private var targetToolbarAlpha: Float = 1f
    private var currentToolbarAlpha: Float = 1f
    private var targetMinibarAlpha: Float = 0f
    private var currentMinibarAlpha: Float = 0f

    private var transitionRatio = 0f
    private var maximumTextInputWidth: Int = 0

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

        minibarText.setOnClickListener {
            //revealToolbar()
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

        // renderLeadingIconState(viewState.leadingIconState)
    }

    fun resetTransitionDelayed() {
        postDelayed(delayInMillis = 100) {
            //revealToolbar()
        }
    }

    fun onScrollChanged(
        scrollableView: View,
        scrollY: Int,
        oldScrollY: Int,
    ) {
        if (maximumTextInputWidth == 0) {
            maximumTextInputWidth = omnibarTextInput.width
        }

        val scrollDelta = scrollY - oldScrollY
        val ratioChange = scrollDelta / 76.toPx(context).toFloat()
        transitionRatio = (transitionRatio + ratioChange).coerceIn(0f, 1f)

        Timber.d("lp_test; transitionRatio: $transitionRatio")
        Timber.d("lp_test; maximumTextInputWidth: ${toolbar.width}")
        Timber.d("lp_test; omnibarTextInput.width: ${omnibarTextInput.width}")
        Timber.d("lp_test; sharedShieldIcon.width: ${sharedShieldIcon.width}")
        Timber.d("lp_test; sharedShieldIcon.height: ${sharedShieldIcon.height}")

        omnibarTextInput.alpha = 1f - transitionRatio
        aiChatDivider.alpha = 1f - transitionRatio
        aiChat.alpha = 1f - transitionRatio
        transitionedOmnibarBackground.alpha = transitionRatio
        minibarText.alpha = transitionRatio

        val newInputTextWidth = toolbar.width - ((toolbar.width - minibarContainer.width /*/ 2*/) * transitionRatio).toInt()
        Timber.d("lp_test; newInputTextWidth: ${newInputTextWidth}")
        omniBarContainerWrapper.updateLayoutParams {
            width = newInputTextWidth
        }
        omnibarCard.strokeWidth = (1.toPx(context) - (1.toPx(context) * transitionRatio)).toInt()
        omnibarCard.elevation = (2.toPx(context) - (2.toPx(context) * transitionRatio))
        val newOmnibarHeight = toolbarHeight - ((toolbarHeight - minibarHeight) * transitionRatio).toInt()
        toolbarContainer.updateLayoutParams {
            height = newOmnibarHeight
        }

        // val newShieldSize = 20.toPx(context) - ((4.toPx(context) * transitionRatio)).toInt()
        // Timber.d("lp_test; newShieldSize: ${newShieldSize}")
        // shieldIcon.updateLayoutParams {
        //     height = newOmnibarHeight
        // }

        /*
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
        }*/
    }
/*

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
*/

    /**
     * Returns a percentage (0.0 to 1.0) of how much the omnibar has shifted into the minibar.
     */
    fun getShiftRatio(): Float {
        return transitionRatio
    }
/*
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
    }*/

    private fun renderLeadingIconState(iconState: OmnibarLayoutViewModel.LeadingIconState) {
        when (iconState) {
            OmnibarLayoutViewModel.LeadingIconState.SEARCH -> {
                sharedShieldIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.PRIVACY_SHIELD -> {
                sharedShieldIcon.show()
                shieldIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DAX -> {
                sharedShieldIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.GLOBE -> {
                sharedShieldIcon.gone()
            }

            OmnibarLayoutViewModel.LeadingIconState.DUCK_PLAYER -> {
                sharedShieldIcon.gone()
            }
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
