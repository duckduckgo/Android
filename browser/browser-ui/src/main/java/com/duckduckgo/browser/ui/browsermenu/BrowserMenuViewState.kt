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

package com.duckduckgo.browser.ui.browsermenu

sealed class BrowserMenuViewState {

    data class Browser(
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val showDuckChatOption: Boolean = false,
        val showNewDuckChatTabOption: Boolean = false,
        val canSharePage: Boolean = false,
        val showSelectDefaultBrowserMenuItem: Boolean = false,
        val canSaveSite: Boolean = false,
        val isBookmark: Boolean = false,
        val canFireproofSite: Boolean = false,
        val isFireproofWebsite: Boolean = false,
        val isEmailSignedIn: Boolean = false,
        val canChangeBrowsingMode: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false,
        val hasPreviousAppLink: Boolean = false,
        val canFindInPage: Boolean = false,
        val addToHomeVisible: Boolean = false,
        val addToHomeEnabled: Boolean = false,
        val canChangePrivacyProtection: Boolean = false,
        val isPrivacyProtectionDisabled: Boolean = false,
        val canReportSite: Boolean = false,
        val showAutofill: Boolean = false,
        val isSSLError: Boolean = false,
        val canPrintPage: Boolean = false,

    ) : BrowserMenuViewState()
    data class CustomTabs(
        val canGoBack: Boolean = false,
        val canGoForward: Boolean = false,
        val canSharePage: Boolean = false,
        val canChangeBrowsingMode: Boolean = false,
        val isDesktopBrowsingMode: Boolean = false,
        val canFindInPage: Boolean = true,
        val canChangePrivacyProtection: Boolean = false,
        val isPrivacyProtectionDisabled: Boolean = false,
    ) : BrowserMenuViewState()
    data class NewTabPage(
        val canGoForward: Boolean = false,
        val showDuckChatOption: Boolean = false,
        val vpnMenuState: VpnMenuState = VpnMenuState.Hidden,
        val showAutofill: Boolean = false,
    ) : BrowserMenuViewState()
    data class DuckAi(
        val canPrintPage: Boolean = false,
        val canReportSite: Boolean = false,
        val showAutofill: Boolean = false,
    ) : BrowserMenuViewState()
}

sealed class VpnMenuState {
    data object Hidden : VpnMenuState()

    data object NotSubscribed : VpnMenuState()

    data object NotSubscribedNoPill : VpnMenuState()

    data class Subscribed(
        val isVpnEnabled: Boolean,
    ) : VpnMenuState()
}
