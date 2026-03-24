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

package com.duckduckgo.browser.ui.omnibar

import com.duckduckgo.app.browser.omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.model.Decoration
import com.duckduckgo.app.global.model.PrivacyShield
import com.duckduckgo.browser.ui.omnibar.model.OmnibarLayoutViewState
import com.duckduckgo.browser.ui.omnibar.model.StateChange

/**
 * Callback interface that bridges OmnibarLayout (in browser-ui) with its host (typically the app module).
 * All ViewModel-like operations that OmnibarLayout previously called directly on its ViewModel are delegated
 * through this interface instead, keeping the UI component decoupled from any specific ViewModel.
 */
interface OmnibarLayoutCallback {
    // Focus and text input events
    fun onOmnibarFocusChanged(hasFocus: Boolean, text: String)
    fun onBackKeyPressed()
    fun onEnterKeyPressed()
    fun onUserTouchedOmnibarTextInput(action: Int)
    fun onInputStateChanged(text: String, hasFocus: Boolean, clearQuery: Boolean, deleteLastCharacter: Boolean)
    fun onClearTextButtonPressed()
    fun onTextInputClickCatcherClicked()

    // Button press events
    fun onFireIconPressed(isPulseAnimationPlaying: Boolean)
    fun onDuckChatButtonPressed()
    fun onPrivacyShieldButtonPressed()
    fun onBackButtonPressed()
    fun onDuckAiHeaderClicked()
    fun onLogoClicked()

    // Decoration events
    fun onViewModeChanged(viewMode: ViewMode)
    fun onPrivacyShieldChanged(privacyShield: PrivacyShield)
    fun onAnimationStarted(decoration: Decoration)
    fun onCustomTabTitleUpdate(decoration: Decoration.ChangeCustomTabTitle)
    fun onHighlightItem(decoration: Decoration.HighlightOmnibarItem)
    fun onVoiceSearchDisabled(url: String)
    fun onCancelAddressBarAnimations()

    // State management
    fun onExternalStateChange(stateChange: StateChange)
    fun onOmnibarScrollingEnabledChanged(isEnabled: Boolean)
    fun onFindInPageRequested()
    fun onFindInPageDismissed()
    fun setDraftTextIfNtpOrSerp(query: String)

    // Custom tab pixel events
    fun onCustomTabAddressBarClicked()
    fun onCustomTabDaxClicked()
    fun onCustomTabTrackerAnimationClicked()

    // Current state queries (replaces viewModel.viewState.value reads)
    val currentViewState: OmnibarLayoutViewState
}
