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
import android.view.View
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.R
import com.duckduckgo.app.browser.SSLErrorType.NONE
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBinding
import com.duckduckgo.app.browser.databinding.PopupWindowBrowserMenuBottomBinding
import com.duckduckgo.app.browser.menu.BrowserPopupMenu.ResourceType.BOTTOM
import com.duckduckgo.app.browser.menu.BrowserPopupMenu.ResourceType.TOP
import com.duckduckgo.app.browser.viewstate.BrowserViewState
import com.duckduckgo.app.browser.viewstate.VpnMenuState
import com.duckduckgo.common.ui.menu.PopupMenu
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.mobile.android.R.drawable

class BrowserPopupMenu(
    private val context: Context,
    layoutInflater: LayoutInflater,
    private val popupMenuResourceType: ResourceType,
) : PopupMenu(
    layoutInflater,
    resourceId = if (popupMenuResourceType == TOP) R.layout.popup_window_browser_menu else R.layout.popup_window_browser_menu_bottom,
    width = context.resources.getDimensionPixelSize(R.dimen.browserPopupMenuWidth),
) {
    private val topBinding = PopupWindowBrowserMenuBinding.bind(contentView)
    private val bottomBinding = PopupWindowBrowserMenuBottomBinding.bind(contentView)

    init {
        contentView =
            when (popupMenuResourceType) {
                TOP -> topBinding.root
                BOTTOM -> bottomBinding.root
            }
    }

    internal val backMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.backMenuItem
            BOTTOM -> bottomBinding.backMenuItem
        }
    }

    internal val forwardMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.forwardMenuItem
            BOTTOM -> bottomBinding.forwardMenuItem
        }
    }

    internal val refreshMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.refreshMenuItem
            BOTTOM -> bottomBinding.refreshMenuItem
        }
    }

    internal val printPageMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.printPageMenuItem
            BOTTOM -> bottomBinding.printPageMenuItem
        }
    }

    internal val newTabMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.newTabMenuItem
            BOTTOM -> bottomBinding.newTabMenuItem
        }
    }

    internal val defaultBrowserMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem
            BOTTOM -> bottomBinding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem
        }
    }

    internal val vpnMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.includeVpnMenuItem.vpnMenuItem
            BOTTOM -> bottomBinding.includeVpnMenuItem.vpnMenuItem
        }
    }

    internal val duckChatMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.includeDuckChatMenuItem.duckChatMenuItem
            BOTTOM -> bottomBinding.includeDuckChatMenuItem.duckChatMenuItem
        }
    }

    internal val sharePageMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.sharePageMenuItem
            BOTTOM -> bottomBinding.sharePageMenuItem
        }
    }

    internal val bookmarksMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.bookmarksMenuItem
            BOTTOM -> bottomBinding.bookmarksMenuItem
        }
    }

    internal val downloadsMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.downloadsMenuItem
            BOTTOM -> bottomBinding.downloadsMenuItem
        }
    }

    internal val settingsMenuItem: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.settingsMenuItem
            BOTTOM -> bottomBinding.settingsMenuItem
        }
    }

    internal val addBookmarksMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.addBookmarksMenuItem
            BOTTOM -> bottomBinding.addBookmarksMenuItem
        }
    }

    internal val fireproofWebsiteMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.fireproofWebsiteMenuItem
            BOTTOM -> bottomBinding.fireproofWebsiteMenuItem
        }
    }

    internal val createAliasMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.createAliasMenuItem
            BOTTOM -> bottomBinding.createAliasMenuItem
        }
    }

    internal val changeBrowserModeMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.changeBrowserModeMenuItem
            BOTTOM -> bottomBinding.changeBrowserModeMenuItem
        }
    }

    internal val openInAppMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.openInAppMenuItem
            BOTTOM -> bottomBinding.openInAppMenuItem
        }
    }

    internal val findInPageMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.findInPageMenuItem
            BOTTOM -> bottomBinding.findInPageMenuItem
        }
    }

    internal val addToHomeMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.addToHomeMenuItem
            BOTTOM -> bottomBinding.addToHomeMenuItem
        }
    }

    internal val privacyProtectionMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.privacyProtectionMenuItem
            BOTTOM -> bottomBinding.privacyProtectionMenuItem
        }
    }

    internal val brokenSiteMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.brokenSiteMenuItem
            BOTTOM -> bottomBinding.brokenSiteMenuItem
        }
    }

    internal val autofillMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.autofillMenuItem
            BOTTOM -> bottomBinding.autofillMenuItem
        }
    }

    internal val runningInDdgBrowserMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.runningInDdgBrowserMenuItem
            BOTTOM -> bottomBinding.runningInDdgBrowserMenuItem
        }
    }

    internal val siteOptionsMenuDivider: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.siteOptionsMenuDivider
            BOTTOM -> bottomBinding.siteOptionsMenuDivider
        }
    }

    internal val browserOptionsMenuDivider: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.browserOptionsMenuDivider
            BOTTOM -> bottomBinding.browserOptionsMenuDivider
        }
    }

    internal val settingsMenuDivider: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.settingsMenuDivider
            BOTTOM -> bottomBinding.settingsMenuDivider
        }
    }

    internal val customTabsMenuDivider: View by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.customTabsMenuDivider
            BOTTOM -> bottomBinding.customTabsMenuDivider
        }
    }

    internal val openInDdgBrowserMenuItem: MenuItemView by lazy {
        when (popupMenuResourceType) {
            TOP -> topBinding.openInDdgBrowserMenuItem
            BOTTOM -> bottomBinding.openInDdgBrowserMenuItem
        }
    }

    fun renderState(
        browserShowing: Boolean,
        viewState: BrowserViewState,
        displayedInCustomTabScreen: Boolean,
    ) {
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward
        refreshMenuItem.isEnabled = browserShowing

        printPageMenuItem.isEnabled = browserShowing

        newTabMenuItem.isVisible = !displayedInCustomTabScreen
        duckChatMenuItem.isVisible = viewState.showDuckChatOption && !displayedInCustomTabScreen
        sharePageMenuItem.isVisible = viewState.canSharePage

        defaultBrowserMenuItem.isVisible = viewState.showSelectDefaultBrowserMenuItem

        configureVpnMenuItem(viewState, browserShowing, displayedInCustomTabScreen)

        bookmarksMenuItem.isVisible = !displayedInCustomTabScreen
        downloadsMenuItem.isVisible = !displayedInCustomTabScreen
        settingsMenuItem.isVisible = !displayedInCustomTabScreen

        addBookmarksMenuItem.isVisible = viewState.canSaveSite && !displayedInCustomTabScreen
        val isBookmark = viewState.bookmark != null
        addBookmarksMenuItem.label {
            context.getString(if (isBookmark) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
        }
        addBookmarksMenuItem.setIcon(if (isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

        fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite && !displayedInCustomTabScreen
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

        createAliasMenuItem.isVisible = viewState.isEmailSignedIn && !displayedInCustomTabScreen

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

        openInAppMenuItem.isVisible = viewState.previousAppLink != null
        findInPageMenuItem.isVisible = viewState.canFindInPage
        addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled && !displayedInCustomTabScreen
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
        brokenSiteMenuItem.isVisible = viewState.canReportSite && !displayedInCustomTabScreen

        siteOptionsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
        browserOptionsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
        settingsMenuDivider.isVisible = viewState.browserShowing && !displayedInCustomTabScreen
        printPageMenuItem.isVisible = viewState.canPrintPage && !displayedInCustomTabScreen
        autofillMenuItem.isVisible = viewState.showAutofill && !displayedInCustomTabScreen

        openInDdgBrowserMenuItem.isVisible = displayedInCustomTabScreen
        customTabsMenuDivider.isVisible = displayedInCustomTabScreen
        runningInDdgBrowserMenuItem.isVisible = displayedInCustomTabScreen
        overrideForSSlError(viewState, displayedInCustomTabScreen)
    }

    private fun overrideForSSlError(
        viewState: BrowserViewState,
        displayedInCustomTabScreen: Boolean,
    ) {
        if (viewState.sslError != NONE && !displayedInCustomTabScreen) {
            newTabMenuItem.isVisible = true
            siteOptionsMenuDivider.isVisible = true
        }
    }

    private fun configureVpnMenuItem(
        viewState: BrowserViewState,
        browserShowing: Boolean,
        displayedInCustomTabScreen: Boolean,
    ) {
        // Only show VPN menu item in new tab page overflow menu
        val shouldShowVpnMenuItem = !browserShowing && !displayedInCustomTabScreen

        when (viewState.vpnMenuState) {
            VpnMenuState.Hidden -> {
                vpnMenuItem.isVisible = false
            }
            VpnMenuState.NotSubscribed -> {
                vpnMenuItem.isVisible = shouldShowVpnMenuItem
                if (shouldShowVpnMenuItem) {
                    val (tryForFreePill, statusIndicator, menuItemView) = getVpnMenuViews()
                    configureVpnMenuItemForNotSubscribed(tryForFreePill, statusIndicator, menuItemView)
                }
            }
            VpnMenuState.NotSubscribedNoPill -> {
                vpnMenuItem.isVisible = shouldShowVpnMenuItem
                if (shouldShowVpnMenuItem) {
                    val (tryForFreePill, statusIndicator, menuItemView) = getVpnMenuViews()
                    configureVpnMenuItemForNotSubscribedNoPill(tryForFreePill, statusIndicator, menuItemView)
                }
            }
            is VpnMenuState.Subscribed -> {
                vpnMenuItem.isVisible = shouldShowVpnMenuItem
                if (shouldShowVpnMenuItem) {
                    val (tryForFreePill, statusIndicator, menuItemView) = getVpnMenuViews()
                    configureVpnMenuItemForSubscribed(tryForFreePill, statusIndicator, menuItemView, viewState.vpnMenuState.isVpnEnabled)
                }
            }
        }
    }

    private fun getVpnMenuViews() =
        when (popupMenuResourceType) {
            TOP ->
                Triple(
                    topBinding.includeVpnMenuItem.tryForFreePill,
                    topBinding.includeVpnMenuItem.statusIndicator,
                    topBinding.includeVpnMenuItem.menuItemView,
                )
            BOTTOM ->
                Triple(
                    bottomBinding.includeVpnMenuItem.tryForFreePill,
                    bottomBinding.includeVpnMenuItem.statusIndicator,
                    bottomBinding.includeVpnMenuItem.menuItemView,
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

    enum class ResourceType {
        TOP,
        BOTTOM,
    }
}
