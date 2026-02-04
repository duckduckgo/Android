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
        )
    }
}
