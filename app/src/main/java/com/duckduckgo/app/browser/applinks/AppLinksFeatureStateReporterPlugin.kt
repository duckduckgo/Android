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

package com.duckduckgo.app.browser.applinks

import com.duckduckgo.app.settings.clear.AppLinkSettingType
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = BrowserFeatureStateReporterPlugin::class,
)
class AppLinksFeatureStateReporterPlugin @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : BrowserFeatureStateReporterPlugin {

    override fun featureStateParams(): Map<String, String> {
        val setting = AppLinkSettingType.getForState(
            appLinksEnabled = settingsDataStore.appLinksEnabled,
            showAppLinksPrompt = settingsDataStore.showAppLinksPrompt,
        )
        return mapOf(APP_LINKS_PARAM to setting.getPixelValue())
    }

    companion object {
        const val APP_LINKS_PARAM = "app_links"
    }
}
