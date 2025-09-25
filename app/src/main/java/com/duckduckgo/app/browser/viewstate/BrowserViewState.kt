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

package com.duckduckgo.app.browser.viewstate

import com.duckduckgo.app.browser.SSLErrorType
import com.duckduckgo.app.browser.SpecialUrlDetector
import com.duckduckgo.app.browser.WebViewErrorResponse
import com.duckduckgo.app.browser.omnibar.QueryOrigin
import com.duckduckgo.app.global.model.MaliciousSiteStatus
import com.duckduckgo.privacyprotectionspopup.api.PrivacyProtectionsPopupViewState
import com.duckduckgo.savedsites.api.models.SavedSite

data class BrowserViewState(
    val browserShowing: Boolean = false,
    val isFullScreen: Boolean = false,
    val isDesktopBrowsingMode: Boolean = false,
    val canChangeBrowsingMode: Boolean = false,
    val showPrivacyShield: HighlightableButton = HighlightableButton.Visible(enabled = false),
    val fireButton: HighlightableButton = HighlightableButton.Visible(),
    val showMenuButton: HighlightableButton = HighlightableButton.Visible(),
    val showSelectDefaultBrowserMenuItem: Boolean = false,
    val canSharePage: Boolean = false,
    val canSaveSite: Boolean = false,
    val bookmark: SavedSite.Bookmark? = null,
    val favorite: SavedSite.Favorite? = null,
    val canFireproofSite: Boolean = false,
    val isFireproofWebsite: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val canChangePrivacyProtection: Boolean = false,
    val isPrivacyProtectionDisabled: Boolean = false,
    val canReportSite: Boolean = false,
    val addToHomeEnabled: Boolean = false,
    val addToHomeVisible: Boolean = false,
    val isEmailSignedIn: Boolean = false,
    var previousAppLink: SpecialUrlDetector.UrlType.AppLink? = null,
    val canFindInPage: Boolean = false,
    val forceRenderingTicker: Long = System.currentTimeMillis(),
    val canPrintPage: Boolean = false,
    val isPrinting: Boolean = false,
    val showAutofill: Boolean = false,
    val browserError: WebViewErrorResponse = WebViewErrorResponse.OMITTED,
    val sslError: SSLErrorType = SSLErrorType.NONE,
    val maliciousSiteBlocked: Boolean = false,
    val maliciousSiteStatus: MaliciousSiteStatus? = null,
    val privacyProtectionsPopupViewState: PrivacyProtectionsPopupViewState = PrivacyProtectionsPopupViewState.Gone,
    val showDuckChatOption: Boolean = false,
    val lastQueryOrigin: QueryOrigin = QueryOrigin.FromUser,
    val vpnMenuState: VpnMenuState = VpnMenuState.Hidden,
)

sealed class VpnMenuState {
    data object Hidden : VpnMenuState()

    data object NotSubscribed : VpnMenuState()

    data object NotSubscribedNoPill : VpnMenuState()

    data class Subscribed(
        val isVpnEnabled: Boolean,
    ) : VpnMenuState()
}

sealed class HighlightableButton {
    data class Visible(
        val enabled: Boolean = true,
        val highlighted: Boolean = false,
    ) : HighlightableButton()

    data object Gone : HighlightableButton()

    fun isHighlighted(): Boolean =
        when (this) {
            is Visible -> this.highlighted
            is Gone -> false
        }

    fun isEnabled(): Boolean =
        when (this) {
            is Visible -> this.enabled
            is Gone -> false
        }
}
