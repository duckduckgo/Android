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
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.navigation.bar.view.BrowserNavigationBarView
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.MaliciousSiteWarning
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.DisableVoiceSearch
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange
import com.duckduckgo.app.browser.omnibar.experiments.FadeOmnibarItemPressedListener
import com.duckduckgo.app.browser.omnibar.experiments.FadeOmnibarLayout
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.omnibar.model.OmnibarType
import com.duckduckgo.app.browser.omnibar.model.OmnibarType.FADE
import com.duckduckgo.app.browser.omnibar.model.OmnibarType.SCROLLING
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.browser.webview.BottomOmnibarBrowserContainerLayoutBehavior
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.experiments.visual.store.VisualDesignExperimentDataStore
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
import timber.log.Timber

@SuppressLint("ClickableViewAccessibility")
class Omnibar(
    val omnibarPosition: OmnibarPosition,
    val omnibarType: OmnibarType,
    private val binding: FragmentBrowserTabBinding,
) {

    init {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                when (omnibarType) {
                    SCROLLING -> {
                        // remove bottom variant
                        binding.rootView.removeView(binding.newOmnibarBottom)

                        // remove all fade omnibars
                        binding.rootView.removeView(binding.fadeOmnibar)
                        binding.rootView.removeView(binding.fadeOmnibarBottom)
                    }

                    FADE -> {
                        // remove bottom variant
                        binding.rootView.removeView(binding.fadeOmnibarBottom)

                        // remove all scrolling omnibars
                        binding.rootView.removeView(binding.newOmnibar)
                        binding.rootView.removeView(binding.newOmnibarBottom)
                    }
                }
            }

            OmnibarPosition.BOTTOM -> {
                when (omnibarType) {
                    SCROLLING -> {
                        // remove top variant
                        binding.rootView.removeView(binding.newOmnibar)

                        // remove all fade omnibars
                        binding.rootView.removeView(binding.fadeOmnibar)
                        binding.rootView.removeView(binding.fadeOmnibarBottom)
                    }

                    FADE -> {
                        // remove top variant
                        binding.rootView.removeView(binding.fadeOmnibar)

                        // remove all scrolling omnibars
                        binding.rootView.removeView(binding.newOmnibar)
                        binding.rootView.removeView(binding.newOmnibarBottom)
                    }
                }

                adjustCoordinatorLayoutBehaviorForBottomOmnibar()
            }
        }
    }

    interface ItemPressedListener {
        fun onTabsButtonPressed()
        fun onTabsButtonLongPressed()
        fun onFireButtonPressed()
        fun onBrowserMenuPressed()
        fun onPrivacyShieldPressed()
        fun onCustomTabClosePressed()
        fun onCustomTabPrivacyDashboardPressed()
        fun onVoiceSearchPressed()
        fun onDuckChatButtonPressed()
    }

    interface FindInPageListener {
        fun onFocusChanged(
            hasFocus: Boolean,
            query: String,
        )

        fun onPreviousSearchItemPressed()
        fun onNextSearchItemPressed()
        fun onClosePressed()
        fun onFindInPageTextChanged(query: String)
    }

    interface TextListener {
        fun onFocusChanged(
            hasFocus: Boolean,
            query: String,
        )

        fun onBackKeyPressed()
        fun onEnterPressed()
        fun onTouchEvent(event: MotionEvent)
        fun onOmnibarTextChanged(state: OmnibarTextState)
        fun onShowSuggestions(state: OmnibarTextState)
        fun onTrackersCountFinished()
    }

    data class OmnibarTextState(
        val text: String,
        val hasFocus: Boolean,
    )

    sealed class ViewMode {
        data object Error : ViewMode()
        data object SSLWarning : ViewMode()
        data object MaliciousSiteWarning : ViewMode()
        data object NewTab : ViewMode()
        data class Browser(val url: String?) : ViewMode()
        data class CustomTab(
            val toolbarColor: Int,
            val title: String?,
            val domain: String?,
            val showDuckPlayerIcon: Boolean = false,
        ) : ViewMode()
    }

    val newOmnibar: OmnibarLayout by lazy {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                when (omnibarType) {
                    SCROLLING -> binding.newOmnibar
                    FADE -> binding.fadeOmnibar
                }
            }

            OmnibarPosition.BOTTOM -> {
                when (omnibarType) {
                    SCROLLING -> binding.newOmnibarBottom
                    FADE -> binding.fadeOmnibarBottom
                }
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
        removeAppBarBehavior(binding.includeNewBrowserTab.newTabLayout)

        binding.browserLayout.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = BottomOmnibarBrowserContainerLayoutBehavior()
        }
    }

    private fun removeAppBarBehavior(view: View) {
        view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = null
        }
    }

    private val findInPage: FindInPage by lazy {
        newOmnibar.findInPage
    }

    val omnibarTextInput: KeyboardAwareEditText by lazy {
        newOmnibar.omnibarTextInput
    }

    private val omniBarContainer: View by lazy {
        newOmnibar.omniBarContainer
    }

    val toolbar: Toolbar by lazy {
        newOmnibar.toolbar
    }

    val shieldIcon: LottieAnimationView by lazy {
        newOmnibar.shieldIcon
    }

    val shieldIconExperiment: LottieAnimationView by lazy {
        newOmnibar.shieldIconExperiment
    }

    val textInputRootView: View by lazy {
        newOmnibar.omnibarTextInput.rootView
    }

    val isInEditMode = newOmnibar.isEditingFlow.distinctUntilChanged()

    var isScrollingEnabled: Boolean
        get() =
            newOmnibar.isScrollingEnabled
        set(value) {
            newOmnibar.isScrollingEnabled = value
        }

    fun setViewMode(viewMode: ViewMode) {
        Timber.d("Omnibar: setViewMode $viewMode")
        when (viewMode) {
            Error -> {
                newOmnibar.decorate(Mode(viewMode))
            }

            NewTab -> {
                newOmnibar.decorate(Mode(viewMode))
            }

            SSLWarning -> {
                newOmnibar.decorate(Mode(viewMode))
            }

            MaliciousSiteWarning -> {
                newOmnibar.decorate(Mode(viewMode))
            }

            else -> {
                newOmnibar.decorate(Mode(viewMode))
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        newOmnibar.setExpanded(expanded)
    }

    fun configureItemPressedListeners(listener: ItemPressedListener) {
        newOmnibar.setOmnibarItemPressedListener(listener)
    }

    fun configureFadeOmnibarItemPressedListeners(listener: FadeOmnibarItemPressedListener) {
        val omnibar = newOmnibar
        if (omnibar is FadeOmnibarLayout) {
            omnibar.setFadeOmnibarItemPressedListener(listener)
        }
    }

    fun addTextListener(listener: TextListener) {
        newOmnibar.setOmnibarTextListener(listener)
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
        newOmnibar.reduce(StateChange.LoadingStateChange(viewState))
    }

    fun renderOmnibarViewState(viewState: OmnibarViewState) {
        Timber.d("Omnibar: renderOmnibarViewState $viewState")
        newOmnibar.reduce(StateChange.OmnibarStateChange(viewState))
    }

    fun setPrivacyShield(privacyShield: PrivacyShield) {
        newOmnibar.decorate(Decoration.PrivacyShieldChanged(privacyShield))
    }

    fun isPulseAnimationPlaying(): Boolean {
        return newOmnibar.isPulseAnimationPlaying()
    }

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

    fun getText(): String {
        return omnibarTextInput.text.toString()
    }

    fun setTextSelection(index: Int) {
        omnibarTextInput.setSelection(index)
    }

    fun showOutline(pressed: Boolean) {
        omniBarContainer.isPressed = pressed
    }

    fun isOutlineShown(): Boolean {
        return omniBarContainer.isPressed
    }

    fun isEditing(): Boolean {
        return newOmnibar.isEditing
    }

    fun renderBrowserViewState(viewState: BrowserViewState) {
        newOmnibar.decorate(
            HighlightOmnibarItem(
                fireButton = viewState.fireButton.isHighlighted(),
                privacyShield = viewState.showPrivacyShield.isHighlighted(),
            ),
        )
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        newOmnibar.decorate(Decoration.LaunchCookiesAnimation(isCosmetic))
    }

    fun enqueueCookiesAnimation(isCosmetic: Boolean) {
        newOmnibar.decorate(Decoration.QueueCookiesAnimation(isCosmetic))
    }

    fun cancelTrackersAnimation() {
        newOmnibar.decorate(Decoration.CancelAnimations)
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        newOmnibar.decorate(Decoration.LaunchTrackersAnimation(events))
    }

    fun configureCustomTab(
        customTabToolbarColor: Int,
        customTabDomainText: String?,
    ) {
        newOmnibar.decorate(Mode(CustomTab(toolbarColor = customTabToolbarColor, title = null, domain = customTabDomainText)))
    }

    fun showWebPageTitleInCustomTab(
        title: String,
        url: String?,
        showDuckPlayerIcon: Boolean,
    ) {
        val redirectedDomain = url?.extractDomain()

        newOmnibar.decorate(Decoration.ChangeCustomTabTitle(title, redirectedDomain, showDuckPlayerIcon))
    }

    fun show() {
        newOmnibar.show()
    }

    fun hide() {
        newOmnibar.gone()
    }

    fun voiceSearchDisabled(url: String?) {
        newOmnibar.decorate(DisableVoiceSearch(url ?: ""))
    }

    fun getNavigationBar(): BrowserNavigationBarView? {
        val omnibar = newOmnibar
        return if (omnibar is FadeOmnibarLayout) {
            omnibar.navigationBar
        } else {
            null
        }
    }
}

fun VisualDesignExperimentDataStore.getOmnibarType(): OmnibarType {
    return if (experimentState.value.isEnabled) {
        FADE
    } else {
        SCROLLING
    }
}
