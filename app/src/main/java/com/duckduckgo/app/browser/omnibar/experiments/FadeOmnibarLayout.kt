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
import android.widget.LinearLayout
import androidx.core.animation.addListener
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import androidx.core.view.marginBottom
import androidx.core.view.marginEnd
import androidx.core.view.marginStart
import androidx.core.view.marginTop
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.duckduckgo.anvil.annotations.InjectWith
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.IncludeFadeOmnibarFindInPageBinding
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.FindInPage
import com.duckduckgo.app.browser.omnibar.FindInPageImpl
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.OmnibarItemPressedListener
import com.duckduckgo.app.browser.omnibar.OmnibarLayout
import com.duckduckgo.app.browser.omnibar.OmnibarLayoutViewModel.ViewState
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.toDp
import com.duckduckgo.di.scopes.FragmentScope
import com.duckduckgo.mobile.android.R as CommonR
import com.duckduckgo.navigation.api.GlobalActivityStarter
import com.google.android.material.card.MaterialCardView
import dagger.android.support.AndroidSupportInjection
import javax.inject.Inject

@InjectWith(FragmentScope::class)
class FadeOmnibarLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : OmnibarLayout(context, attrs, defStyle) {

    @Inject
    lateinit var experimentDataStore: VisualDesignExperimentDataStore

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

    /**
     * Returns the [BrowserNavigationBarView] reference if it's embedded inside of this omnibar layout, otherwise, returns null.
     */
    var navigationBar: BrowserNavigationBarView? = null
        private set

    private val toolbarContainerPaddingTopWhenAtBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.experimentalToolbarContainerPaddingTopWhenAtBottom)
    }
    private val toolbarContainerPaddingHorizontalWhenAtBottom by lazy {
        resources.getDimensionPixelSize(CommonR.dimen.experimentalToolbarContainerPaddingHorizontalWhenAtBottom)
    }
    private val omnibarCardMarginHorizontal by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginHorizontal) }
    private val omnibarCardMarginTop by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginTop) }
    private val omnibarCardMarginBottom by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardMarginBottom) }
    private val omnibarCardFocusedMarginHorizontal by lazy {
        resources.getDimensionPixelSize(
            CommonR.dimen.experimentalOmnibarCardFocusedMarginHorizontal,
        )
    }
    private val omnibarCardFocusedMarginTop by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginTop) }
    private val omnibarCardFocusedMarginBottom by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarCardFocusedMarginBottom) }
    private val omnibarOutlineWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineWidth) }
    private val omnibarOutlineFocusedWidth by lazy { resources.getDimensionPixelSize(CommonR.dimen.experimentalOmnibarOutlineFocusedWidth) }

    private var focusAnimator: ValueAnimator? = null

    private var fadeOmnibarItemPressedListener: OmnibarItemPressedListener? = null

    init {
        val attr = context.theme.obtainStyledAttributes(attrs, R.styleable.FadeOmnibarLayout, defStyle, 0)
        omnibarPosition = OmnibarPosition.entries[attr.getInt(R.styleable.FadeOmnibarLayout_omnibarPosition, 0)]
        val root = inflate(context, R.layout.view_fade_omnibar, this)

        AndroidSupportInjection.inject(this)

        val rootContainer = root.findViewById<LinearLayout>(R.id.rootContainer)
        val navBar = rootContainer.findViewById<BrowserNavigationBarView>(R.id.omnibarNavigationBar)
        if (omnibarPosition == OmnibarPosition.TOP) {
            rootContainer.removeView(navBar)

            omnibarCard.elevation = 1f.toDp(context)
        } else {
            navigationBar = navBar

            // When omnibar is at the bottom, we're adding an additional space at the top
            toolbarContainer.updatePadding(
                top = toolbarContainerPaddingTopWhenAtBottom,
                right = toolbarContainerPaddingHorizontalWhenAtBottom,
                left = toolbarContainerPaddingHorizontalWhenAtBottom,
            )
            // at the same time, we remove that space from the navigation bar which now sits below the omnibar
            navBar.findViewById<LinearLayout>(R.id.barView).updatePadding(
                top = 0,
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

    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            // This allows the view to adjust to configuration changes, even if it's currently in the focused state.
            // We need to do this after the layout pass that triggered onSizeChanged because there appears to be a race condition
            // where layout param changes done directly in the onSizeChanged loop are not applied correctly (only applies to TOP omnibar position).
            if (omnibarPosition == OmnibarPosition.TOP) {
                doOnLayout {
                    unlockContentDimensions()
                }
            } else {
                // For BOTTOM omnibar position, we don't wait to doOnLayout because it breaks the omnibar layout with tab swiping
                unlockContentDimensions()
            }
        }
    }

    override fun isBottomNavEnabled(): Boolean {
        return true
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
        tabsMenu.isVisible = false
        fireIconMenu.isVisible = false
        browserMenu.isVisible = viewState.viewMode is ViewMode.CustomTab && viewState.showBrowserMenu && !isFindInPageVisible
        browserMenuHighlight.isVisible = false
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

        enableClickCatcher(viewState.showClickCatcher)
    }

    private fun enableClickCatcher(enabled: Boolean) {
        omniBarClickCatcher.isVisible = enabled

        omnibarTextInput.apply {
            isEnabled = !enabled
            isFocusable = !enabled
            isFocusableInTouchMode = !enabled
        }
    }

    /**
     * In focused state the Omnibar card will grow 4dp in each direction, where 2dp of that  will be taken by the card's outline.
     * The growth is achieved by decreasing the card's margins.
     *
     * At the same time, we need to compensate the size increase so that the icons don't move too,
     * so we add horizontal padding to the card's container equal to the 4dp of growth.
     *
     * We also add additional 2dp of vertical padding so that content (like progress bar) doesn't overlap with the outline.
     */
    private fun animateOmnibarFocusedState(focused: Boolean) {
        focusAnimator?.cancel()

        val startCardMarginTop = omnibarCard.marginTop
        val startCardMarginBottom = omnibarCard.marginBottom
        val startCardMarginStart = omnibarCard.marginStart
        val startCardMarginEnd = omnibarCard.marginEnd
        val startCardStrokeWidth = omnibarCard.strokeWidth

        val endCardMarginTop: Int
        val endCardMarginBottom: Int
        val endCardMarginStart: Int
        val endCardMarginEnd: Int
        val endCardStrokeWidth: Int

        if (focused) {
            endCardMarginTop = omnibarCardFocusedMarginTop
            endCardMarginBottom = omnibarCardFocusedMarginBottom
            endCardMarginStart = omnibarCardFocusedMarginHorizontal
            endCardMarginEnd = omnibarCardFocusedMarginHorizontal
            endCardStrokeWidth = omnibarOutlineFocusedWidth
        } else {
            endCardMarginTop = omnibarCardMarginTop
            endCardMarginBottom = omnibarCardMarginBottom
            endCardMarginStart = omnibarCardMarginHorizontal
            endCardMarginEnd = omnibarCardMarginHorizontal
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
            val animatedCardStrokeWidth = (startCardStrokeWidth + (endCardStrokeWidth - startCardStrokeWidth) * fraction).toInt()

            val params = omnibarCard.layoutParams as MarginLayoutParams
            params.leftMargin = animatedCardMarginStart
            params.topMargin = animatedCardMarginTop
            params.rightMargin = animatedCardMarginEnd
            params.bottomMargin = animatedCardMarginBottom
            omnibarCard.setLayoutParams(params)

            omnibarCard.strokeWidth = animatedCardStrokeWidth
        }

        animator.addListener(
            onStart = {
                lockContentDimensions()
            },
            onEnd = {
                // Only unlock content size when we're back to unfocused state.
                if (!focused) {
                    unlockContentDimensions()
                }
            },
            onCancel = {
                // Ensuring that onEnd, and consequently #unlockContentDimensions(), is not called when transition is canceled,
                // which can result in content jumping to match_parent while the transition is not finished yet.
                // We only want to unlock when we're fully transitioned back to unfocused state.
                animator.removeAllListeners()
            },
        )

        animator.start()
        focusAnimator = animator
    }

    /**
     * Lock content dimensions so that the view doesn't respond to the wrapping card's size changes.
     *
     * When focused, we resize the wrapping card to make the stroke appear "outside" but we don't want the content to expand with it.
     */
    private fun lockContentDimensions() {
        omniBarContentContainer.updateLayoutParams {
            width = omniBarContentContainer.measuredWidth
            height = omniBarContentContainer.measuredHeight
        }
    }

    /**
     * Unlock content dimensions so that the view can correctly respond to changes in the viewport size,
     * like resizing the app window or changing device orientation.
     */
    private fun unlockContentDimensions() {
        omniBarContentContainer.updateLayoutParams {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    private fun onFindInPageShown() {
        omniBarContentContainer.hide()
        customTabToolbarContainerWrapper.hide()
        if (viewModel.viewState.value.viewMode is ViewMode.CustomTab) {
            omniBarContainer.show()
            browserMenu.gone()
        }
        animateOmnibarFocusedState(focused = true)
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
    }

    fun setFadeOmnibarItemPressedListener(itemPressedListener: OmnibarItemPressedListener) {
        fadeOmnibarItemPressedListener = itemPressedListener
        backIcon.setOnClickListener {
            viewModel.onBackButtonPressed()
            fadeOmnibarItemPressedListener?.onBackButtonPressed()
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
