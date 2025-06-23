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
import android.view.ViewGroup
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import androidx.core.view.isVisible
import androidx.core.view.marginEnd
import androidx.core.view.updatePadding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeFadeOmnibarFindInPageBinding
import com.duckduckgo.app.browser.omnibar.FindInPage
import com.duckduckgo.app.browser.omnibar.FindInPageImpl
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarItemPressedListener
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toDp
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject
import com.duckduckgo.mobile.android.R as CommonR

@InjectWith(FragmentScope::class)
class SingleOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {
    @Inject
    lateinit var globalActivityStarter: GlobalActivityStarter

    private val aiChatDivider: View by lazy { findViewById(R.id.verticalDivider) }
    private val omnibarCard: MaterialCardView by lazy { findViewById(R.id.omniBarContainer) }
    private val omniBarContentContainer: View by lazy { findViewById(R.id.omniBarContentContainer) }
    private val backIcon: ImageView by lazy { findViewById(R.id.backIcon) }
    private val customTabToolbarContainerWrapper: ViewGroup by lazy { findViewById(R.id.customTabToolbarContainerWrapper) }
    val omniBarClickCatcher: View by lazy { findViewById(R.id.omnibarClickCatcher) }

    override val findInPage: FindInPage by lazy {
        FindInPageImpl(IncludeFadeOmnibarFindInPageBinding.bind(findViewById(R.id.findInPage)))
    }
    private var isFindInPageVisible = false
    private val findInPageLayoutVisibilityChangeListener = OnGlobalLayoutListener {
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

    private val toolbarContainerPaddingTopWhenAtBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.experimentalToolbarContainerPaddingTopWhenAtBottom)
    }
    private val omnibarOutlineWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineWidth) }
    private val omnibarOutlineFocusedWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineFocusedWidth) }

    private var focusAnimator: ValueAnimator? = null

    private var singleOmnibarItemPressedListener: OmnibarItemPressedListener? = null

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.SingleOmnibarLayout, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.SingleOmnibarLayout_omnibarPosition, 0)]

        inflate(context, R.layout.view_single_omnibar, this)

        AndroidSupportInjection.inject(this)

        if (omnibarPosition == OmnibarPosition.TOP) {
            omnibarCard.elevation = 1f.toDp(context)
        } else {
            // When omnibar is at the bottom, we're adding an additional space at the top
            toolbarContainer.updatePadding(
                top = toolbarContainerPaddingTopWhenAtBottom,
            )

            omnibarCard.elevation = 0.5f.toDp(context)
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

        renderShadows(viewState.showShadows)

        if (viewState.hasFocus || isFindInPageVisible) {
            animateOmnibarFocusedState(focused = true)
        } else {
            animateOmnibarFocusedState(focused = false)
        }
    }

    override fun renderButtons(viewState: ViewState) {
        super.renderButtons(viewState)

        clearTextButton.isVisible = viewState.showClearButton
        voiceSearchButton.isVisible = viewState.showVoiceSearch
        spacer.isVisible = false

        aiChatMenu?.isVisible = viewState.showChatMenu
        aiChatDivider.isVisible = (viewState.showVoiceSearch || viewState.showClearButton) && viewState.showChatMenu

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

        omniBarClickCatcher.isVisible = viewState.showClickCatcher
    }

    private fun animateOmnibarFocusedState(focused: Boolean) {
        focusAnimator?.cancel()

        val startCardStrokeWidth = omnibarCard.strokeWidth
        val endCardStrokeWidth: Int = if (focused) {
            omnibarOutlineFocusedWidth
        } else {
            omnibarOutlineWidth
        }

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = DEFAULT_ANIMATION_DURATION
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener { valueAnimator ->
            val fraction = valueAnimator.animatedValue as Float

            val animatedCardStrokeWidth = (startCardStrokeWidth + (endCardStrokeWidth - startCardStrokeWidth) * fraction).toInt()

            val params = omnibarCard.layoutParams as MarginLayoutParams
            omnibarCard.setLayoutParams(params)

            omnibarCard.strokeWidth = animatedCardStrokeWidth
        }

        animator.start()
        focusAnimator = animator
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

    private fun renderShadows(showShadows: Boolean) {
        // outlineProvider = if (showShadows) {
        //     ViewOutlineProvider.BACKGROUND
        // } else {
        //     null
        // }
    }

    companion object {
        private const val DEFAULT_ANIMATION_DURATION = 300L
    }
}
