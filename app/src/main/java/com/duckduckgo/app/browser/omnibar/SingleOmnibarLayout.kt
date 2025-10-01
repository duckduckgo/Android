/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.app.browser.omnibar

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.ImageView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.common.ui.view.addBottomShadow
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toPx
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class SingleOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {
    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val omnibarCardShadow: MaterialCardView by lazy { findViewById(R.id.omniBarContainerShadow) }
    private val iconsContainer: View by lazy { findViewById(R.id.iconsContainer) }
    private val shieldIconPulseAnimationContainer: View by lazy { findViewById(R.id.shieldIconPulseAnimationContainer) }
    private val omniBarContentContainer: View by lazy { findViewById(R.id.omniBarContentContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }
    private val customTabToolbarContainerWrapper: ViewGroup by lazy { findViewById(R.id.customTabToolbarContainerWrapper) }

    private var isFindInPageVisible = false
    private val findInPageLayoutVisibilityChangeListener =
        OnGlobalLayoutListener {
            val isVisible = findInPage.findInPageContainer.isVisible
            if (isFindInPageVisible != isVisible) {
                isFindInPageVisible = isVisible
                if (isVisible) {
                    onFindInPageShown()
                } else {
                    onFindInPageHidden()
                }
            }
        }

    private val experimentalOmnibarCardMarginTop by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginTop)
    }

    private val experimentalOmnibarCardMarginBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.omnibarCardMarginBottom)
    }

    private val omnibarOutlineWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.omnibarOutlineWidth) }
    private val omnibarOutlineFocusedWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.omnibarOutlineFocusedWidth) }

    private var focusAnimator: ValueAnimator? = null

    private var singleOmnibarItemPressedListener: OmnibarItemPressedListener? = null

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.SingleOmnibarLayout, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.SingleOmnibarLayout_omnibarPosition, 0)]

        inflate(context, R.layout.view_single_omnibar, this)

        AndroidSupportInjection.inject(this)

        if (Build.VERSION.SDK_INT >= 28) {
            omnibarCardShadow.addBottomShadow()
        }

        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                if (Build.VERSION.SDK_INT < 28) {
                    omnibarCardShadow.cardElevation = 2f.toPx(context)
                }
            }
            OmnibarPosition.BOTTOM -> {
                // When omnibar is at the bottom, we're adding an additional space at the top
                omnibarCardShadow.updateLayoutParams {
                    (this as MarginLayoutParams).apply {
                        topMargin = experimentalOmnibarCardMarginBottom
                        bottomMargin = experimentalOmnibarCardMarginTop
                    }
                }

                iconsContainer.updateLayoutParams {
                    (this as MarginLayoutParams).apply {
                        topMargin = experimentalOmnibarCardMarginBottom
                        bottomMargin = experimentalOmnibarCardMarginTop
                    }
                }

                shieldIconPulseAnimationContainer.updateLayoutParams {
                    (this as MarginLayoutParams).apply {
                        topMargin = experimentalOmnibarCardMarginBottom
                        bottomMargin = experimentalOmnibarCardMarginTop
                    }
                }

                // Try to reduce the bottom omnibar material shadow when not using the custom shadow
                if (Build.VERSION.SDK_INT < 28) {
                    omnibarCardShadow.cardElevation = 0.5f.toPx(context)
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        findInPage.findInPageContainer.viewTreeObserver.addOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        focusAnimator?.cancel()
        findInPage.findInPageContainer.viewTreeObserver.removeOnGlobalLayoutListener(findInPageLayoutVisibilityChangeListener)
    }

    override fun render(viewState: ViewState) {
        super.render(viewState)

        if (viewState.hasFocus || isFindInPageVisible) {
            animateOmnibarFocusedState(focused = true)
        } else {
            animateOmnibarFocusedState(focused = false)
        }

        omnibarCardShadow.isGone = viewState.viewMode is ViewMode.CustomTab && !isFindInPageVisible
    }

    override fun renderButtons(viewState: ViewState) {
        super.renderButtons(viewState)

        val showBackArrow = viewState.hasFocus
        if (showBackArrow) {
            backIcon.show()
            searchIcon.gone()
            shieldIcon.gone()
            daxIcon.gone()
            globeIcon.gone()
            duckPlayerIcon.gone()
        } else {
            backIcon.hide()
        }
    }

    private fun animateOmnibarFocusedState(focused: Boolean) {
        // temporarily disable focus animation
    }

    private fun onFindInPageShown() {
        omniBarContentContainer.hide()
        customTabToolbarContainerWrapper.hide()
        if (viewModel.viewState.value.viewMode is ViewMode.CustomTab) {
            omniBarContainer.show()
            browserMenu.gone()
        }
        animateOmnibarFocusedState(focused = true)
        viewModel.onFindInPageRequested()
    }

    private fun onFindInPageHidden() {
        omniBarContentContainer.show()
        customTabToolbarContainerWrapper.show()
        if (viewModel.viewState.value.viewMode is ViewMode.CustomTab) {
            omniBarContainer.hide()
            browserMenu.isVisible = viewModel.viewState.value.showBrowserMenu
        }
        if (!viewModel.viewState.value.hasFocus) {
            animateOmnibarFocusedState(focused = false)
        }
        viewModel.onFindInPageDismissed()
    }

    fun setSingleOmnibarItemPressedListener(itemPressedListener: OmnibarItemPressedListener) {
        singleOmnibarItemPressedListener = itemPressedListener
        backIcon.setOnClickListener {
            viewModel.onBackButtonPressed()
            singleOmnibarItemPressedListener?.onBackButtonPressed()
        }
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 300L
    }
}
