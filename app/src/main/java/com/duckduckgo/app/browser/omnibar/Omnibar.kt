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

import android.animation.Animator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import com.airbnb.lottie.LottieAnimationView
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.TabSwitcherButton
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.databinding.IncludeCustomTabToolbarBinding
import com.duckduckgo.app.browser.databinding.IncludeFindInPageBinding
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.CustomTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.HighlightOmnibarItem
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.Decoration.Mode
import com.duckduckgo.app.browser.omnibar.OmnibarLayout.StateChange
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.LoadingViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.tabs.model.TabEntity
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.common.ui.DuckDuckGoActivity
import com.duckduckgo.common.ui.view.KeyboardAwareEditText
import com.duckduckgo.common.ui.view.KeyboardAwareEditText.ShowSuggestionsListener
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.common.ui.view.hide
import com.duckduckgo.common.ui.view.hideKeyboard
import com.duckduckgo.common.ui.view.show
import com.duckduckgo.common.ui.view.showKeyboard
import com.duckduckgo.common.utils.extensions.isDifferent
import com.duckduckgo.common.utils.extensions.replaceTextChangedListener
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.common.utils.text.TextChangedWatcher
import com.google.android.material.appbar.AppBarLayout.GONE
import com.google.android.material.appbar.AppBarLayout.VISIBLE
import timber.log.Timber

@SuppressLint("ClickableViewAccessibility")
class Omnibar(
    val omnibarPosition: OmnibarPosition,
    private val refactorFlagEnabled: Boolean,
    private val binding: FragmentBrowserTabBinding,
) {

    init {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                if (refactorFlagEnabled) {
                    Timber.d("Omnibar: using NewOmnibar anchored TOP")
                    binding.rootView.removeView(binding.legacyOmnibarBottom)
                    binding.rootView.removeView(binding.legacyOmnibar)
                    binding.rootView.removeView(binding.newOmnibarBottom)
                } else {
                    Timber.d("Omnibar: using LegacyOmnibar anchored TOP")
                    binding.rootView.removeView(binding.newOmnibarBottom)
                    binding.rootView.removeView(binding.newOmnibar)
                    binding.rootView.removeView(binding.legacyOmnibarBottom)
                }
            }

            OmnibarPosition.BOTTOM -> {
                if (refactorFlagEnabled) {
                    Timber.d("Omnibar: using NewOmnibar anchored BOTTOM")
                    binding.rootView.removeView(binding.legacyOmnibarBottom)
                    binding.rootView.removeView(binding.legacyOmnibar)
                    binding.rootView.removeView(binding.newOmnibar)

                    // prevent the touch event leaking to the webView below
                    binding.newOmnibarBottom.setOnTouchListener { _, _ -> true }
                } else {
                    Timber.d("Omnibar: using LegacyOmnibar anchored BOTTOM")
                    binding.rootView.removeView(binding.newOmnibarBottom)
                    binding.rootView.removeView(binding.newOmnibar)
                    binding.rootView.removeView(binding.legacyOmnibar)

                    // prevent the touch event leaking to the webView below
                    binding.legacyOmnibarBottom.setOnTouchListener { _, _ -> true }
                }

                // remove the default top abb bar behavior
                removeAppBarBehavior(binding.autoCompleteSuggestionsList)
                removeAppBarBehavior(binding.browserLayout)
                removeAppBarBehavior(binding.focusedView)
                removeAppBarBehavior(binding.includeNewBrowserTab.newTabLayout)
            }
        }
    }

    interface ItemPressedListener {
        fun onTabsButtonPressed()
        fun onTabsButtonLongPressed()
        fun onFireButtonPressed(isPulseAnimationPlaying: Boolean)
        fun onBrowserMenuPressed()
        fun onPrivacyShieldPressed()
        fun onClearTextPressed()
        fun onCustomTabClosePressed()
        fun onCustomTabPrivacyDashboardPressed()
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
    }

    data class OmnibarTextState(
        val text: String,
        val hasFocus: Boolean,
    )

    sealed class ViewMode {
        data object Error : ViewMode()
        data object SSLWarning : ViewMode()
        data object NewTab : ViewMode()
        data class Browser(val url: String?) : ViewMode()
        data class CustomTab(
            val toolbarColor: Int,
            val domain: String?,
            val showDuckPlayerIcon: Boolean = false,
        ) : ViewMode()
    }

    private val newOmnibar: OmnibarLayout by lazy {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.newOmnibar
            }

            OmnibarPosition.BOTTOM -> {
                binding.newOmnibarBottom
            }
        }
    }

    private val legacyOmnibar: LegacyOmnibarView by lazy {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.legacyOmnibar
            }

            OmnibarPosition.BOTTOM -> {
                binding.legacyOmnibarBottom
            }
        }
    }

    private fun removeAppBarBehavior(view: View) {
        view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = null
        }
    }

    val findInPage: IncludeFindInPageBinding by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.findInPage
        } else {
            legacyOmnibar.findInPage
        }
    }

    val omnibarTextInput: KeyboardAwareEditText by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.omnibarTextInput
        } else {
            legacyOmnibar.omnibarTextInput
        }
    }

    val tabsMenu: TabSwitcherButton by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.tabsMenu
        } else {
            legacyOmnibar.tabsMenu
        }
    }

    val fireIconMenu: FrameLayout by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.fireIconMenu
        } else {
            legacyOmnibar.fireIconMenu
        }
    }

    val browserMenu: FrameLayout by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.browserMenu
        } else {
            legacyOmnibar.browserMenu
        }
    }

    val omniBarContainer: View by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.omniBarContainer
        } else {
            legacyOmnibar.omniBarContainer
        }
    }

    val toolbar: Toolbar by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.toolbar
        } else {
            legacyOmnibar.toolbar
        }
    }

    val toolbarContainer: View by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.toolbarContainer
        } else {
            legacyOmnibar.toolbarContainer
        }
    }

    val customTabToolbarContainer: IncludeCustomTabToolbarBinding by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.customTabToolbarContainer
        } else {
            legacyOmnibar.customTabToolbarContainer
        }
    }

    val browserMenuImageView: ImageView by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.browserMenuImageView
        } else {
            legacyOmnibar.browserMenuImageView
        }
    }

    val shieldIcon: LottieAnimationView by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.shieldIcon
        } else {
            legacyOmnibar.shieldIcon
        }
    }

    val pageLoadingIndicator: ProgressBar by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.pageLoadingIndicator
        } else {
            legacyOmnibar.pageLoadingIndicator
        }
    }

    val searchIcon: ImageView by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.searchIcon
        } else {
            legacyOmnibar.searchIcon
        }
    }

    val daxIcon: ImageView by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.daxIcon
        } else {
            legacyOmnibar.daxIcon
        }
    }

    val clearTextButton: ImageView by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.clearTextButton
        } else {
            legacyOmnibar.clearTextButton
        }
    }

    val placeholder: View by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.placeholder
        } else {
            legacyOmnibar.placeholder
        }
    }

    val voiceSearchButton: ImageView by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.voiceSearchButton
        } else {
            legacyOmnibar.voiceSearchButton
        }
    }

    val spacer: View by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.spacer
        } else {
            legacyOmnibar.spacer
        }
    }

    val textInputRootView: View by lazy {
        if (refactorFlagEnabled) {
            newOmnibar.omnibarTextInput.rootView
        } else {
            legacyOmnibar.omnibarTextInput.rootView
        }
    }

    var isScrollingEnabled: Boolean
        get() =
            if (refactorFlagEnabled) {
                newOmnibar.isScrollingEnabled
            } else {
                legacyOmnibar.isScrollingEnabled
            }
        set(value) {
            if (refactorFlagEnabled) {
                newOmnibar.isScrollingEnabled = value
            } else {
                legacyOmnibar.isScrollingEnabled = value
            }
        }

    fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            Error -> {
                if (refactorFlagEnabled) {
                    newOmnibar.decorate(Mode(viewMode))
                } else {
                    setExpanded(true)
                    shieldIcon.isInvisible = true
                }
            }

            NewTab -> {
                if (refactorFlagEnabled) {
                    newOmnibar.decorate(Mode(viewMode))
                } else {
                    isScrollingEnabled = false
                }
            }

            SSLWarning -> {
                if (refactorFlagEnabled) {
                    newOmnibar.decorate(Mode(viewMode))
                } else {
                    setExpanded(true)
                    shieldIcon.isInvisible = true
                    searchIcon.isInvisible = true
                    daxIcon.isInvisible = true
                }
            }

            else -> {
                if (refactorFlagEnabled) {
                    newOmnibar.decorate(Mode(viewMode))
                }
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        if (refactorFlagEnabled) {
            newOmnibar.setExpanded(expanded)
        } else {
            legacyOmnibar.setExpanded(expanded)
        }
    }

    fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        if (refactorFlagEnabled) {
            newOmnibar.setExpanded(expanded, animate)
        } else {
            legacyOmnibar.setExpanded(expanded, animate)
        }
    }

    fun configureItemPressedListeners(listener: ItemPressedListener) {
        if (refactorFlagEnabled) {
            newOmnibar.setOmnibarItemPressedListener(listener)
        } else {
            tabsMenu.setOnClickListener {
                listener.onTabsButtonPressed()
            }
            tabsMenu.setOnLongClickListener {
                listener.onTabsButtonLongPressed()
                return@setOnLongClickListener true
            }
            fireIconMenu.setOnClickListener {
                listener.onFireButtonPressed(legacyOmnibar.isPulseAnimationPlaying())
            }
            browserMenu.setOnClickListener {
                listener.onBrowserMenuPressed()
            }
            shieldIcon.setOnClickListener {
                listener.onPrivacyShieldPressed()
            }
            clearTextButton.setOnClickListener {
                listener.onClearTextPressed()
            }
        }
    }

    fun addTextListener(listener: TextListener) {
        if (refactorFlagEnabled) {
            newOmnibar.setOmnibarTextListener(listener)
        } else {
            omnibarTextInput.onFocusChangeListener =
                View.OnFocusChangeListener { _, hasFocus: Boolean ->
                    listener.onFocusChanged(hasFocus, omnibarTextInput.text.toString())
                    if (hasFocus) {
                        showOutline(true)
                    } else {
                        showOutline(false)
                    }
                }

            omnibarTextInput.onBackKeyListener = object : KeyboardAwareEditText.OnBackKeyListener {
                override fun onBackKey(): Boolean {
                    listener.onBackKeyPressed()
                    return false
                }
            }

            omnibarTextInput.setOnEditorActionListener(
                TextView.OnEditorActionListener { _, actionId, keyEvent ->
                    if (actionId == EditorInfo.IME_ACTION_GO || keyEvent?.keyCode == KeyEvent.KEYCODE_ENTER) {
                        listener.onEnterPressed()
                        return@OnEditorActionListener true
                    }
                    false
                },
            )

            omnibarTextInput.setOnTouchListener { _, event ->
                listener.onTouchEvent(event)
                false
            }

            omnibarTextInput.replaceTextChangedListener(
                object : TextChangedWatcher() {
                    override fun afterTextChanged(editable: Editable) {
                        listener.onOmnibarTextChanged(
                            OmnibarTextState(
                                omnibarTextInput.text.toString(),
                                omnibarTextInput.hasFocus(),
                            ),
                        )
                    }
                },
            )

            omnibarTextInput.showSuggestionsListener = object : ShowSuggestionsListener {
                override fun showSuggestions() {
                    listener.onShowSuggestions(
                        OmnibarTextState(
                            omnibarTextInput.text.toString(),
                            omnibarTextInput.hasFocus(),
                        ),
                    )
                }
            }
        }
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

    fun renderLoadingViewState(
        viewState: LoadingViewState,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        if (refactorFlagEnabled) {
            newOmnibar.reduce(StateChange.LoadingStateChange(viewState, onAnimationEnd))
        } else {
            legacyOmnibar.onNewProgress(viewState.progress, onAnimationEnd)
        }
    }

    fun renderOmnibarViewState(viewState: OmnibarViewState) {
        Timber.d("Omnibar: renderOmnibarViewState $viewState")
        if (refactorFlagEnabled) {
            newOmnibar.reduce(StateChange.OmnibarStateChange(viewState))
        } else {
            if (viewState.navigationChange) {
                setExpanded(true, true)
            } else if (shouldUpdateOmnibarTextInput(viewState, viewState.omnibarText)) {
                setText(viewState.omnibarText)
                if (viewState.forceExpand) {
                    setExpanded(true, true)
                }
                if (viewState.shouldMoveCaretToEnd) {
                    setTextSelection(viewState.omnibarText.length)
                }
            }
            if (viewState.shouldMoveCaretToStart) {
                setTextSelection(0)
            }
        }
    }

    private fun shouldUpdateOmnibarTextInput(
        viewState: OmnibarViewState,
        omnibarInput: String?,
    ) =
        (!viewState.isEditing || omnibarInput.isNullOrEmpty()) && omnibarTextInput.isDifferent(
            omnibarInput,
        )

    fun setPrivacyShield(
        isCustomTab: Boolean,
        privacyShield: PrivacyShield,
    ) {
        if (refactorFlagEnabled) {
            newOmnibar.decorate(Decoration.PrivacyShieldChanged(privacyShield))
        } else {
            legacyOmnibar.setPrivacyShield(isCustomTab, privacyShield)
        }
    }

    fun renderVoiceSearch(
        viewState: BrowserViewState,
        voiceSearchPressed: () -> Unit,
    ) {
        if (viewState.showVoiceSearch) {
            voiceSearchButton.visibility = VISIBLE
            voiceSearchButton.setOnClickListener {
                voiceSearchPressed()
            }
        } else {
            voiceSearchButton.visibility = GONE
        }
    }

    fun isPulseAnimationPlaying(): Boolean {
        return if (refactorFlagEnabled) {
            newOmnibar.isPulseAnimationPlaying()
        } else {
            legacyOmnibar.isPulseAnimationPlaying()
        }
    }

    fun hideFindInPage() {
        if (findInPage.findInPageContainer.visibility != GONE) {
            binding.focusDummy.requestFocus()
            findInPage.findInPageContainer.gone()
            findInPage.findInPageInput.hideKeyboard()
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

    fun renderBrowserViewState(
        viewState: BrowserViewState,
        tabDisplayedInCustomTabScreen: Boolean,
    ) {
        if (refactorFlagEnabled) {
            newOmnibar.decorate(
                HighlightOmnibarItem(
                    fireButton = viewState.fireButton.isHighlighted(),
                    privacyShield = viewState.showPrivacyShield.isHighlighted(),
                ),
            )
        } else {
            legacyOmnibar.renderBrowserViewState(viewState, tabDisplayedInCustomTabScreen)
        }

        if (viewState.showDuckPlayerIcon) {
            isScrollingEnabled = false
        }
    }

    fun animateTabsCount() {
        if (!refactorFlagEnabled) {
            tabsMenu.animateCount()
        }
    }

    fun renderTabIcon(tabs: List<TabEntity>) {
        if (!refactorFlagEnabled) {
            tabsMenu.count = tabs.count()
            tabsMenu.hasUnread = tabs.firstOrNull { !it.viewed } != null
        }
    }

    fun incrementTabs(onTabsIncremented: () -> Unit) {
        if (!refactorFlagEnabled) {
            setExpanded(true, true)
            tabsMenu.increment {
                onTabsIncremented()
            }
        }
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        if (refactorFlagEnabled) {
            newOmnibar.decorate(Decoration.LaunchCookiesAnimation(isCosmetic))
        } else {
            legacyOmnibar.createCookiesAnimation(isCosmetic)
        }
    }

    fun cancelTrackersAnimation() {
        if (refactorFlagEnabled) {
            newOmnibar.decorate(Decoration.CancelAnimations)
        } else {
            legacyOmnibar.cancelTrackersAnimation()
        }
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        if (refactorFlagEnabled) {
            newOmnibar.decorate(Decoration.LaunchTrackersAnimation(events))
        } else {
            legacyOmnibar.startTrackersAnimation(events)
        }
    }

    fun configureCustomTab(
        context: Context,
        customTabToolbarColor: Int,
        customTabDomainText: String?,
        onTabClosePressed: () -> Unit,
        onPrivacyShieldPressed: () -> Unit,
    ) {
        if (refactorFlagEnabled) {
            newOmnibar.decorate(Mode(CustomTab(customTabToolbarColor, customTabDomainText)))
        } else {
            configureLegacyCustomTab(context, customTabToolbarColor, customTabDomainText, onTabClosePressed, onPrivacyShieldPressed)
        }
    }

    private fun configureLegacyCustomTab(
        context: Context,
        customTabToolbarColor: Int,
        customTabDomainText: String?,
        onTabClosePressed: () -> Unit,
        onPrivacyShieldPressed: () -> Unit,
    ) {
        omniBarContainer.hide()
        fireIconMenu.hide()
        tabsMenu.hide()

        toolbar.background = ColorDrawable(customTabToolbarColor)
        toolbarContainer.background = ColorDrawable(customTabToolbarColor)

        customTabToolbarContainer.customTabToolbar.show()

        customTabToolbarContainer.customTabCloseIcon.setOnClickListener {
            onTabClosePressed()
        }

        customTabToolbarContainer.customTabShieldIcon.setOnClickListener { _ ->
            onPrivacyShieldPressed()
        }

        customTabToolbarContainer.customTabDomain.text = customTabDomainText
        customTabToolbarContainer.customTabDomainOnly.text = customTabDomainText
        customTabToolbarContainer.customTabDomainOnly.show()

        val foregroundColor = calculateBlackOrWhite(context, customTabToolbarColor)
        customTabToolbarContainer.customTabCloseIcon.setColorFilter(foregroundColor)
        customTabToolbarContainer.customTabDomain.setTextColor(foregroundColor)
        customTabToolbarContainer.customTabDomainOnly.setTextColor(
            foregroundColor,
        )
        customTabToolbarContainer.customTabTitle.setTextColor(foregroundColor)
        browserMenuImageView.setColorFilter(foregroundColor)
    }

    private fun calculateBlackOrWhite(
        context: Context,
        color: Int,
    ): Int {
        // Handle the case where we did not receive a color.
        if (color == 0) {
            return if ((context as DuckDuckGoActivity).isDarkThemeEnabled()) Color.WHITE else Color.BLACK
        }

        if (color == Color.WHITE || Color.alpha(color) < 128) {
            return Color.BLACK
        }
        val greyValue =
            (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)).toInt()
        return if (greyValue < 186) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    fun showWebPageTitleInCustomTab(
        title: String,
        url: String?,
        showDuckPlayerIcon: Boolean,
    ) {
        val redirectedDomain = url?.extractDomain()

        if (refactorFlagEnabled) {
            newOmnibar.decorate(Decoration.ChangeCustomTabTitle(title, redirectedDomain, showDuckPlayerIcon))
        } else {
            customTabToolbarContainer.customTabTitle.text = title

            redirectedDomain?.let {
                customTabToolbarContainer.customTabDomain.text = redirectedDomain
            }

            customTabToolbarContainer.customTabTitle.show()
            customTabToolbarContainer.customTabDomainOnly.hide()
            customTabToolbarContainer.customTabDomain.show()
            customTabToolbarContainer.customTabShieldIcon.isInvisible = showDuckPlayerIcon
            customTabToolbarContainer.customTabDuckPlayerIcon.isVisible = showDuckPlayerIcon
        }
    }

    fun show() {
        if (refactorFlagEnabled) {
            newOmnibar.show()
        } else {
            legacyOmnibar.show()
        }
    }

    fun hide() {
        if (refactorFlagEnabled) {
            newOmnibar.gone()
        } else {
            legacyOmnibar.gone()
        }
    }
}
