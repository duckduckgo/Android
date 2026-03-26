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

package com.duckduckgo.settings.impl.serpsettings.pixel

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.settings.impl.serpsettings.pixel.SerpSettingsPixelName.SERP_SETTINGS_OPEN_DUCK_AI
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

enum class SerpSettingsPixelName(override val pixelName: String) : Pixel.PixelName {
    SERP_SETTINGS_OPEN_DUCK_AI("serp_settings_open_duck-ai"),
}

@ContributesMultibinding(AppScope::class)
class SerpSettingsPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            SERP_SETTINGS_OPEN_DUCK_AI.pixelName to PixelParameter.removeAtb(),
        )
    }
}
