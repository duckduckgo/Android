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

package com.duckduckgo.app.browser.omnibar

import android.annotation.SuppressLint
import android.text.Editable
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.browser.omnibar.model.Decoration.DisableVoiceSearch
import com.duckduckgo.app.browser.omnibar.model.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.model.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.model.FindInPageListener
import com.duckduckgo.app.browser.omnibar.model.InputScreenLaunchListener
import com.duckduckgo.app.browser.omnibar.model.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.model.LogoClickListener
import com.duckduckgo.app.browser.omnibar.model.StateChange
import com.duckduckgo.app.browser.omnibar.model.TextListener
import com.duckduckgo.app.browser.omnibar.model.ViewMode
import com.duckduckgo.app.browser.omnibar.model.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.model.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.model.ViewMode.MaliciousSiteWarning
import com.duckduckgo.app.browser.omnibar.model.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.model.ViewMode.SSLWarning
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.browser.webview.BottomOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.google.android.material.appbar.AppBarLayout.GONE
import com.google.android.material.appbar.AppBarLayout.VISIBLE
import kotlinx.coroutines.flow.distinctUntilChanged
import logcat.logcat

@SuppressLint("ClickableViewAccessibility")
class Omnibar(
    val omnibarPosition: OmnibarPosition,
    private val binding: FragmentBrowserTabBinding,
) {
    init {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                // remove bottom variant
                binding.rootView.removeView(binding.singleOmnibarLayoutBottom)
            }

            OmnibarPosition.BOTTOM -> {
                // remove top variant
                binding.rootView.removeView(binding.singleOmnibarLayoutTop)

                adjustCoordinatorLayoutBehaviorForBottomOmnibar()
            }
        }
        binding.rootView.removeView(binding.unifiedOmnibarLayoutTop)
        binding.rootView.removeView(binding.unifiedOmnibarLayoutBottom)
    }

    val omnibarView: OmnibarView by lazy {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.singleOmnibarLayoutTop
            }

            OmnibarPosition.BOTTOM -> {
                binding.singleOmnibarLayoutBottom
            }
        }
    }

    /**
     * When bottom omnibar is used, this function removes the default top app bar behavior as most of the offsets are handled via [BottomAppBarBehavior].
     *
     * However, the browser (web view) content offset is managed via [BottomOmnibarBrowserContainerLayoutBehavior].
     */
    private fun adjustCoordinatorLayoutBehaviorForBottomOmnibar() {
        removeAppBarBehavior(binding.autoCompleteSuggestionsList)
        removeAppBarBehavior(binding.focusedView)

        binding.browserLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = BottomOmnibarBrowserContainerLayoutBehavior()
        }

        binding.includeNewBrowserTab.newTabLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = BottomOmnibarBrowserContainerLayoutBehavior()
        }
    }

    private fun removeAppBarBehavior(view: View) {
        view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = null
        }
    }

    private val findInPage: IncludeFindInPageBinding by lazy {
        omnibarView.findInPage
    }

    val omnibarTextInput: KeyboardAwareEditText by lazy {
        omnibarView.omnibarTextInput
    }

    val omniBarContainer: View by lazy {
        omnibarView.omniBarContainer
    }

    val toolbar: Toolbar by lazy {
        omnibarView.toolbar
    }

    val shieldIcon: LottieAnimationView by lazy {
        omnibarView.shieldIcon
    }

    val daxIcon: ImageView by lazy {
        omnibarView.daxIcon
    }

    val textInputRootView: View by lazy {
        omnibarView.omnibarTextInput.rootView
    }

    val isInEditMode = omnibarView.isEditingFlow.distinctUntilChanged()

    var isScrollingEnabled: Boolean
        get() =
            omnibarView.isScrollingEnabled
        set(value) {
            omnibarView.isScrollingEnabled = value
        }

    var viewMode: ViewMode = ViewMode.Browser(null)
        private set

    fun setViewMode(newViewMode: ViewMode) {
        logcat { "Omnibar: setViewMode $newViewMode" }
        viewMode = newViewMode
        when (newViewMode) {
            Error -> {
                omnibarView.decorate(Mode(newViewMode))
            }

            NewTab -> {
                omnibarView.decorate(Mode(newViewMode))
            }

            SSLWarning -> {
                omnibarView.decorate(Mode(newViewMode))
            }

            MaliciousSiteWarning -> {
                omnibarView.decorate(Mode(newViewMode))
            }

            else -> {
                omnibarView.decorate(Mode(newViewMode))
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        omnibarView.setExpanded(expanded)
    }

    fun configureItemPressedListeners(listener: ItemPressedListener) {
        omnibarView.setOmnibarItemPressedListener(listener)
    }

    fun configureLogoClickListener(logoClickListener: LogoClickListener) {
        omnibarView.setLogoClickListener(logoClickListener)
    }

    fun configureOmnibarItemPressedListeners(listener: OmnibarItemPressedListener) {
        val omnibar = omnibarView
        if (omnibar is SingleOmnibarLayout) {
            omnibar.setSingleOmnibarItemPressedListener(listener)
        }
    }

    fun configureInputScreenLaunchListener(listener: InputScreenLaunchListener) {
        omnibarView.setInputScreenLaunchListener(listener)
    }

    fun addTextListener(listener: TextListener) {
        omnibarView.setOmnibarTextListener(listener)
    }

    fun configureFindInPage(listener: FindInPageListener) {
        // we could move this to the layout once the refactor is do
        findInPage.findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            listener.onFocusChanged(hasFocus, findInPage.findInPageInput.text.toString())
        }

        findInPage.previousSearchTermButton.setOnClickListener { listener.onPreviousSearchItemPressed() }
        findInPage.nextSearchTermButton.setOnClickListener { listener.onNextSearchItemPressed() }
        findInPage.closeFindInPagePanel.setOnClickListener { listener.onClosePressed() }
        findInPage.findInPageInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    listener.onFindInPageTextChanged(findInPage.findInPageInput.text.toString())
                }
            },
        )
    }

    fun renderLoadingViewState(viewState: LoadingViewState) {
        omnibarView.reduce(StateChange.LoadingStateChange(viewState))
    }

    fun renderOmnibarViewState(
        viewState: OmnibarViewState,
        forceRender: Boolean = false,
    ) {
        logcat { "Omnibar: renderOmnibarViewState $viewState" }
        omnibarView.reduce(StateChange.OmnibarStateChange(viewState, forceRender))
    }

    fun setPrivacyShield(privacyShield: PrivacyShield) {
        omnibarView.decorate(Decoration.PrivacyShieldChanged(privacyShield))
    }

    fun isPulseAnimationPlaying(): Boolean = omnibarView.isPulseAnimationPlaying()

    fun hideFindInPage() {
        if (findInPage.findInPageContainer.visibility != GONE) {
            binding.focusDummy.requestFocus()
            findInPage.findInPageContainer.gone()
            findInPage.findInPageInput.hideKeyboard()
            findInPage.findInPageInput.text.clear()
        }
    }

    fun showFindInPageView(viewState: FindInPageViewState) {
        if (findInPage.findInPageContainer.visibility != VISIBLE) {
            findInPage.findInPageContainer.show()
            findInPage.findInPageInput.postDelayed(KEYBOARD_DELAY) {
                findInPage.findInPageInput.showKeyboard()
            }
        }

        if (viewState.showNumberMatches) {
            findInPage.findInPageMatches.text =
                findInPage.findInPageMatches.context.getString(R.string.findInPageMatches, viewState.activeMatchIndex, viewState.numberMatches)
            findInPage.findInPageMatches.show()
        } else {
            findInPage.findInPageMatches.hide()
        }
    }

    fun setText(text: String) {
        omnibarTextInput.setText(text)
    }

    fun getText(): String = omnibarTextInput.text.toString()

    fun setTextSelection(index: Int) {
        omnibarTextInput.setSelection(index)
    }

    fun showOutline(pressed: Boolean) {
        omniBarContainer.isPressed = pressed
    }

    fun isOutlineShown(): Boolean = omniBarContainer.isPressed

    fun isEditing(): Boolean = omnibarView.isEditing

    fun renderBrowserViewState(viewState: BrowserViewState) {
        omnibarView.decorate(
            HighlightOmnibarItem(
                fireButton = viewState.fireButton.isHighlighted(),
                privacyShield = viewState.showPrivacyShield.isHighlighted(),
            ),
        )
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        omnibarView.decorate(Decoration.LaunchCookiesAnimation(isCosmetic))
    }

    fun enqueueCookiesAnimation(isCosmetic: Boolean) {
        omnibarView.decorate(Decoration.QueueCookiesAnimation(isCosmetic))
    }

    fun cancelTrackersAnimation() {
        omnibarView.decorate(Decoration.CancelAnimations)
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        omnibarView.decorate(Decoration.LaunchTrackersAnimation(events))
    }

    fun configureCustomTab(
        customTabToolbarColor: Int,
        customTabDomainText: String?,
    ) {
        omnibarView.decorate(Mode(CustomTab(toolbarColor = customTabToolbarColor, title = null, domain = customTabDomainText)))
    }

    fun showWebPageTitleInCustomTab(
        title: String,
        url: String?,
        showDuckPlayerIcon: Boolean,
    ) {
        val redirectedDomain = url?.extractDomain()

        omnibarView.decorate(Decoration.ChangeCustomTabTitle(title, redirectedDomain, showDuckPlayerIcon))
    }

    fun show() {
        omnibarView.show()
    }

    fun hide() {
        omnibarView.gone()
    }

    fun voiceSearchDisabled(url: String?) {
        omnibarView.decorate(DisableVoiceSearch(url ?: ""))
    }

    fun setDraftTextIfNtpOrSerp(query: String) {
        omnibarView.setDraftTextIfNtpOrSerp(query)
    }
}
