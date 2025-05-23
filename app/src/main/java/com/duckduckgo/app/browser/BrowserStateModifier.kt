/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser

import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.HighlightableButton
import com.duckduckgo.app.global.model.MaliciousSiteStatus
import io.reactivex.annotations.CheckReturnValue

class BrowserStateModifier {

    @CheckReturnValue
    fun copyForBrowserShowing(original: BrowserViewState): BrowserViewState {
        return original.copy(
            browserShowing = true,
            canChangePrivacyProtection = true,
            canFireproofSite = true,
            canReportSite = true,
            canSharePage = true,
            canSaveSite = true,
            canChangeBrowsingMode = true,
            canFindInPage = true,
            addToHomeEnabled = true,
            canPrintPage = true,
            maliciousSiteBlocked = false,
            maliciousSiteStatus = null,
        )
    }

    @CheckReturnValue
    fun copyForMaliciousSiteWarningShowing(original: BrowserViewState, maliciousSiteStatus: MaliciousSiteStatus): BrowserViewState {
        return original.copy(
            browserShowing = false,
            showPrivacyShield = HighlightableButton.Gone,
            fireButton = HighlightableButton.Gone,
            maliciousSiteBlocked = true,
            maliciousSiteStatus = maliciousSiteStatus,
            canChangePrivacyProtection = false,
            canFireproofSite = false,
            canReportSite = false,
            canSharePage = false,
            canSaveSite = false,
            canFindInPage = false,
            canChangeBrowsingMode = false,
            canPrintPage = false,
        )
    }

    @CheckReturnValue
    fun copyForHomeShowing(original: BrowserViewState): BrowserViewState {
        return original.copy(
            browserShowing = false,
            canChangePrivacyProtection = false,
            canFireproofSite = false,
            canReportSite = false,
            canSharePage = false,
            canSaveSite = false,
            canFindInPage = false,
            canChangeBrowsingMode = false,
            addToHomeEnabled = false,
            canGoBack = false,
            canPrintPage = false,
            maliciousSiteBlocked = false,
            maliciousSiteStatus = null,
        )
    }
}
