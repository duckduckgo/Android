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

package com.duckduckgo.app.browser.menu

import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.browser.ui.browsermenu.BrowserMenuViewState

object RealBrowserMenuViewStateFactory {
    fun create(
        viewMode: Omnibar.ViewMode,
        viewState: BrowserViewState,
    ): BrowserMenuViewState {
        return when (viewMode){
           is Omnibar.ViewMode.Browser -> createBrowserViewState(browserViewState = viewState, browserShowing = true)
            is Omnibar.ViewMode.CustomTab -> createCustomTabsViewState(viewState)
            Omnibar.ViewMode.NewTab -> createNewTabPageViewState(viewState)
            Omnibar.ViewMode.DuckAI -> BrowserMenuViewState.DuckAi
            else -> createBrowserViewState(browserViewState = viewState, browserShowing = false)
        }
    }

    private fun createCustomTabsViewState(
        browserViewState: BrowserViewState,
    ): BrowserMenuViewState.CustomTabs {
        return BrowserMenuViewState.CustomTabs(
            canGoBack = browserViewState.canGoBack,
            canGoForward = browserViewState.canGoForward,
            canSharePage = browserViewState.canSharePage,
            canChangePrivacyProtection = browserViewState.canChangePrivacyProtection,
            isPrivacyProtectionDisabled = browserViewState.isPrivacyProtectionDisabled,
        )
    }

    private fun createNewTabPageViewState(
        browserViewState: BrowserViewState,
    ): BrowserMenuViewState.NewTabPage {
        return BrowserMenuViewState.NewTabPage(
            showDuckChatOption = browserViewState.showDuckChatOption,
            vpnMenuState = browserViewState.vpnMenuState,
            showAutofill = browserViewState.showAutofill,
        )
    }

    private fun createBrowserViewState(
        browserViewState: BrowserViewState,
        browserShowing: Boolean,
    ): BrowserMenuViewState.Browser {
        return BrowserMenuViewState.Browser(
            browserShowing = browserShowing,
            canGoBack = browserViewState.canGoBack,
            canGoForward = browserViewState.canGoForward,
            showDuckChatOption = browserViewState.showDuckChatOption,
            canSharePage = browserViewState.canSharePage,
            showSelectDefaultBrowserMenuItem = browserViewState.showSelectDefaultBrowserMenuItem,
            canSaveSite = browserViewState.canSaveSite,
            isBookmark = browserViewState.bookmark != null,
            canFireproofSite = browserViewState.canFireproofSite,
            isFireproofWebsite = browserViewState.isFireproofWebsite,
            isEmailSignedIn = browserViewState.isEmailSignedIn,
            canChangeBrowsingMode = browserViewState.canChangeBrowsingMode,
            isDesktopBrowsingMode = browserViewState.isDesktopBrowsingMode,
            hasPreviousAppLink = browserViewState.previousAppLink != null,
            canFindInPage = browserViewState.canFindInPage,
            addToHomeVisible = browserViewState.addToHomeVisible,
            addToHomeEnabled = browserViewState.addToHomeEnabled,
            canChangePrivacyProtection = browserViewState.canChangePrivacyProtection,
            isPrivacyProtectionDisabled = browserViewState.isPrivacyProtectionDisabled,
            canReportSite = browserViewState.canReportSite,
            showAutofill = browserViewState.showAutofill,
            isSSLError = browserViewState.sslError != NONE,
            canPrintPage = browserViewState.canPrintPage,
        )
    }
}
