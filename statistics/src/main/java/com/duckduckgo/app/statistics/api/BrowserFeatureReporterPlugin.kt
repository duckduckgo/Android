/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.statistics.api

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.app.statistics.pixels.Pixel.StatisticsPixelName
import com.duckduckgo.di.scopes.AppScope

@ContributesPluginPoint(AppScope::class)
interface BrowserFeatureReporterPlugin {

    /**
     * Used by the [StatisticsPixelName.BROWSER_DAILY_ACTIVE_FEATURE] pixel,
     * to notify the value of a feature
     * The first parameter is the feature value, the second parameter is the feature name
     */
    fun feature(): Pair<String, String>
}
