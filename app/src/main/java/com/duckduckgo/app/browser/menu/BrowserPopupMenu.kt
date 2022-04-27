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
import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.ViewGroup.LayoutParams
import androidx.core.view.isVisible
import com.duckduckgo.app.browser.BrowserTabViewModel.BrowserViewState
import com.duckduckgo.app.browser.R
import com.duckduckgo.mobile.android.R.dimen
import com.duckduckgo.mobile.android.R.drawable
import com.duckduckgo.mobile.android.ui.menu.PopupMenu
import kotlinx.android.synthetic.main.popup_window_browser_menu.view.*

class BrowserPopupMenu(
    context: Context,
    layoutInflater: LayoutInflater
) : PopupMenu(
    layoutInflater,
    resourceId = R.layout.popup_window_browser_menu,
    width = getPopupMenuWidth(context)
) {

    fun renderState(
        browserShowing: Boolean,
        viewState: BrowserViewState
    ) {
        contentView.apply {
            backMenuItem.isEnabled = viewState.canGoBack
            forwardMenuItem.isEnabled = viewState.canGoForward
            refreshMenuItem.isEnabled = browserShowing

            newTabMenuItem.isVisible = browserShowing
            sharePageMenuItem?.isVisible = viewState.canSharePage
            addBookmarksMenuItem?.isVisible = viewState.canSaveSite
            val isBookmark = viewState.bookmark != null
            addBookmarksMenuItem?.label {
                context.getString(if (isBookmark) R.string.editBookmarkMenuTitle else R.string.addBookmarkMenuTitle)
            }
            addBookmarksMenuItem?.setIcon(if (isBookmark) drawable.ic_bookmark_solid_16 else drawable.ic_bookmark_16)

            val isFavorite = viewState.favorite != null
            addFavoriteMenuItem?.isVisible = viewState.addFavorite.isEnabled()
            addFavoriteMenuItem.label {
                when {
                    viewState.addFavorite.isHighlighted() -> context.getString(R.string.addFavoriteMenuTitleHighlighted)
                    isFavorite -> context.getString(R.string.removeFavoriteMenuTitle)
                    else -> context.getString(R.string.addFavoriteMenuTitle)
                }
            }
            addFavoriteMenuItem.setIcon(if (isFavorite) drawable.ic_favorite_solid_16 else drawable.ic_favorite_16)

            fireproofWebsiteMenuItem?.isVisible = viewState.canFireproofSite
            fireproofWebsiteMenuItem?.label {
                context.getString(
                    if (viewState.isFireproofWebsite) {
                        R.string.fireproofWebsiteMenuTitleRemove
                    } else {
                        R.string.fireproofWebsiteMenuTitleAdd
                    }
                )
            }
            fireproofWebsiteMenuItem?.setIcon(if (viewState.isFireproofWebsite) drawable.ic_fire_16 else drawable.ic_fireproofed_16)

            createAliasMenuItem?.isVisible = viewState.isEmailSignedIn

            changeBrowserModeMenuItem?.isVisible = viewState.canChangeBrowsingMode
            changeBrowserModeMenuItem.label {
                context.getString(
                    if (viewState.isDesktopBrowsingMode) {
                        R.string.requestMobileSiteMenuTitle
                    } else {
                        R.string.requestDesktopSiteMenuTitle
                    }
                )
            }
            changeBrowserModeMenuItem?.setIcon(
                if (viewState.isDesktopBrowsingMode) drawable.ic_device_mobile_16 else drawable.ic_device_desktop_16
            )

            openInAppMenuItem.isVisible = viewState.previousAppLink != null
            findInPageMenuItem.isVisible = viewState.canFindInPage
            addToHomeMenuItem.isVisible = viewState.addToHomeVisible && viewState.addToHomeEnabled
            privacyProtectionMenuItem?.isVisible = viewState.canChangePrivacyProtection
            privacyProtectionMenuItem?.label {
                context.getText(
                    if (viewState.isPrivacyProtectionEnabled) {
                        R.string.enablePrivacyProtection
                    } else {
                        R.string.disablePrivacyProtection
                    }
                ).toString()
            }
            privacyProtectionMenuItem?.setIcon(
                if (viewState.isPrivacyProtectionEnabled) drawable.ic_protections_16 else drawable.ic_protections_blocked_16
            )
            brokenSiteMenuItem?.isVisible = viewState.canReportSite

            siteOptionsMenuDivider.isVisible = viewState.browserShowing
            browserOptionsMenuDivider.isVisible = viewState.browserShowing
            settingsMenuDivider.isVisible = viewState.browserShowing
        }
    }
}

private fun getPopupMenuWidth(context: Context): Int {
    val orientation = context.resources.configuration.orientation
    return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
        LayoutParams.WRAP_CONTENT
    } else {
        context.resources.getDimensionPixelSize(dimen.popupMenuWidth)
    }
}
