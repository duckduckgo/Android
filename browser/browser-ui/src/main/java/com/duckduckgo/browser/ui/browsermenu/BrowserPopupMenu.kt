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
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.omnibar.OmnibarType
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.browser.ui.databinding.PopupWindowBrowserMenuBottomBinding
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.mobile.android.R.drawable

class BrowserPopupMenu(
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

    val backMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.backMenuItem
            else -> topBinding.backMenuItem
        }
    }

    val forwardMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.forwardMenuItem
            else -> topBinding.forwardMenuItem
        }
    }

    val refreshMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.refreshMenuItem
            else -> topBinding.refreshMenuItem
        }
    }

    val printPageMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.printPageMenuItem
            else -> topBinding.printPageMenuItem
        }
    }

    val newTabMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.newTabMenuItem
            else -> topBinding.newTabMenuItem
        }
    }

    val defaultBrowserMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem
            else -> topBinding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem
        }
    }

    val vpnMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.includeVpnMenuItem.vpnMenuItem
            else -> topBinding.includeVpnMenuItem.vpnMenuItem
        }
    }

    val duckChatMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.includeDuckChatMenuItem
            else -> topBinding.includeDuckChatMenuItem
        }
    }

    val sharePageMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.sharePageMenuItem
            else -> topBinding.sharePageMenuItem
        }
    }

    val bookmarksMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.bookmarksMenuItem
            else -> topBinding.bookmarksMenuItem
        }
    }

    val downloadsMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.downloadsMenuItem
            else -> topBinding.downloadsMenuItem
        }
    }

    val settingsMenuItem: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.settingsMenuItem
            else -> topBinding.settingsMenuItem
        }
    }

    val addBookmarksMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.addBookmarksMenuItem
            else -> topBinding.addBookmarksMenuItem
        }
    }

    val fireproofWebsiteMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.fireproofWebsiteMenuItem
            else -> topBinding.fireproofWebsiteMenuItem
        }
    }

    val createAliasMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.createAliasMenuItem
            else -> topBinding.createAliasMenuItem
        }
    }

    val changeBrowserModeMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.changeBrowserModeMenuItem
            else -> topBinding.changeBrowserModeMenuItem
        }
    }

    val openInAppMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.openInAppMenuItem
            else -> topBinding.openInAppMenuItem
        }
    }

    val findInPageMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.findInPageMenuItem
            else -> topBinding.findInPageMenuItem
        }
    }

    val addToHomeMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.addToHomeMenuItem
            else -> topBinding.addToHomeMenuItem
        }
    }

    val privacyProtectionMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.privacyProtectionMenuItem
            else -> topBinding.privacyProtectionMenuItem
        }
    }

    val brokenSiteMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.brokenSiteMenuItem
            else -> topBinding.brokenSiteMenuItem
        }
    }

    val autofillMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.autofillMenuItem
            else -> topBinding.autofillMenuItem
        }
    }

    val runningInDdgBrowserMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.runningInDdgBrowserMenuItem
            else -> topBinding.runningInDdgBrowserMenuItem
        }
    }

    val siteOptionsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.siteOptionsMenuDivider
            else -> topBinding.siteOptionsMenuDivider
        }
    }

    val browserOptionsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.browserOptionsMenuDivider
            else -> topBinding.browserOptionsMenuDivider
        }
    }

    val settingsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.settingsMenuDivider
            else -> topBinding.settingsMenuDivider
        }
    }

    val customTabsMenuDivider: View by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.customTabsMenuDivider
            else -> topBinding.customTabsMenuDivider
        }
    }

    val openInDdgBrowserMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.openInDdgBrowserMenuItem
            else -> topBinding.openInDdgBrowserMenuItem
        }
    }

    private val menuItemsContainer: LinearLayout by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.menuItemsContainer
            else -> topBinding.menuItemsContainer
        }
    }

    val duckNewChatMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.newChatMenuItem
            else -> topBinding.newChatMenuItem
        }
    }

    val duckChatHistoryMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.chatHistoryMenuItem
            else -> topBinding.chatHistoryMenuItem
        }
    }

    val duckChatSettingsMenuItem: MenuItemView by lazy {
        when (omnibarType) {
            OmnibarType.SINGLE_BOTTOM -> bottomBinding.chatSettings
            else -> topBinding.chatSettings
        }
    }

    fun render(viewState: BrowserMenuViewState) {
        hideAllMenuItems()
        when (viewState) {
            is BrowserMenuViewState.Browser -> renderBrowserMenu(viewState)
            is BrowserMenuViewState.CustomTabs -> renderCustomTabsMenu(viewState)
            is BrowserMenuViewState.NewTabPage -> renderNewTabPageMenu(viewState)
            is BrowserMenuViewState.DuckAi -> renderDuckAIMenu(viewState)
        }
    }

    private fun hideAllMenuItems() {
        menuItemsContainer.children.forEach { menuItem ->
            menuItem.gone()
        }
    }

    /**
     * These are items available across all browser types
     */
    private fun showCommonItems() {
        bookmarksMenuItem.isVisible = true
        downloadsMenuItem.isVisible = true
        settingsMenuItem.isVisible = true
    }

    private fun renderBrowserMenu(viewState: BrowserMenuViewState.Browser) {
        showCommonItems()
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward
        refreshMenuItem.isEnabled = true
        printPageMenuItem.isEnabled = true

        newTabMenuItem.isVisible = true
        duckChatMenuItem.isVisible = viewState.showDuckChatOption
        duckNewChatMenuItem.isVisible = viewState.showNewDuckChatTabOption
        sharePageMenuItem.isVisible = viewState.canSharePage

        defaultBrowserMenuItem.isVisible = viewState.showSelectDefaultBrowserMenuItem

        addBookmarksMenuItem.isVisible = viewState.canSaveSite
        addBookmarksMenuItem.label {
            context.getString(if (viewState.isBookmark) R.string.browserMenuEditBookmark else R.string.browserMenuAddBookmark)
        }
        addBookmarksMenuItem.setIcon(if (viewState.isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

        fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite
        fireproofWebsiteMenuItem.label {
            context.getString(
                if (viewState.isFireproofWebsite) {
                    R.string.browserMenuRemoveFireproofing
                } else {
                    R.string.browserMenuFireproofSite
                },
            )
        }
        fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproof_solid_16)

        createAliasMenuItem.isVisible = viewState.isEmailSignedIn

        changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
        changeBrowserModeMenuItem.label {
            context.getString(
                if (viewState.isDesktopBrowsingMode) {
                    R.string.browserMenuMobileSite
                } else {
                    R.string.browserMenuDesktopSite
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
                        R.string.browserMenuEnablePrivacyProtection
                    } else {
                        R.string.browserMenuDisablePrivacyProtection
                    },
                ).toString()
        }
        privacyProtectionMenuItem.setIcon(
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_16 else drawable.ic_shield_disabled_16,
        )
        brokenSiteMenuItem.isVisible = viewState.canReportSite

        siteOptionsMenuDivider.isVisible = true
        browserOptionsMenuDivider.isVisible = true
        settingsMenuDivider.isVisible = true
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

    private fun renderCustomTabsMenu(viewState: BrowserMenuViewState.CustomTabs) {
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward

        refreshMenuItem.isEnabled = true
        printPageMenuItem.isEnabled = true

        sharePageMenuItem.isVisible = viewState.canSharePage
        findInPageMenuItem.isVisible = viewState.canFindInPage

        changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
        changeBrowserModeMenuItem.label {
            context.getString(
                if (viewState.isDesktopBrowsingMode) {
                    R.string.browserMenuMobileSite
                } else {
                    R.string.browserMenuDesktopSite
                },
            )
        }

        privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
        privacyProtectionMenuItem.label {
            context
                .getText(
                    if (viewState.isPrivacyProtectionDisabled) {
                        R.string.browserMenuEnablePrivacyProtection
                    } else {
                        R.string.browserMenuDisablePrivacyProtection
                    },
                ).toString()
        }
        privacyProtectionMenuItem.setIcon(
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_16 else drawable.ic_shield_disabled_16,
        )

        openInDdgBrowserMenuItem.isVisible = true
        customTabsMenuDivider.isVisible = true
        runningInDdgBrowserMenuItem.isVisible = true
    }

    private fun renderNewTabPageMenu(viewState: BrowserMenuViewState.NewTabPage) {
        showCommonItems()

        backMenuItem.isEnabled = false
        refreshMenuItem.isEnabled = false
        newTabMenuItem.isVisible = true

        forwardMenuItem.isEnabled = viewState.canGoForward
        duckChatMenuItem.isVisible = viewState.showDuckChatOption
        autofillMenuItem.isVisible = viewState.showAutofill

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

    private fun renderDuckAIMenu(viewState: BrowserMenuViewState.DuckAi) {
        showCommonItems()
        siteOptionsMenuDivider.isVisible = true
        browserOptionsMenuDivider.isVisible = true
        settingsMenuDivider.isVisible = true

        newTabMenuItem.isVisible = true

        brokenSiteMenuItem.isVisible = viewState.canReportSite
        printPageMenuItem.isVisible = viewState.canPrintPage
        autofillMenuItem.isVisible = viewState.showAutofill

        duckChatHistoryMenuItem.isVisible = true
        duckChatSettingsMenuItem.isVisible = true
        duckNewChatMenuItem.isVisible = true
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
