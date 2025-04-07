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
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.di.scopes.FragmentScope
import dagger.android.support.AndroidSupportInjection

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val aiChat: ImageView by lazy { findViewById(R.id.aiChat) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarWrapper: View by lazy { findViewById(R.id.omniBarContainerWrapper) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }

    /**
     * Returns the [BrowserNavigationBarView] reference if it's embedded inside of this omnibar layout, otherwise, returns null.
     */
    var navigationBar: BrowserNavigationBarView? = null
        private set

    private val omnibarPressedHeight by lazy {
        resources.getDimensionPixelSize(
            com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarCardPressedSize,
        )
    }
    private val omnibarDefaultHeight by lazy { resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarCardSize) }
    private val omnibarFocusedExtraWidth by lazy {
        resources.getDimensionPixelSize(
            com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarWithIncrease,
        )
    }
    private val omnibarOutline by lazy { ContextCompat.getDrawable(context, com.duckduckgo.mobile.android.R.drawable.fade_omnibar_outline) }
    private var omnibarWidth: Int = 0

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
            val layoutParams = omnibarWrapper.layoutParams as LinearLayout.LayoutParams
            layoutParams.setMargins(layoutParams.leftMargin, 4.toPx(), layoutParams.rightMargin, 0.toPx())
            omnibarWrapper.layoutParams = layoutParams
        } else {
            navigationBar = navBar
        }

        outlineProvider = null
        omnibarWidth = omnibarWrapper.width
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
            amimateToFocusedState()
        } else {
            animateToDefaultState()
        }
    }

    private fun amimateToFocusedState() {
        val heightAnimator = ValueAnimator.ofInt(omnibarDefaultHeight, omnibarPressedHeight)
        heightAnimator.duration = DEFAULT_ANIMATION_DURATION
        heightAnimator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Int
            omnibarWrapper.layoutParams.height = animatedValue
            omnibarWrapper.requestLayout()
        }

        val widthAnimator = ValueAnimator.ofInt(omnibarWrapper.width, omnibarWrapper.width + omnibarFocusedExtraWidth)
        widthAnimator.duration = DEFAULT_ANIMATION_DURATION
        widthAnimator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Int
            omnibarWrapper.layoutParams.width = animatedValue
            omnibarWrapper.requestLayout()
        }

        val outlineAnimator = ValueAnimator.ofInt(0, 255)
        outlineAnimator.duration = DEFAULT_ANIMATION_DURATION
        outlineAnimator.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Int
            omnibarOutline?.alpha = alpha
            omnibarWrapper.background = omnibarOutline
        }
        outlineAnimator.start()
        heightAnimator.start()
        // widthAnimator.start()
    }

    private fun animateToDefaultState() {
        val heightAnimator = ValueAnimator.ofInt(omnibarPressedHeight, omnibarDefaultHeight)
        heightAnimator.duration = DEFAULT_ANIMATION_DURATION

        heightAnimator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Int
            omnibarWrapper.layoutParams.height = animatedValue
            omnibarWrapper.requestLayout()
        }

        val widthAnimator = ValueAnimator.ofInt(omnibarWrapper.width, omnibarWidth)
        widthAnimator.duration = DEFAULT_ANIMATION_DURATION
        widthAnimator.addUpdateListener { valueAnimator ->
            val animatedValue = valueAnimator.animatedValue as Int
            omnibarWrapper.layoutParams.width = animatedValue
            omnibarWrapper.requestLayout()
        }

        val outlineAnimator = ValueAnimator.ofInt(255, 0)
        outlineAnimator.duration = DEFAULT_ANIMATION_DURATION
        outlineAnimator.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Int
            omnibarOutline?.alpha = alpha
            omnibarWrapper.background = omnibarOutline
        }
        outlineAnimator.start()

        heightAnimator.start()
        // widthAnimator.start()
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
