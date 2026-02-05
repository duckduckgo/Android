/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class BrowserMenuParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            AppPixelName.EXPERIMENTAL_MENU_USED_DAILY.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_USED_UNIQUE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_USED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ENABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ENABLED_UNIQUE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ENABLED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISABLED_DAILY.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISABLED_UNIQUE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISABLED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISPLAYED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISPLAYED_NTP.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISPLAYED_AICHAT.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISPLAYED_CUSTOMTABS.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISPLAYED_ERROR.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_NAVIGATE_BACK_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_NAVIGATE_FORWARD_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_REFRESH_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_CUSTOM_TABS_MENU_REFRESH.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_CUSTOM_TABS_OPEN_IN_DDG.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_ADD.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_CUSTOM_TABS_MENU_DISABLE_PROTECTIONS_ALLOW_LIST_REMOVE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_NEW_TAB_PRESSED_FROM_SERP.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_NEW_TAB_PRESSED_FROM_SITE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DUCK_CHAT_OPEN_BROWSER_MENU.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DUCK_CHAT_SETTINGS_NEW_CHAT_TAB_TAPPED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DUCK_CHAT_OMNIBAR_NEW_CHAT_TAPPED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DUCK_CHAT_SETTINGS_SIDEBAR_TAPPED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_DUCK_CHAT_DUCK_AI_SETTINGS_TAPPED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_ADD_BOOKMARK_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_EDIT_BOOKMARK_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_SHARE_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_FIND_IN_PAGE_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_PRINT_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_ADD_TO_HOME_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_DESKTOP_SITE_ENABLE_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_DESKTOP_SITE_DISABLE_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_REPORT_BROKEN_SITE_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_SETTINGS_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_APP_LINKS_OPEN_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_BOOKMARKS_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_DOWNLOADS_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ACTION_AUTOFILL_PRESSED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_FIREPROOF_WEBSITE_ADDED.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_FIREPROOF_WEBSITE_REMOVE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ALLOWLIST_ADD.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_ALLOWLIST_REMOVE.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_SET_AS_DEFAULT_IN_MENU_CLICK.pixelName to PixelParameter.removeAtb(),
            AppPixelName.EXPERIMENTAL_MENU_EMAIL_COPIED_TO_CLIPBOARD.pixelName to PixelParameter.removeAtb(),
        )
    }
}
