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
import com.duckduckgo.common.ui.view.StatusIndicatorView
import com.duckduckgo.common.ui.view.gone
import com.duckduckgo.mobile.android.R.drawable
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

@SuppressLint("NoBottomSheetDialog")
class BrowserMenuBottomSheet(
    private val context: Context,
    private val expandedByDefault: Boolean = false,
) : BottomSheetDialog(context) {
    private val binding = BottomSheetBrowserMenuBinding.inflate(LayoutInflater.from(context))

    init {
        setContentView(binding.root)
        behavior.apply {
            isDraggable = true
            isHideable = true
            state = if (expandedByDefault) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_COLLAPSED
            }
        }

        setOnShowListener { dialogInterface ->
            (dialogInterface as BottomSheetDialog).setRoundCorners()
        }

        setOnCancelListener {
            dismiss()
        }
    }

    private val menuItemsContainer: LinearLayout
        get() = binding.menuItemsContainer

    private val menuActionItemsContainer: LinearLayout
        get() = binding.menuActionItemsContainer

    val forwardMenuItem: MenuActionButtonView
        get() = binding.forwardMenuItem

    val refreshMenuItem: MenuActionButtonView
        get() = binding.refreshMenuItem

    val newTabMenuItem: MenuActionButtonView
        get() = binding.newTabMenuItem

    val newDuckChatTabMenuItem: MenuActionButtonView
        get() = binding.newDuckChatTabMenuItem

    val settingsMenuItem: MenuActionButtonView
        get() = binding.settingsMenuItem

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

    val sharePageMenuItem: MenuItemView
        get() = binding.sharePageMenuItem

    val addToHomeMenuItem: MenuItemView
        get() = binding.addToHomeMenuItem

    val privacyProtectionMenuItem: MenuItemView
        get() = binding.privacyProtectionMenuItem

    val changeBrowserModeMenuItem: MenuItemView
        get() = binding.changeBrowsingModeMenuItem

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
        forwardMenuItem.isEnabled = viewState.canGoForward
        refreshMenuItem.isEnabled = true
        newTabMenuItem.isEnabled = true
        newDuckChatTabMenuItem.isEnabled = true
        settingsMenuItem.isEnabled = true

        printPageMenuItem.isVisible = viewState.canPrintPage
        sharePageMenuItem.isVisible = viewState.canSharePage

        addBookmarksMenuItem.isVisible = viewState.canSaveSite
        val bookmarkLabel = context.getString(if (viewState.isBookmark) R.string.browserMenuEditBookmark else R.string.browserMenuAddBookmark)
        addBookmarksMenuItem.label(bookmarkLabel)
        addBookmarksMenuItem.setIcon(if (viewState.isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

        fireproofWebsiteMenuItem.isVisible = viewState.canFireproofSite
        val fireproofLabel = context.getString(
            if (viewState.isFireproofWebsite) {
                R.string.browserMenuRemoveFireproofing
            } else {
                R.string.browserMenuFireproofSite
            },
        )
        fireproofWebsiteMenuItem.label(fireproofLabel)
        fireproofWebsiteMenuItem.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproof_solid_16)
        duckChatHistoryMenuItem.isVisible = true

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
            if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_16 else drawable.ic_device_desktop_16,
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
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_16 else drawable.ic_shield_disabled_16,
        )
        brokenSiteMenuItem.isVisible = viewState.canReportSite

        autofillMenuItem.isVisible = viewState.showAutofill

        vpnMenuItem.isVisible = false

        binding.urlPageActionsSectionDivider.isVisible = true
        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = true
        binding.utilitiesSectionDivider.isVisible = true
    }

    private fun renderNewTabPageMenu(viewState: BrowserMenuViewState.NewTabPage) {
        forwardMenuItem.isEnabled = viewState.canGoForward
        refreshMenuItem.isEnabled = false
        newTabMenuItem.isEnabled = false
        newDuckChatTabMenuItem.isEnabled = true
        settingsMenuItem.isEnabled = true

        autofillMenuItem.isVisible = viewState.showAutofill
        downloadsMenuItem.isVisible = true
        duckChatHistoryMenuItem.isVisible = true
        renderVpnMenu(viewState.vpnMenuState)

        binding.urlPageActionsSectionDivider.isVisible = false
        binding.librarySectionDivider.isVisible = true
        binding.privacyToolsSectionDivider.isVisible = false
        binding.utilitiesSectionDivider.isVisible = false
    }

    private fun renderCustomTabsMenu(viewState: BrowserMenuViewState.CustomTabs) {
        forwardMenuItem.isEnabled = viewState.canGoForward
        refreshMenuItem.isEnabled = true
        newTabMenuItem.isEnabled = false
        newDuckChatTabMenuItem.isEnabled = false
        settingsMenuItem.isEnabled = false

        printPageMenuItem.isVisible = true
        sharePageMenuItem.isVisible = viewState.canSharePage
        findInPageMenuItem.isVisible = viewState.canFindInPage

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
            if (viewState.isPrivacyProtectionDisabled) drawable.ic_shield_16 else drawable.ic_shield_disabled_16,
        )

        binding.urlPageActionsSectionDivider.isVisible = true
        binding.librarySectionDivider.isVisible = false
        binding.privacyToolsSectionDivider.isVisible = false
        binding.utilitiesSectionDivider.isVisible = true
    }

    private fun renderDuckAiMenu(viewState: BrowserMenuViewState.DuckAi) {
        forwardMenuItem.isEnabled = false
        refreshMenuItem.isEnabled = false
        newTabMenuItem.isEnabled = false
        newDuckChatTabMenuItem.isEnabled = true
        settingsMenuItem.isEnabled = true

        brokenSiteMenuItem.isVisible = viewState.canReportSite
        printPageMenuItem.isVisible = viewState.canPrintPage
        autofillMenuItem.isVisible = viewState.showAutofill

        duckChatHistoryMenuItem.isVisible = true

        binding.urlPageActionsSectionDivider.isVisible = true
        binding.librarySectionDivider.isVisible = false
        binding.privacyToolsSectionDivider.isVisible = false
        binding.utilitiesSectionDivider.isVisible = true
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
