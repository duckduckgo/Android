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

package com.duckduckgo.app.pixels.surface

import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.autofill.impl.pixel.AutofillPixelNames
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.newtabpage.impl.pixels.NewTabPixelNames
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = SurfacePixelPlugin::class,
)
class MobileSurfacePixelPlugin @Inject constructor() : SurfacePixelPlugin {

    override fun names(): List<String> = listOf(
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_SERP_LOADED.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_SERP_LOADED_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_WEBSITE_LOADED.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_WEBSITE_LOADED_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_LANDSCAPE_ORIENTATION_USED.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_LANDSCAPE_ORIENTATION_USED_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_TAB_MANAGER_CLICKED.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_TAB_MANAGER_CLICKED_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_DATA_CLEARING_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_MENU_OPENED.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_MENU_OPENED_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_SETTINGS_OPENED.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_SETTINGS_OPENED_DAILY.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_DAU.pixelName,
        AppPixelName.PRODUCT_TELEMETRY_SURFACE_DAU_DAILY.pixelName,
        AutofillPixelNames.PRODUCT_TELEMETRY_SURFACE_PASSWORDS_OPENED.pixelName,
        AutofillPixelNames.PRODUCT_TELEMETRY_SURFACE_PASSWORDS_OPENED_DAILY.pixelName,
        DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED.pixelName,
        DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_AUTOCOMPLETE_DISPLAYED_DAILY.pixelName,
        DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN.pixelName,
        DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_DUCK_AI_OPEN_DAILY.pixelName,
        DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE.pixelName,
        DuckChatPixelName.PRODUCT_TELEMETRY_SURFACE_KEYBOARD_USAGE_DAILY.pixelName,
        NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED.pixelName,
        NewTabPixelNames.PRODUCT_SURFACE_TELEMETRY_NEW_TAB_DISPLAYED_DAILY.pixelName,
        SavedSitesPixelName.PRODUCT_TELEMETRY_SURFACE_BOOKMARKS_OPENED.pixelName,
        SavedSitesPixelName.PRODUCT_TELEMETRY_SURFACE_BOOKMARKS_OPENED_DAILY.pixelName,
    )
}
