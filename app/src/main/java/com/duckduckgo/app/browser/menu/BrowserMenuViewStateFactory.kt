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
import com.duckduckgo.app.browser.WebViewErrorResponse.OMITTED
import com.duckduckgo.app.browser.omnibar.Omnibar
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.browser.ui.browsermenu.BrowserMenuViewState
import com.duckduckgo.browser.ui.browsermenu.PageContextHeaderState
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface BrowserMenuViewStateFactory {
    fun create(
        omnibarViewMode: Omnibar.ViewMode,
        viewState: BrowserViewState,
        customTabsMode: Boolean,
        tabId: String,
        site: Site?,
        shortUrl: String?,
        omnibarText: String?,
    ): BrowserMenuViewState
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealBrowserMenuViewStateFactory @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
) : BrowserMenuViewStateFactory {
    override fun create(
        omnibarViewMode: Omnibar.ViewMode,
        viewState: BrowserViewState,
        customTabsMode: Boolean,
        tabId: String,
        site: Site?,
        shortUrl: String?,
        omnibarText: String?,
    ): BrowserMenuViewState {
        return if (customTabsMode) {
            createCustomTabsViewState(viewState, site, tabId, shortUrl, omnibarText)
        } else {
            when (omnibarViewMode) {
                Omnibar.ViewMode.NewTab -> createNewTabPageViewState(viewState)
                Omnibar.ViewMode.DuckAI -> createDuckAiViewState(viewState, site, tabId)
                Omnibar.ViewMode.Error -> createNewTabPageViewState(viewState)
                Omnibar.ViewMode.SSLWarning -> createNewTabPageViewState(viewState)
                Omnibar.ViewMode.MaliciousSiteWarning -> createNewTabPageViewState(viewState)
                else -> createBrowserViewState(viewState, site, tabId, shortUrl, omnibarText)
            }
        }
    }

    private fun createCustomTabsViewState(
        browserViewState: BrowserViewState,
        site: Site?,
        tabId: String,
        shortUrl: String?,
        omnibarText: String?,
    ): BrowserMenuViewState.CustomTabs {
        return BrowserMenuViewState.CustomTabs(
            canGoBack = browserViewState.canGoBack,
            canGoForward = browserViewState.canGoForward,
            canSharePage = browserViewState.canSharePage,
            canChangeBrowsingMode = browserViewState.canChangeBrowsingMode,
            isDesktopBrowsingMode = browserViewState.isDesktopBrowsingMode,
            canChangePrivacyProtection = browserViewState.canChangePrivacyProtection,
            isPrivacyProtectionDisabled = browserViewState.isPrivacyProtectionDisabled,
            pageContextHeader = createBrowserHeaderContextState(browserViewState, site, tabId, shortUrl, omnibarText),
        )
    }

    private fun createNewTabPageViewState(
        browserViewState: BrowserViewState,
    ): BrowserMenuViewState.NewTabPage {
        return BrowserMenuViewState.NewTabPage(
            showDuckChatOption = browserViewState.showDuckChatOption,
            vpnMenuState = browserViewState.vpnMenuState,
            showAutofill = browserViewState.showAutofill,
            canGoForward = browserViewState.canGoForward,
        )
    }

    private fun createDuckAiViewState(
        browserViewState: BrowserViewState,
        site: Site?,
        tabId: String,
    ): BrowserMenuViewState.DuckAi {
        return BrowserMenuViewState.DuckAi(
            canPrintPage = browserViewState.canPrintPage,
            canReportSite = browserViewState.canReportSite,
            showAutofill = browserViewState.showAutofill,
            pageContextHeader = PageContextHeaderState.DuckAi(title = site?.title, tabId = tabId),
        )
    }

    private fun createBrowserViewState(
        browserViewState: BrowserViewState,
        site: Site?,
        tabId: String,
        shortUrl: String?,
        omnibarText: String?,
    ): BrowserMenuViewState.Browser {
        val isDuckAIFullscreenModeEnabled = duckAiFeatureState.showFullScreenMode.value
        return BrowserMenuViewState.Browser(
            canGoBack = browserViewState.canGoBack,
            canGoForward = browserViewState.canGoForward,
            showDuckChatOption = browserViewState.showDuckChatOption && !isDuckAIFullscreenModeEnabled,
            showNewDuckChatTabOption = isDuckAIFullscreenModeEnabled,
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
            pageContextHeader = createBrowserHeaderContextState(browserViewState, site, tabId, shortUrl, omnibarText),
        )
    }

    private fun createBrowserHeaderContextState(
        viewState: BrowserViewState,
        site: Site?,
        tabId: String,
        shortUrl: String?,
        omnibarText: String?,
    ): PageContextHeaderState {
        val isErrorMode = viewState.browserError != OMITTED
        return if (site != null && shortUrl != null) {
            if (isErrorMode) {
                PageContextHeaderState.Error(shortUrl)
            } else {
                PageContextHeaderState.Visible(
                    title = site.title,
                    shortUrl = shortUrl,
                    tabId = tabId,
                )
            }
        } else if (isErrorMode && omnibarText != null) {
            PageContextHeaderState.Error(omnibarText)
        } else {
            PageContextHeaderState.Hidden
        }
    }
}
