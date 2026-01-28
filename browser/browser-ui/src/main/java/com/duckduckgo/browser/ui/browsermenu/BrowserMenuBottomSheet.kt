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

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import com.duckduckgo.browser.ui.R
import com.duckduckgo.browser.ui.databinding.BottomSheetBrowserMenuBinding
import com.duckduckgo.common.ui.setRoundCorners
import com.duckduckgo.common.ui.view.MenuActionButtonView
import com.duckduckgo.common.ui.view.MenuItemView
import com.duckduckgo.common.ui.view.MenuItemViewSize
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.mobile.android.R.drawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class BrowserMenuBottomSheet(
    private val context: Context,
    private val onDismissListener: () -> Unit,
    private val onMenuItemClickListener: () -> Unit,
) : BottomSheetDialog(context) {
    private val binding = BottomSheetBrowserMenuBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)

        // Set VPN menu item size to medium like other menu items
        binding.includeVpnMenuItem.vpnMenuItem
            .findViewById<MenuItemView>(R.id.menuItemView)
            .setSize(MenuItemViewSize.MEDIUM)

        setOnShowListener { dialogInterface ->
            (dialogInterface as BottomSheetDialog).setRoundCorners()

            behavior.apply {
                isDraggable = true
                isHideable = true
                peekHeight = context.resources.displayMetrics.heightPixels / 2
                state = BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        setOnCancelListener {
            onDismissListener()
            dismiss()
        }
    }

    private val menuItemsContainer: LinearLayout
        get() = binding.menuItemsContainer

    private val menuActionItemsContainer: LinearLayout
        get() = binding.menuActionItemsContainer

    val backMenuItem: MenuActionButtonView
        get() = binding.backMenuItem

    val forwardMenuItem: MenuActionButtonView
        get() = binding.forwardMenuItem

    val newTabMenuItem: MenuActionButtonView
        get() = binding.newTabMenuItem

    val newDuckChatTabMenuItem: MenuActionButtonView
        get() = binding.newDuckChatTabMenuItem

    val newDuckChatMenuItem: MenuActionButtonView
        get() = binding.newDuckChatMenuItem

    val settingsMenuItem: MenuActionButtonView
        get() = binding.settingsMenuItem

    val defaultBrowserMenuItem: View
        get() = binding.includeDefaultBrowserMenuItem.defaultBrowserMenuItem

    val printPageMenuItem: MenuItemView
        get() = binding.printPageMenuItem

    val vpnMenuItem: View
        get() = binding.includeVpnMenuItem.vpnMenuItem

    val bookmarksMenuItem: MenuItemView
        get() = binding.bookmarksMenuItem

    val addBookmarksMenuItem: MenuItemView
        get() = binding.addBookmarksMenuItem

    val fireproofWebsiteMenuItem: MenuItemView
        get() = binding.fireproofWebsiteMenuItem

    val findInPageMenuItem: MenuItemView
        get() = binding.findInPageMenuItem

    val autofillMenuItem: MenuItemView
        get() = binding.autofillMenuItem

    val createAliasMenuItem: MenuItemView
        get() = binding.createAliasMenuItem

    val downloadsMenuItem: MenuItemView
        get() = binding.downloadsMenuItem

    val duckChatHistoryMenuItem: MenuItemView
        get() = binding.duckChatHistoryMenuItem

    val duckChatSettingsMenuItem: MenuItemView
        get() = binding.chatSettings

    val sharePageMenuItem: MenuItemView
        get() = binding.sharePageMenuItem

    val addToHomeMenuItem: MenuItemView
        get() = binding.addToHomeMenuItem

    val privacyProtectionMenuItem: MenuItemView
        get() = binding.privacyProtectionMenuItem

    val changeBrowserModeMenuItem: MenuItemView
        get() = binding.changeBrowsingModeMenuItem

    val refreshMenuItem: MenuItemView
        get() = binding.refreshMenuItem

    val openInAppMenuItem: MenuItemView
        get() = binding.openInAppMenuItem

    val openInDdgBrowserMenuItem: MenuItemView
        get() = binding.openInDdgBrowserMenuItem

    val runningInDdgBrowserMenuItem: MenuItemView
        get() = binding.runningInDdgBrowserMenuItem

    val brokenSiteMenuItem: MenuItemView
        get() = binding.reportBrokenSiteMenuItem

    fun render(viewState: BrowserMenuViewState) {
        hideAllMenuItems()
        showCommonItems()
        when (viewState) {
            is BrowserMenuViewState.Browser -> renderBrowserMenu(viewState)
            is BrowserMenuViewState.NewTabPage -> renderNewTabPageMenu(viewState)
            is BrowserMenuViewState.CustomTabs -> renderCustomTabsMenu(viewState)
            is BrowserMenuViewState.DuckAi -> renderDuckAiMenu(viewState)
        }
    }

    fun onMenuItemClicked(view: View, onClick: () -> Unit) {
        view.setOnClickListener {
            onMenuItemClickListener()
            onClick()
            dismiss()
        }
    }

    private fun hideAllMenuItems() {
        menuItemsContainer.children.forEach { menuItem ->
            menuItem.gone()
        }
    }

    private fun showCommonItems() {
        menuActionItemsContainer.isVisible = true
        bookmarksMenuItem.isVisible = true
        downloadsMenuItem.isVisible = true
    }

    private fun renderBrowserMenu(viewState: BrowserMenuViewState.Browser) {
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward
        newDuckChatTabMenuItem.isEnabled = viewState.showNewDuckChatTabOption
        newDuckChatTabMenuItem.isVisible = viewState.showNewDuckChatTabOption
        newDuckChatMenuItem.isEnabled = viewState.showDuckChatOption
        newDuckChatMenuItem.isVisible = viewState.showDuckChatOption
        newTabMenuItem.isEnabled = true
        settingsMenuItem.isEnabled = true

        refreshMenuItem.isVisible = true
        defaultBrowserMenuItem.isVisible = viewState.showSelectDefaultBrowserMenuItem
        printPageMenuItem.isVisible = viewState.canPrintPage
        sharePageMenuItem.isVisible = viewState.canSharePage
        openInAppMenuItem.isVisible = viewState.hasPreviousAppLink
        openInDdgBrowserMenuItem.isVisible = false
        runningInDdgBrowserMenuItem.isVisible = false

        addBookmarksMenuItem.isVisible = viewState.canSaveSite
        val bookmarkLabel = context.getString(if (viewState.isBookmark) R.string.browserMenuEditBookmark else R.string.browserMenuAddBookmark)
        addBookmarksMenuItem.label(bookmarkLabel)
        addBookmarksMenuItem.setIcon(if (viewState.isBookmark) drawable.ic_bookmark_solid_24 else drawable.ic_bookmark_24)

        fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite
        val fireproofLabel = context.getString(
            if (viewState.isFireproofWebsite) {
                R.string.browserMenuRemoveFireproofing
            } else {
                R.string.browserMenuFireproofSite
            },
        )
        fireproofWebsiteMenuItem.label(fireproofLabel)
        fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_24 else drawable.ic_fireproof_solid_24)
        duckChatHistoryMenuItem.isVisible = false

        createAliasMenuItem.isVisible = viewState.isEmailSignedIn

        changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode
        val changeBrowserLabel = context.getString(
            if (viewState.isDesktopBrowsingMode) {
                R.string.browserMenuMobileSite
            } else {
                R.string.browserMenuDesktopSite
            },
        )
        changeBrowserModeMenuItem.label(changeBrowserLabel)
        changeBrowserModeMenuItem.setIcon(
            if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_24 else drawable.ic_device_desktop_24,
        )

        findInPageMenuItem.isVisible = viewState.canFindInPage
        addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled
        privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
        val privacyProtectionLabel = context.getText(
            if (viewState.isPrivacyProtectionDisabled) {
                R.string.browserMenuEnablePrivacyProtection
            } else {
                R.string.browserMenuDisablePrivacyProtection
            },
        ).toString()
        privacyProtectionMenuItem.label(privacyProtectionLabel)
        privacyProtectionMenuItem.setIcon(
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_24 else drawable.ic_shield_disabled_24,
        )
        brokenSiteMenuItem.isVisible = viewState.canReportSite

        autofillMenuItem.isVisible = viewState.showAutofill

        vpnMenuItem.isVisible = false

        binding.urlPageActionsSectionDivider.isVisible = true
        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = viewState.canFireproofSite || viewState.isEmailSignedIn
        binding.utilitiesSectionDivider.isVisible = true
        binding.customTabsMenuDivider.isVisible = false
    }

    private fun renderNewTabPageMenu(viewState: BrowserMenuViewState.NewTabPage) {
        backMenuItem.isEnabled = false
        forwardMenuItem.isEnabled = viewState.canGoForward
        newTabMenuItem.isEnabled = true
        newDuckChatTabMenuItem.isEnabled = false
        newDuckChatTabMenuItem.isVisible = false
        newDuckChatMenuItem.isEnabled = viewState.showDuckChatOption
        newDuckChatMenuItem.isVisible = viewState.showDuckChatOption
        settingsMenuItem.isEnabled = true

        refreshMenuItem.isVisible = false
        autofillMenuItem.isVisible = viewState.showAutofill
        downloadsMenuItem.isVisible = true
        duckChatHistoryMenuItem.isVisible = false
        renderVpnMenu(viewState.vpnMenuState)

        binding.urlPageActionsSectionDivider.isVisible = false
        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = viewState.vpnMenuState != VpnMenuState.Hidden
        binding.utilitiesSectionDivider.isVisible = false
        binding.customTabsMenuDivider.isVisible = false
    }

    private fun renderCustomTabsMenu(viewState: BrowserMenuViewState.CustomTabs) {
        backMenuItem.isEnabled = viewState.canGoBack
        forwardMenuItem.isEnabled = viewState.canGoForward
        newTabMenuItem.isEnabled = false
        newDuckChatTabMenuItem.isEnabled = false
        newDuckChatTabMenuItem.isVisible = false
        newDuckChatMenuItem.isEnabled = false
        newDuckChatMenuItem.isVisible = true
        settingsMenuItem.isEnabled = false

        refreshMenuItem.isVisible = true
        printPageMenuItem.isVisible = true
        sharePageMenuItem.isVisible = viewState.canSharePage
        findInPageMenuItem.isVisible = viewState.canFindInPage
        openInDdgBrowserMenuItem.isVisible = true
        runningInDdgBrowserMenuItem.isVisible = true

        val changeBrowserLabel = context.getString(
            if (viewState.isDesktopBrowsingMode) {
                R.string.browserMenuMobileSite
            } else {
                R.string.browserMenuDesktopSite
            },
        )
        changeBrowserModeMenuItem.label(changeBrowserLabel)
        changeBrowserModeMenuItem.isVisible = viewState.canChangeBrowsingMode

        bookmarksMenuItem.isVisible = false
        downloadsMenuItem.isVisible = false

        privacyProtectionMenuItem.isVisible = viewState.canChangePrivacyProtection
        val privacyProtectionLabel = context.getText(
            if (viewState.isPrivacyProtectionDisabled) {
                R.string.browserMenuEnablePrivacyProtection
            } else {
                R.string.browserMenuDisablePrivacyProtection
            },
        ).toString()
        privacyProtectionMenuItem.label(privacyProtectionLabel)
        privacyProtectionMenuItem.setIcon(
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_24 else drawable.ic_shield_disabled_24,
        )

        binding.urlPageActionsSectionDivider.isVisible = true
        binding.librarySectionDivider.isVisible = false
        binding.privacyToolsSectionDivider.isVisible = false
        binding.utilitiesSectionDivider.isVisible = true
        binding.customTabsMenuDivider.isVisible = true
    }

    private fun renderDuckAiMenu(viewState: BrowserMenuViewState.DuckAi) {
        forwardMenuItem.isEnabled = false
        newTabMenuItem.isEnabled = true
        newDuckChatTabMenuItem.isEnabled = true
        newDuckChatTabMenuItem.isVisible = true
        newDuckChatMenuItem.isEnabled = false
        newDuckChatMenuItem.isVisible = false
        settingsMenuItem.isEnabled = true

        refreshMenuItem.isVisible = true
        brokenSiteMenuItem.isVisible = viewState.canReportSite
        printPageMenuItem.isVisible = viewState.canPrintPage
        autofillMenuItem.isVisible = viewState.showAutofill

        duckChatHistoryMenuItem.isVisible = true
        duckChatSettingsMenuItem.isVisible = true

        binding.urlPageActionsSectionDivider.isVisible = true
        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = false
        binding.utilitiesSectionDivider.isVisible = true
        binding.customTabsMenuDivider.isVisible = false
    }

    private fun renderVpnMenu(viewState: VpnMenuState) {
        when (viewState) {
            VpnMenuState.Hidden -> {
                vpnMenuItem.isVisible = false
            }
            VpnMenuState.NotSubscribed -> {
                vpnMenuItem.isVisible = true
                configureVpnMenuItemForNotSubscribed(
                    binding.includeVpnMenuItem.tryForFreePill,
                    binding.includeVpnMenuItem.statusIndicator,
                    binding.includeVpnMenuItem.menuItemView,
                )
            }
            VpnMenuState.NotSubscribedNoPill -> {
                vpnMenuItem.isVisible = true
                configureVpnMenuItemForNotSubscribedNoPill(
                    binding.includeVpnMenuItem.tryForFreePill,
                    binding.includeVpnMenuItem.statusIndicator,
                    binding.includeVpnMenuItem.menuItemView,
                )
            }
            is VpnMenuState.Subscribed -> {
                vpnMenuItem.isVisible = true
                configureVpnMenuItemForSubscribed(
                    binding.includeVpnMenuItem.tryForFreePill,
                    binding.includeVpnMenuItem.statusIndicator,
                    binding.includeVpnMenuItem.menuItemView,
                    viewState.isVpnEnabled,
                )
            }
        }
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
