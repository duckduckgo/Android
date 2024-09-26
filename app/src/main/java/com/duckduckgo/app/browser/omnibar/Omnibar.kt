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
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.core.view.postDelayed
import androidx.core.view.updateLayoutParams
import com.duckduckgo.app.browser.BrowserTabFragment.Companion.KEYBOARD_DELAY
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.FragmentBrowserTabBinding
import com.duckduckgo.app.browser.omnibar.LegacyOmnibarView.FindInPageListener
import com.duckduckgo.app.browser.omnibar.LegacyOmnibarView.ItemPressedListener
import com.duckduckgo.app.browser.omnibar.LegacyOmnibarView.OmnibarTextState
import com.duckduckgo.app.browser.omnibar.LegacyOmnibarView.TextListener
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.Error
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.NewTab
import com.duckduckgo.app.browser.omnibar.Omnibar.ViewMode.SSLWarning
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.FindInPageViewState
import com.duckduckgo.app.browser.viewstate.OmnibarViewState
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.app.global.view.isDifferent
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
import com.duckduckgo.common.ui.view.text.TextChangedWatcher
import com.duckduckgo.common.utils.extractDomain
import com.duckduckgo.mobile.android.R as CommonR
import com.google.android.material.appbar.AppBarLayout.GONE
import com.google.android.material.appbar.AppBarLayout.VISIBLE

@SuppressLint("ClickableViewAccessibility")
class Omnibar(
    val omnibarPosition: OmnibarPosition,
    private val binding: FragmentBrowserTabBinding,
) {

    sealed class ViewMode {
        data object Error : ViewMode()
        data object SSLWarning : ViewMode()
        data object NewTab : ViewMode()
    }

    private val actionBarSize: Int by lazy {
        val array: TypedArray = binding.rootView.context.theme.obtainStyledAttributes(intArrayOf(android.R.attr.actionBarSize))
        val actionBarSize = array.getDimensionPixelSize(0, -1)
        array.recycle()
        actionBarSize
    }

    val legacyOmnibar: LegacyOmnibarView by lazy {
        when (omnibarPosition) {
            OmnibarPosition.TOP -> {
                binding.rootView.removeView(binding.legacyOmnibarBottom)
                binding.legacyOmnibar
            }

            OmnibarPosition.BOTTOM -> {
                binding.rootView.removeView(binding.legacyOmnibar)

                // remove the default top abb bar behavior
                removeAppBarBehavior(binding.autoCompleteSuggestionsList)
                removeAppBarBehavior(binding.browserLayout)
                removeAppBarBehavior(binding.focusedView)

                // add padding to the NTP to prevent the bottom toolbar from overlapping the settings button
                binding.includeNewBrowserTab.browserBackground.apply {
                    setPadding(paddingLeft, context.resources.getDimensionPixelSize(CommonR.dimen.keyline_2), paddingRight, actionBarSize)
                }

                // prevent the touch event leaking to the webView below
                binding.legacyOmnibarBottom.setOnTouchListener { _, _ -> true }

                binding.legacyOmnibarBottom
            }
        }
    }

    private fun removeAppBarBehavior(view: View) {
        view.updateLayoutParams<CoordinatorLayout.LayoutParams> {
            behavior = null
        }
    }

    val findInPage = legacyOmnibar.findInPage
    val omnibarTextInput = legacyOmnibar.omnibarTextInput
    val tabsMenu = legacyOmnibar.tabsMenu
    val fireIconMenu = legacyOmnibar.fireIconMenu
    val browserMenu = legacyOmnibar.browserMenu
    val omniBarContainer = legacyOmnibar.omniBarContainer
    val toolbar = legacyOmnibar.toolbar
    val toolbarContainer = legacyOmnibar.toolbarContainer
    val customTabToolbarContainer = legacyOmnibar.customTabToolbarContainer
    val browserMenuImageView = legacyOmnibar.browserMenuImageView
    val shieldIcon = legacyOmnibar.shieldIcon
    val pageLoadingIndicator = legacyOmnibar.pageLoadingIndicator
    val searchIcon = legacyOmnibar.searchIcon
    val daxIcon = legacyOmnibar.daxIcon
    val clearTextButton = legacyOmnibar.clearTextButton
    val placeholder = legacyOmnibar.placeholder
    val voiceSearchButton = legacyOmnibar.voiceSearchButton
    val spacer = legacyOmnibar.spacer

    fun setViewMode(viewMode: ViewMode) {
        when (viewMode) {
            Error -> {
                setExpanded(true)
                shieldIcon.isInvisible = true
            }
            NewTab -> {
                setScrollingEnabled(false)
                setExpanded(true)
            }
            SSLWarning -> {
                setExpanded(true)
                shieldIcon.isInvisible = true
                searchIcon.isInvisible = true
                daxIcon.isInvisible = true
            }
        }
    }

    fun setExpanded(expanded: Boolean) {
        legacyOmnibar.setExpanded(expanded)
    }

    fun setExpanded(
        expanded: Boolean,
        animate: Boolean,
    ) {
        legacyOmnibar.setExpanded(expanded, animate)
    }

    fun configureItemPressedListeners(listener: ItemPressedListener) {
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

    fun addTextChangedListeners(
        onFindInPageTextChanged: (String) -> Unit,
        onOmnibarTextChanged: (OmnibarTextState) -> Unit,
        onShowSuggestions: (OmnibarTextState) -> Unit,
    ) {
        findInPage.findInPageInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    onFindInPageTextChanged(findInPage.findInPageInput.text.toString())
                }
            },
        )

        omnibarTextInput.replaceTextChangedListener(
            object : TextChangedWatcher() {
                override fun afterTextChanged(editable: Editable) {
                    onOmnibarTextChanged(
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
                onShowSuggestions(
                    OmnibarTextState(
                        omnibarTextInput.text.toString(),
                        omnibarTextInput.hasFocus(),
                    ),
                )
            }
        }
    }

    fun addTextListener(listener: TextListener) {
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
    }

    fun configureFindInPage(listener: FindInPageListener) {
        findInPage.findInPageInput.setOnFocusChangeListener { _, hasFocus ->
            listener.onFocusChanged(hasFocus, findInPage.findInPageInput.text.toString())
        }

        findInPage.previousSearchTermButton.setOnClickListener { listener.onPreviousSearchItemPressed() }
        findInPage.nextSearchTermButton.setOnClickListener { listener.onNextSearchItemPressed() }
        findInPage.closeFindInPagePanel.setOnClickListener { listener.onClosePressed() }
    }

    fun renderOmnibarViewState(viewState: OmnibarViewState) {
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
        legacyOmnibar.setPrivacyShield(isCustomTab, privacyShield)
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
        return legacyOmnibar.isPulseAnimationPlaying()
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

    fun showTextSpacer(
        showClearButton: Boolean,
        showVoiceSearch: Boolean,
    ) {
        spacer.isVisible = showVoiceSearch && showClearButton
    }

    fun showOutline(pressed: Boolean) {
        omniBarContainer.isPressed = pressed
    }

    fun renderBrowserViewState(
        viewState: BrowserViewState,
        tabDisplayedInCustomTabScreen: Boolean,
    ) {
        legacyOmnibar.renderBrowserViewState(viewState, tabDisplayedInCustomTabScreen)
    }

    fun animateTabsCount() {
        tabsMenu.animateCount()
    }

    fun renderTabIcon(tabs: List<TabEntity>) {
        tabsMenu.count = tabs.count()
        tabsMenu.hasUnread = tabs.firstOrNull { !it.viewed } != null
    }

    fun incrementTabs(onTabsIncremented: () -> Unit) {
        setExpanded(true, true)
        tabsMenu.increment {
            onTabsIncremented()
        }
    }

    fun createCookiesAnimation(isCosmetic: Boolean) {
        legacyOmnibar.createCookiesAnimation(isCosmetic)
    }

    fun cancelTrackersAnimation() {
        legacyOmnibar.cancelTrackersAnimation()
    }

    fun startTrackersAnimation(events: List<Entity>?) {
        legacyOmnibar.startTrackersAnimation(events)
    }

    fun onNewProgress(
        newProgress: Int,
        onAnimationEnd: (Animator?) -> Unit,
    ) {
        legacyOmnibar.onNewProgress(newProgress, onAnimationEnd)
    }

    fun setScrollingEnabled(enabled: Boolean) {
        legacyOmnibar.setScrollingEnabled(enabled)
    }

    fun configureCustomTab(
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
    ) {
        customTabToolbarContainer.customTabTitle.text = title

        val redirectedDomain = url?.extractDomain()
        redirectedDomain?.let {
            customTabToolbarContainer.customTabDomain.text = redirectedDomain
        }

        customTabToolbarContainer.customTabTitle.show()
        customTabToolbarContainer.customTabDomainOnly.hide()
        customTabToolbarContainer.customTabDomain.show()
    }
}

fun EditText.replaceTextChangedListener(textWatcher: TextChangedWatcher) {
    removeTextChangedListener(textWatcher)
    addTextChangedListener(textWatcher)
}
