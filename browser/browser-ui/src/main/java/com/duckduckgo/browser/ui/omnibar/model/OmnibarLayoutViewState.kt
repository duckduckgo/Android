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

package com.duckduckgo.browser.ui.omnibar.model

import com.duckduckgo.app.browser.omnibar.ViewMode
import com.duckduckgo.app.browser.omnibar.ViewMode.Browser
import com.duckduckgo.app.browser.omnibar.ViewMode.MaliciousSiteWarning
import com.duckduckgo.app.global.model.PrivacyShield

data class OmnibarLayoutViewState(
    val viewMode: ViewMode = Browser(null),
    val leadingIconState: LeadingIconState = LeadingIconState.Search,
    val previousLeadingIconState: LeadingIconState? = null,
    val privacyShield: PrivacyShield = PrivacyShield.UNKNOWN,
    val hasFocus: Boolean = false,
    val query: String = "",
    val omnibarText: String = "",
    val url: String = "",
    val expanded: Boolean = false,
    val expandedAnimated: Boolean = false,
    val updateOmnibarText: Boolean = false,
    val tabCount: Int = 0,
    val hasUnreadTabs: Boolean = false,
    val shouldUpdateTabsCount: Boolean = false,
    val showVoiceSearch: Boolean = false,
    val showClearButton: Boolean = false,
    val showTabsMenu: Boolean = true,
    val showFireIcon: Boolean = true,
    val showBrowserMenu: Boolean = true,
    val showChatMenu: Boolean = true,
    val showBrowserMenuHighlight: Boolean = false,
    val scrollingEnabled: Boolean = true,
    val isLoading: Boolean = false,
    val loadingProgress: Int = 0,
    val highlightPrivacyShield: HighlightableButton = HighlightableButton.Visible(enabled = false),
    val highlightFireButton: HighlightableButton = HighlightableButton.Visible(),
    val trackersBlocked: Int = 0,
    val previouslyTrackersBlocked: Int = 0,
    val showShadows: Boolean = false,
    val showTextInputClickCatcher: Boolean = false,
    val showFindInPage: Boolean = false,
    val showDuckAIHeader: Boolean = false,
    val showDuckAISidebar: Boolean = false,
    val isAddressBarTrackersAnimationEnabled: Boolean = false,
) {
    fun shouldUpdateOmnibarText(
        isFullUrlEnabled: Boolean,
    ): Boolean {
        val updateOmnibarText =
            this.viewMode is Browser || this.viewMode is MaliciousSiteWarning || (!isFullUrlEnabled && omnibarText.isNotEmpty())
        return updateOmnibarText && url.isNotEmpty()
    }
}
