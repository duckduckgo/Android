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
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBottomBinding
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition.BOTTOM
import com.duckduckgo.app.browser.omnibar.model.OmnibarPosition.TOP
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.mobile.android.R.dimen
import com.duckduckgo.mobile.android.R.drawable

class BrowserPopupMenu(
    context: Context,
    layoutInflater: LayoutInflater,
    displayedInCustomTabScreen: Boolean,
    omnibarPosition: OmnibarPosition,
) : PopupMenu(
    layoutInflater,
    resourceId = if (omnibarPosition == TOP) R.layout.popup_window_browser_menu else R.layout.popup_window_browser_menu_bottom,
    width = context.resources.getDimensionPixelSize(dimen.popupMenuWidth),
) {
    private val topBinding = PopupWindowBrowserMenuBinding.bind(contentView)
    private val bottomBinding = PopupWindowBrowserMenuBottomBinding.bind(contentView)

    init {
        contentView = when (omnibarPosition) {
            TOP -> topBinding.root
            BOTTOM -> bottomBinding.root
        }
    }

    fun renderStateTop(
        browserShowing: Boolean,
        viewState: BrowserViewState,
        displayedInCustomTabScreen: Boolean,
    ) {
        contentView.apply {
            topBinding.backMenuItem.isEnabled = viewState.canGoBack
            topBinding.forwardMenuItem.isEnabled = viewState.canGoForward
            topBinding.refreshMenuItem.isEnabled = browserShowing
            topBinding.printPageMenuItem.isEnabled = browserShowing

            topBinding.newTabMenuItem.isVisible = browserShowing && !displayedInCustomTabScreen
            topBinding.sharePageMenuItem.isVisible = viewState.canSharePage

            topBinding.bookmarksMenuItem.isVisible = !displayedInCustomTabScreen
            topBinding.downloadsMenuItem.isVisible = !displayedInCustomTabScreen
            topBinding.settingsMenuItem.isVisible = !displayedInCustomTabScreen

            topBinding.addBookmarksMenuItem.isVisible = viewState.canSaveSite && !displayedInCustomTabScreen
            val isBookmark = viewState.bookmark != null
            topBinding.addBookmarksMenuItem.label {
                context.getString(if (isBookmark) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
            }
            topBinding.addBookmarksMenuItem.setIcon(if (isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

            topBinding.fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite && !displayedInCustomTabScreen
            topBinding.fireproofWebsiteMenuItem.label {
                context.getString(
                    if (viewState.isFireproofWebsite) {
                        R.string.fireproofWebsiteMenuTitleRemove
                    } else {
                        R.string.fireproofWebsiteMenuTitleAdd
                    },
                )
            }
            topBinding.fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproofed_16)

            topBinding.createAliasMenuItem.isVisible = viewState.isEmailSignedIn && !displayedInCustomTabScreen

            topBinding.changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
            topBinding.changeBrowserModeMenuItem.label {
                context.getString(
                    if (viewState.isDesktopBrowsingMode) {
                        R.string.requestMobileSiteMenuTitle
                    } else {
                        R.string.requestDesktopSiteMenuTitle
                    },
                )
            }
            topBinding.changeBrowserModeMenuItem.setIcon(
                if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_16 else drawable.ic_device_desktop_16,
            )

            topBinding.openInAppMenuItem.isVisible = viewState.previousAppLink != null
            topBinding.findInPageMenuItem.isVisible = viewState.canFindInPage
            topBinding.addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled && !displayedInCustomTabScreen
            topBinding.privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
            topBinding.privacyProtectionMenuItem.label {
                context.getText(
                    if (viewState.isPrivacyProtectionDisabled) {
                        R.string.enablePrivacyProtection
                    } else {
                        R.string.disablePrivacyProtection
                    },
                ).toString()
            }
            topBinding.privacyProtectionMenuItem.setIcon(
                if (viewState.isPrivacyProtectionDisabled) drawable.ic_protections_16 else drawable.ic_protections_blocked_16,
            )
            topBinding.brokenSiteMenuItem.isVisible = viewState.canReportSite && !displayedInCustomTabScreen

            topBinding.siteOptionsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
            topBinding.browserOptionsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
            topBinding.settingsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
            topBinding.printPageMenuItem.isVisible = viewState.canPrintPage && !displayedInCustomTabScreen
            topBinding.autofillMenuItem.isVisible = viewState.showAutofill && !displayedInCustomTabScreen

            topBinding.openInDdgBrowserMenuItem.isVisible = displayedInCustomTabScreen
            topBinding.customTabsMenuDivider.isVisible = displayedInCustomTabScreen
            topBinding.runningInDdgBrowserMenuItem.isVisible = displayedInCustomTabScreen
            overrideForSSlError(topBinding, viewState)
        }
    }

    fun renderStateBottom(
        browserShowing: Boolean,
        viewState: BrowserViewState,
        displayedInCustomTabScreen: Boolean,
    ) {
        contentView.apply {
            bottomBinding.backMenuItem.isEnabled = viewState.canGoBack
            bottomBinding.forwardMenuItem.isEnabled = viewState.canGoForward
            bottomBinding.refreshMenuItem.isEnabled = browserShowing
            bottomBinding.printPageMenuItem.isEnabled = browserShowing

            bottomBinding.newTabMenuItem.isVisible = browserShowing && !displayedInCustomTabScreen
            bottomBinding.sharePageMenuItem.isVisible = viewState.canSharePage

            bottomBinding.bookmarksMenuItem.isVisible = !displayedInCustomTabScreen
            bottomBinding.downloadsMenuItem.isVisible = !displayedInCustomTabScreen
            bottomBinding.settingsMenuItem.isVisible = !displayedInCustomTabScreen

            bottomBinding.addBookmarksMenuItem.isVisible = viewState.canSaveSite && !displayedInCustomTabScreen
            val isBookmark = viewState.bookmark != null
            bottomBinding.addBookmarksMenuItem.label {
                context.getString(if (isBookmark) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
            }
            bottomBinding.addBookmarksMenuItem.setIcon(if (isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

            bottomBinding.fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite && !displayedInCustomTabScreen
            bottomBinding.fireproofWebsiteMenuItem.label {
                context.getString(
                    if (viewState.isFireproofWebsite) {
                        R.string.fireproofWebsiteMenuTitleRemove
                    } else {
                        R.string.fireproofWebsiteMenuTitleAdd
                    },
                )
            }
            bottomBinding.fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproofed_16)

            bottomBinding.createAliasMenuItem.isVisible = viewState.isEmailSignedIn && !displayedInCustomTabScreen

            bottomBinding.changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
            bottomBinding.changeBrowserModeMenuItem.label {
                context.getString(
                    if (viewState.isDesktopBrowsingMode) {
                        R.string.requestMobileSiteMenuTitle
                    } else {
                        R.string.requestDesktopSiteMenuTitle
                    },
                )
            }
            bottomBinding.changeBrowserModeMenuItem.setIcon(
                if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_16 else drawable.ic_device_desktop_16,
            )

            bottomBinding.openInAppMenuItem.isVisible = viewState.previousAppLink != null
            bottomBinding.findInPageMenuItem.isVisible = viewState.canFindInPage
            bottomBinding.addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled && !displayedInCustomTabScreen
            bottomBinding.privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
            bottomBinding.privacyProtectionMenuItem.label {
                context.getText(
                    if (viewState.isPrivacyProtectionDisabled) {
                        R.string.enablePrivacyProtection
                    } else {
                        R.string.disablePrivacyProtection
                    },
                ).toString()
            }
            bottomBinding.privacyProtectionMenuItem.setIcon(
                if (viewState.isPrivacyProtectionDisabled) drawable.ic_protections_16 else drawable.ic_protections_blocked_16,
            )
            bottomBinding.brokenSiteMenuItem.isVisible = viewState.canReportSite && !displayedInCustomTabScreen

            bottomBinding.siteOptionsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
            bottomBinding.browserOptionsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
            bottomBinding.settingsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
            bottomBinding.printPageMenuItem.isVisible = viewState.canPrintPage && !displayedInCustomTabScreen
            bottomBinding.autofillMenuItem.isVisible = viewState.showAutofill && !displayedInCustomTabScreen

            bottomBinding.openInDdgBrowserMenuItem.isVisible = displayedInCustomTabScreen
            bottomBinding.customTabsMenuDivider.isVisible = displayedInCustomTabScreen
            bottomBinding.runningInDdgBrowserMenuItem.isVisible = displayedInCustomTabScreen
            overrideForSSlErrorBottom(bottomBinding, viewState)
        }
    }

    private fun overrideForSSlError(
        binding: PopupWindowBrowserMenuBinding,
        viewState: BrowserViewState,
    ) {
        if (viewState.sslError != NONE) {
            binding.newTabMenuItem.isVisible = true
            binding.siteOptionsMenuDivider.isVisible = true
        }
    }

    private fun overrideForSSlErrorBottom(
        binding: PopupWindowBrowserMenuBottomBinding,
        viewState: BrowserViewState,
    ) {
        if (viewState.sslError != NONE) {
            binding.newTabMenuItem.isVisible = true
            binding.siteOptionsMenuDivider.isVisible = true
        }
    }
}
