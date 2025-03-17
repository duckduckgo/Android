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

package com.duckduckgo.duckchat.impl

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DUCK_CHAT_MENU_SETTING_OFF
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DUCK_CHAT_MENU_SETTING_ON
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DUCK_CHAT_OPEN
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DUCK_CHAT_OPEN_BROWSER_MENU
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DUCK_CHAT_OPEN_NEW_TAB_MENU
import com.duckduckgo.duckchat.impl.DuckChatPixelName.DUCK_CHAT_SETTINGS_PRESSED
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

enum class DuckChatPixelName(override val pixelName: String) : Pixel.PixelName {
    DUCK_CHAT_OPEN("aichat_open"),
    DUCK_CHAT_OPEN_BROWSER_MENU("aichat_open_browser_menu"),
    DUCK_CHAT_OPEN_NEW_TAB_MENU("aichat_open_new_tab_menu"),
    DUCK_CHAT_MENU_SETTING_OFF("aichat_menu_setting_off"),
    DUCK_CHAT_MENU_SETTING_ON("aichat_menu_setting_on"),
    DUCK_CHAT_SETTINGS_PRESSED("settings_aichat_pressed"),
    DEDICATED_WEBVIEW_NEW_TAB_REQUESTED("m_dedicated_webview_new_tab_requested"),
    DEDICATED_WEBVIEW_URL_EXTRACTION_FAILED("m_dedicated_webview_url_extraction_failed"),
}

@ContributesMultibinding(AppScope::class)
class DuckChatParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            DUCK_CHAT_OPEN.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_MENU_SETTING_OFF.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_MENU_SETTING_ON.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_BROWSER_MENU.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_OPEN_NEW_TAB_MENU.pixelName to PixelParameter.removeAtb(),
            DUCK_CHAT_SETTINGS_PRESSED.pixelName to PixelParameter.removeAtb(),
        )
    }
}
