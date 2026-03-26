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

package com.duckduckgo.app.statistics.pixels

import com.duckduckgo.app.statistics.pixels.Pixel.PixelName
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

enum class StatisticsPixelName(override val pixelName: String) : PixelName {
    BROWSER_DAILY_ACTIVE_FEATURE_STATE("m_browser_feature_daily_active_user_d"),
    RETENTION_SEGMENTS("m_retention_segments"),

    ATB_PRE_INITIALIZER_PLUGIN_TIMEOUT("timeout_atb_pre_initializer_plugin"),
}

@ContributesMultibinding(AppScope::class)
class AtbInitializerPluginPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> {
        return listOf(
            StatisticsPixelName.ATB_PRE_INITIALIZER_PLUGIN_TIMEOUT.pixelName to PixelParameter.removeAtb(),
        )
    }
}
