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

package com.duckduckgo.app.browser.omnibar

import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.api.BrowserFeatureStateReporterPlugin
import com.duckduckgo.app.statistics.pixels.Pixel.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface OmnibarPositionReporterPlugin

@ContributesMultibinding(scope = AppScope::class, boundType = BrowserFeatureStateReporterPlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = OmnibarPositionReporterPlugin::class)
@SingleInstanceIn(AppScope::class)
class OmnibarPositionDetector @Inject constructor(
    private val settingsDataStore: SettingsDataStore,
) : OmnibarPositionReporterPlugin, BrowserFeatureStateReporterPlugin {
    override fun featureStateParams(): Map<String, String> {
        return mapOf(PixelParameter.ADDRESS_BAR to settingsDataStore.omnibarPosition.name)
    }
}
