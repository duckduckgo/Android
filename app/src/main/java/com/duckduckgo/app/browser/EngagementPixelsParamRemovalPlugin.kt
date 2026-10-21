/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.browser

import com.duckduckgo.app.pixels.AppPixelName.ADD_BOOKMARK_CONFIRM_EDITED
import com.duckduckgo.app.pixels.AppPixelName.AI_CHAT_DUCK_AI_DIRECT_NAVIGATION_COUNT
import com.duckduckgo.app.pixels.AppPixelName.AI_CHAT_DUCK_AI_DIRECT_NAVIGATION_DAILY
import com.duckduckgo.app.pixels.AppPixelName.BROWSER_MODE_SWITCHED
import com.duckduckgo.app.pixels.AppPixelName.FIRE_TABS_PROMO_TAB_SWITCHER_DISMISSED
import com.duckduckgo.app.pixels.AppPixelName.FIRE_TABS_PROMO_TAB_SWITCHER_SHOWN
import com.duckduckgo.app.pixels.AppPixelName.LONG_PRESS_COPY_LINK_TEXT
import com.duckduckgo.app.pixels.AppPixelName.LONG_PRESS_NEW_FIRE_TAB
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_BACK_BUTTON_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_CLICKED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_CLOSE_TAB_CLICKED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_CLOSE_TAB_SWIPED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_MENU_DOWNLOADS_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_MENU_NEW_TAB_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_MENU_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_MENU_SETTINGS_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_NEW_TAB_CLICKED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_NEW_TAB_LONG_PRESSED
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_SWITCH_TABS
import com.duckduckgo.app.pixels.AppPixelName.TAB_MANAGER_UP_BUTTON_PRESSED
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

@ContributesIntoSet(AppScope::class)
class EngagementPixelsParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {

    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            TAB_MANAGER_CLICKED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_NEW_TAB_CLICKED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_SWITCH_TABS.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_CLOSE_TAB_CLICKED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_CLOSE_TAB_SWIPED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_NEW_TAB_LONG_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_UP_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_BACK_BUTTON_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_MENU_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_MENU_NEW_TAB_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_MENU_CLOSE_ALL_TABS_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_MENU_CLOSE_ALL_TABS_CONFIRMED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_MENU_DOWNLOADS_PRESSED.pixelName to PixelParameter.removeAtb(),
            TAB_MANAGER_MENU_SETTINGS_PRESSED.pixelName to PixelParameter.removeAtb(),
            ADD_BOOKMARK_CONFIRM_EDITED.pixelName to PixelParameter.removeAtb(),
            LONG_PRESS_COPY_LINK_TEXT.pixelName to PixelParameter.removeAtb(),
            LONG_PRESS_NEW_FIRE_TAB.pixelName to PixelParameter.removeAtb(),
            FIRE_TABS_PROMO_TAB_SWITCHER_SHOWN.pixelName to PixelParameter.removeAtb(),
            FIRE_TABS_PROMO_TAB_SWITCHER_DISMISSED.pixelName to PixelParameter.removeAtb(),
            AI_CHAT_DUCK_AI_DIRECT_NAVIGATION_COUNT.pixelName to PixelParameter.removeAtb(),
            AI_CHAT_DUCK_AI_DIRECT_NAVIGATION_DAILY.pixelName to PixelParameter.removeAtb(),
            BROWSER_MODE_SWITCHED.pixelName to PixelParameter.removeAtb(),
        )
    }
}
