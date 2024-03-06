/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Context
import android.view.LayoutInflater
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.BrowserTabViewModel.BrowserViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.R.dimen
import com.duckduckgo.mobile.android.R.drawable

class BrowserPopupMenu(
    context: Context,
    layoutInflater: LayoutInflater,
) : PopupMenu(
    layoutInflater,
    resourceId = R.layout.popup_window_browser_menu,
    width = context.resources.getDimensionPixelSize(dimen.popupMenuWidth),
) {
    private val binding = PopupWindowBrowserMenuBinding.inflate(layoutInflater)

    init {
        contentView = binding.root
    }

    fun renderState(
        browserShowing: Boolean,
        viewState: BrowserViewState,
    ) {
        contentView.apply {
            binding.backMenuItem.isEnabled = viewState.canGoBack
            binding.forwardMenuItem.isEnabled = viewState.canGoForward
            binding.refreshMenuItem.isEnabled = browserShowing
            binding.printPageMenuItem.isEnabled = browserShowing

            binding.newTabMenuItem.isVisible = browserShowing
            binding.sharePageMenuItem.isVisible = viewState.canSharePage
            binding.addBookmarksMenuItem.isVisible = viewState.canSaveSite
            val isBookmark = viewState.bookmark != null
            binding.addBookmarksMenuItem.label {
                context.getString(if (isBookmark) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
            }
            binding.addBookmarksMenuItem.setIcon(if (isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

            binding.fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite
            binding.fireproofWebsiteMenuItem.label {
                context.getString(
                    if (viewState.isFireproofWebsite) {
                        R.string.fireproofWebsiteMenuTitleRemove
                    } else {
                        R.string.fireproofWebsiteMenuTitleAdd
                    },
                )
            }
            binding.fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproofed_16)

            binding.createAliasMenuItem.isVisible = viewState.isEmailSignedIn

            binding.changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
            binding.changeBrowserModeMenuItem.label {
                context.getString(
                    if (viewState.isDesktopBrowsingMode) {
                        R.string.requestMobileSiteMenuTitle
                    } else {
                        R.string.requestDesktopSiteMenuTitle
                    },
                )
            }
            binding.changeBrowserModeMenuItem.setIcon(
                if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_16 else drawable.ic_device_desktop_16,
            )

            binding.openInAppMenuItem.isVisible = viewState.previousAppLink != null
            binding.findInPageMenuItem.isVisible = viewState.canFindInPage
            binding.addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled
            binding.privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
            binding.privacyProtectionMenuItem.label {
                context.getText(
                    if (viewState.isPrivacyProtectionDisabled) {
                        R.string.enablePrivacyProtection
                    } else {
                        R.string.disablePrivacyProtection
                    },
                ).toString()
            }
            binding.privacyProtectionMenuItem.setIcon(
                if (viewState.isPrivacyProtectionDisabled) drawable.ic_protections_16 else drawable.ic_protections_blocked_16,
            )
            binding.brokenSiteMenuItem.isVisible = viewState.canReportSite

            binding.siteOptionsMenuDivider.isVisible = viewState.browserShowing
            binding.browserOptionsMenuDivider.isVisible = viewState.browserShowing
            binding.settingsMenuDivider.isVisible = viewState.browserShowing
            binding.printPageMenuItem.isVisible = viewState.canPrintPage
            binding.autofillMenuItem.isVisible = viewState.showAutofill
        }
    }
}
