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
import androidx.constraintlayout.widget.ConstraintLayout
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
import timber.log.Timber

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    private val aiChat: ImageView by lazy { findViewById(R.id.aiChat) }
    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarWrapper: View by lazy { findViewById(R.id.omniBarContainerWrapper) }
    private val omnibarTrailingIconsContainer: View by lazy { findViewById(R.id.endIconsContainer) }
    private val omnibarLeadingIconsContainer: View by lazy { findViewById(R.id.omnibarIconContainer) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }
    private val omnibarIconContainer: View by lazy { findViewById(R.id.endIconsContainer) }

    /**
     * Returns the [BrowserNavigationBarView] reference if it's embedded inside of this omnibar layout, otherwise, returns null.
     */
    var navigationBar: BrowserNavigationBarView? = null
        private set

    private val omnibarDefaultHeight by lazy { resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarCardSize) }
    private val omnibarFocusedHeight by lazy {
        resources.getDimensionPixelSize(
            com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarCardFocusedHeight,
        )
    }
    private val omnibarDefaultMargin by lazy { resources.getDimensionPixelSize(com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarCardMargin) }
    private val omnibarFocusedMargin by lazy {
        resources.getDimensionPixelSize(
            com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarCardFocusedMargin,
        )
    }
    private val omnibarDefaultIconsMargin by lazy {
        resources.getDimensionPixelSize(
            com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarIconsMargin,
        )
    }
    private val omnibarFocusedIconsMargin by lazy {
        resources.getDimensionPixelSize(
            com.duckduckgo.mobile.android.R.dimen.experimentalOmnibarIconsFocusedMargin,
        )
    }
    private val omnibarOutline by lazy { ContextCompat.getDrawable(context, com.duckduckgo.mobile.android.R.drawable.fade_omnibar_outline) }

    private var fadeOmnibarItemPressedListener: FadeOmnibarItemPressedListener? = null

    private var focusAnimated = false

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

        Timber.d("OmnibarFocus: omnibarWidth: ${omnibarWrapper.width}")

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
            if (!focusAnimated) {
                animateOmnibarFocusedState(focused = true)
                focusAnimated = true
            }
        } else {
            if (focusAnimated) {
                animateOmnibarFocusedState(focused = false)
                focusAnimated = false
            }
        }
    }

    /**
     * In focused state the Omnibar will grow 2dp in each direction
     * It will also show an outline to indicate that the Omnibar is focused
     * We need to compensate the size increase so that the icons don't move too
     */
    private fun animateOmnibarFocusedState(focused: Boolean) {
        var startMargin = 0
        var endMargin = 0
        var iconsStartMargin = 0
        var iconsEndMargin = 0
        var startHeight = 0
        var endHeight = 0

        if (focused) {
            startMargin = omnibarDefaultMargin
            endMargin = omnibarFocusedMargin
            iconsStartMargin = omnibarDefaultIconsMargin
            iconsEndMargin = omnibarFocusedIconsMargin
            startHeight = omnibarDefaultHeight
            endHeight = omnibarFocusedHeight
        } else {
            startMargin = omnibarFocusedMargin
            endMargin = omnibarDefaultMargin
            iconsStartMargin = omnibarFocusedIconsMargin
            iconsEndMargin = omnibarDefaultIconsMargin
            startHeight = omnibarFocusedHeight
            endHeight = omnibarDefaultHeight
        }

        val layoutParams = omnibarWrapper.layoutParams as LinearLayout.LayoutParams
        val trailingIconsLayoutParams = omnibarTrailingIconsContainer.layoutParams as ConstraintLayout.LayoutParams
        val leadingIconsLayoutParams = omnibarLeadingIconsContainer.layoutParams as ConstraintLayout.LayoutParams

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = DEFAULT_ANIMATION_DURATION
        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedValue as Float

            val animatedHeight = (startHeight + (endHeight - startHeight) * fraction).toInt()
            val animatedMargin = (startMargin + (endMargin - startMargin) * fraction).toInt()
            val animatedIconsMargin = (iconsStartMargin + (iconsEndMargin - iconsStartMargin) * fraction).toInt()

            layoutParams.height = animatedHeight
            layoutParams.leftMargin = animatedMargin
            layoutParams.rightMargin = animatedMargin
            omnibarWrapper.layoutParams = layoutParams
            omnibarWrapper.requestLayout()

            leadingIconsLayoutParams.leftMargin = animatedIconsMargin
            omnibarLeadingIconsContainer.layoutParams = leadingIconsLayoutParams
            omnibarLeadingIconsContainer.requestLayout()

            trailingIconsLayoutParams.rightMargin = animatedIconsMargin
            omnibarTrailingIconsContainer.layoutParams = trailingIconsLayoutParams
            omnibarTrailingIconsContainer.requestLayout()
        }

        val outlineAnimator = if (focused) {
            ValueAnimator.ofInt(0, 255)
        } else {
            ValueAnimator.ofInt(255, 0)
        }

        outlineAnimator.duration = DEFAULT_ANIMATION_DURATION
        outlineAnimator.addUpdateListener { animation ->
            val alpha = animation.animatedValue as Int
            omnibarOutline?.alpha = alpha
            omnibarWrapper.background = omnibarOutline
        }

        animator.start()
        outlineAnimator.start()
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
