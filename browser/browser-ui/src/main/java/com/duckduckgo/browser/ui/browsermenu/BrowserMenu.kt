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

package com.duckduckgo.browser.ui.browsermenu

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.browser.ui.databinding.PopupWindowBrowserMenuBottomBinding
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.mobile.android.R.drawable

class BrowserMenu(
    private val context: Context,
    layoutInflater: LayoutInflater,
    private val omnibarType: OmnibarType,
) : PopupMenu(
    layoutInflater,
    resourceId = if (omnibarType == OmnibarType.SINGLE_BOTTOM) R.layout.popup_window_browser_menu_bottom else R.layout.popup_window_browser_menu,
    width = context.resources.getDimensionPixelSize(R.dimen.browserPopupMenuWidth),
) {
    private val topBinding = PopupWindowBrowserMenuBinding.bind(contentView)
    private val bottomBinding = PopupWindowBrowserMenuBottomBinding.bind(contentView)

    init {
        contentView =
            when (omnibarType) {
                OmnibarType.SINGLE_BOTTOM -> bottomBinding.root
                else -> topBinding.root
            }
    }

    internal val backMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.backMenuItem
            else -> topBinding.backMenuItem
        }
    }

    internal val forwardMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.forwardMenuItem
            else -> topBinding.forwardMenuItem
        }
    }

    internal val refreshMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.refreshMenuItem
            else -> topBinding.refreshMenuItem
        }
    }

    internal val printPageMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.printPageMenuItem
            else -> topBinding.printPageMenuItem
        }
    }

    internal val newTabMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.newTabMenuItem
            else -> topBinding.newTabMenuItem
        }
    }

    internal val defaultBrowserMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem
            else -> topBinding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem
        }
    }

    internal val vpnMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.includeVpnMenuItem.vpnMenuItem
            else -> topBinding.includeVpnMenuItem.vpnMenuItem
        }
    }

    internal val duckChatMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.includeDuckChatMenuItem
            else -> topBinding.includeDuckChatMenuItem
        }
    }

    internal val sharePageMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.sharePageMenuItem
            else -> topBinding.sharePageMenuItem
        }
    }

    internal val bookmarksMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.bookmarksMenuItem
            else -> topBinding.bookmarksMenuItem
        }
    }

    internal val downloadsMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.downloadsMenuItem
            else -> topBinding.downloadsMenuItem
        }
    }

    internal val settingsMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.settingsMenuItem
            else -> topBinding.settingsMenuItem
        }
    }

    internal val addBookmarksMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.addBookmarksMenuItem
            else -> topBinding.addBookmarksMenuItem
        }
    }

    internal val fireproofWebsiteMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.fireproofWebsiteMenuItem
            else -> topBinding.fireproofWebsiteMenuItem
        }
    }

    internal val createAliasMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.createAliasMenuItem
            else -> topBinding.createAliasMenuItem
        }
    }

    internal val changeBrowserModeMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.changeBrowserModeMenuItem
            else -> topBinding.changeBrowserModeMenuItem
        }
    }

    internal val openInAppMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.openInAppMenuItem
            else -> topBinding.openInAppMenuItem
        }
    }

    internal val findInPageMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.findInPageMenuItem
            else -> topBinding.findInPageMenuItem
        }
    }

    internal val addToHomeMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.addToHomeMenuItem
            else -> topBinding.addToHomeMenuItem
        }
    }

    internal val privacyProtectionMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.privacyProtectionMenuItem
            else -> topBinding.privacyProtectionMenuItem
        }
    }

    internal val brokenSiteMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.brokenSiteMenuItem
            else -> topBinding.brokenSiteMenuItem
        }
    }

    internal val autofillMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.autofillMenuItem
            else -> topBinding.autofillMenuItem
        }
    }

    internal val runningInDdgBrowserMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.runningInDdgBrowserMenuItem
            else -> topBinding.runningInDdgBrowserMenuItem
        }
    }

    internal val siteOptionsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.siteOptionsMenuDivider
            else -> topBinding.siteOptionsMenuDivider
        }
    }

    internal val browserOptionsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.browserOptionsMenuDivider
            else -> topBinding.browserOptionsMenuDivider
        }
    }

    internal val settingsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.settingsMenuDivider
            else -> topBinding.settingsMenuDivider
        }
    }

    internal val customTabsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.customTabsMenuDivider
            else -> topBinding.customTabsMenuDivider
        }
    }

    internal val openInDdgBrowserMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.openInDdgBrowserMenuItem
            else -> topBinding.openInDdgBrowserMenuItem
        }
    }

    fun render(viewState: BrowserMenuViewState) {
        when (viewState) {
            is BrowserMenuViewState.Browser -> renderBrowserMenu(viewState)
            is BrowserMenuViewState.CustomTabs -> renderCustomTabsMenu(viewState)
            is BrowserMenuViewState.NewTabPage -> renderNewTabPageMenu(viewState)
            is BrowserMenuViewState.DuckAi -> renderDuckAIMenu(viewState)
        }
    }

    fun renderBrowserMenu(viewState: BrowserMenuViewState.Browser) {
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward
        refreshMenuItem.isEnabled = viewState.browserShowing
        printPageMenuItem.isEnabled = viewState.browserShowing

        newTabMenuItem.isVisible = true
        duckChatMenuItem.isVisible = viewState.showDuckChatOption
        sharePageMenuItem.isVisible = viewState.canSharePage

        defaultBrowserMenuItem.isVisible = viewState.showSelectDefaultBrowserMenuItem

        bookmarksMenuItem.isVisible = true
        downloadsMenuItem.isVisible = true
        settingsMenuItem.isVisible = true

        addBookmarksMenuItem.isVisible = viewState.canSaveSite
        addBookmarksMenuItem.label {
            context.getString(if (viewState.isBookmark) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
        }
        addBookmarksMenuItem.setIcon(if (viewState.isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

        fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite
        fireproofWebsiteMenuItem.label {
            context.getString(
                if (viewState.isFireproofWebsite) {
                    R.string.fireproofWebsiteMenuTitleRemove
                } else {
                    R.string.fireproofWebsiteMenuTitleAdd
                },
            )
        }
        fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproof_solid_16)

        createAliasMenuItem.isVisible = viewState.isEmailSignedIn

        changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
        changeBrowserModeMenuItem.label {
            context.getString(
                if (viewState.isDesktopBrowsingMode) {
                    R.string.requestMobileSiteMenuTitle
                } else {
                    R.string.requestDesktopSiteMenuTitle
                },
            )
        }
        changeBrowserModeMenuItem.setIcon(
            if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_16 else drawable.ic_device_desktop_16,
        )

        openInAppMenuItem.isVisible = viewState.hasPreviousAppLink
        findInPageMenuItem.isVisible = viewState.canFindInPage
        addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled
        privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
        privacyProtectionMenuItem.label {
            context
                .getText(
                    if (viewState.isPrivacyProtectionDisabled) {
                        R.string.enablePrivacyProtection
                    } else {
                        R.string.disablePrivacyProtection
                    },
                ).toString()
        }
        privacyProtectionMenuItem.setIcon(
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_16 else drawable.ic_shield_disabled_16,
        )
        brokenSiteMenuItem.isVisible = viewState.canReportSite

        siteOptionsMenuDivider.isVisible = viewState.browserShowing
        browserOptionsMenuDivider.isVisible = viewState.browserShowing
        settingsMenuDivider.isVisible = viewState.browserShowing
        printPageMenuItem.isVisible = viewState.canPrintPage
        autofillMenuItem.isVisible = viewState.showAutofill

        vpnMenuItem.isVisible = false

        openInDdgBrowserMenuItem.isVisible = false
        customTabsMenuDivider.isVisible = false
        runningInDdgBrowserMenuItem.isVisible = false

        if (viewState.isSSLError) {
            newTabMenuItem.isVisible = true
            siteOptionsMenuDivider.isVisible = true
        }
    }

    fun renderCustomTabsMenu(viewState: BrowserMenuViewState.CustomTabs) {
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward

        refreshMenuItem.isEnabled = true
        printPageMenuItem.isEnabled = true

        sharePageMenuItem.isVisible = viewState.canSharePage

        openInDdgBrowserMenuItem.isVisible = true
        customTabsMenuDivider.isVisible = true
        runningInDdgBrowserMenuItem.isVisible = true
    }

    fun renderNewTabPageMenu(viewState: BrowserMenuViewState.NewTabPage) {
        backMenuItem.isEnabled = false
        forwardMenuItem.isEnabled = false
        refreshMenuItem.isEnabled = false

        newTabMenuItem.isVisible = false
        duckChatMenuItem.isVisible = viewState.showDuckChatOption

        when (viewState.vpnMenuState) {
            VpnMenuState.Hidden -> {
                vpnMenuItem.isVisible = false
            }
            VpnMenuState.NotSubscribed -> {
                vpnMenuItem.isVisible = true
                val (tryForFreePill, statusIndicator, menuItemView) = getVpnMenuViews()
                configureVpnMenuItemForNotSubscribed(tryForFreePill, statusIndicator, menuItemView)
            }
            VpnMenuState.NotSubscribedNoPill -> {
                vpnMenuItem.isVisible = true
                val (tryForFreePill, statusIndicator, menuItemView) = getVpnMenuViews()
                configureVpnMenuItemForNotSubscribedNoPill(tryForFreePill, statusIndicator, menuItemView)
            }
            is VpnMenuState.Subscribed -> {
                vpnMenuItem.isVisible = true
                val (tryForFreePill, statusIndicator, menuItemView) = getVpnMenuViews()
                configureVpnMenuItemForSubscribed(tryForFreePill, statusIndicator, menuItemView, viewState.vpnMenuState.isVpnEnabled)
            }
        }
    }

    fun renderDuckAIMenu(viewState: BrowserMenuViewState.DuckAi) {
    }

    private fun getVpnMenuViews() =
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> {
                Triple(
                    bottomBinding.includeVpnMenuItem.tryForFreePill,
                    bottomBinding.includeVpnMenuItem.statusIndicator,
                    bottomBinding.includeVpnMenuItem.menuItemView,
                )
            }

            else ->
                Triple(
                    topBinding.includeVpnMenuItem.tryForFreePill,
                    topBinding.includeVpnMenuItem.statusIndicator,
                    topBinding.includeVpnMenuItem.menuItemView,
                )
        }

    private fun configureVpnMenuItemForNotSubscribed(
        tryForFreePill: View,
        statusIndicator: StatusIndicatorView,
        menuItemView: MenuItemView,
    ) {
        tryForFreePill.isVisible = true
        statusIndicator.isVisible = false
        menuItemView.setIcon(drawable.ic_vpn_unlocked_24)
    }

    private fun configureVpnMenuItemForNotSubscribedNoPill(
        tryForFreePill: View,
        statusIndicator: StatusIndicatorView,
        menuItemView: MenuItemView,
    ) {
        tryForFreePill.isVisible = false
        statusIndicator.isVisible = false
        menuItemView.setIcon(drawable.ic_vpn_unlocked_24)
    }

    private fun configureVpnMenuItemForSubscribed(
        tryForFreePill: View,
        statusIndicator: StatusIndicatorView,
        menuItemView: MenuItemView,
        isVpnEnabled: Boolean,
    ) {
        tryForFreePill.isVisible = false
        statusIndicator.isVisible = true
        statusIndicator.setStatus(isVpnEnabled)

        val iconRes = if (isVpnEnabled) drawable.ic_vpn_24 else drawable.ic_vpn_unlocked_24
        menuItemView.setIcon(iconRes)
    }
}
