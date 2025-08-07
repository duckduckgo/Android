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

package com.duckduckgo.feature.toggles.impl.metrics

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.FeatureTogglesInventory
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject

@ContributesMultibinding(AppScope::class)
class AppUseMetricPixelsPlugin @Inject constructor(private val inventory: FeatureTogglesInventory) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return inventory.getAllActiveExperimentToggles().flatMap { toggle ->
            listOf(
                MetricsPixel(
                    metric = "app_use",
                    value = "1",
                    toggle = toggle,
                    conversionWindow = (1..14).map { ConversionWindow(lowerWindow = it, upperWindow = it) } +
                        listOf(
                            ConversionWindow(lowerWindow = 5, upperWindow = 7),
                            ConversionWindow(lowerWindow = 1, upperWindow = 4),
                            ConversionWindow(lowerWindow = 8, upperWindow = 14),
                        ),
                ),
                MetricsPixel(
                    metric = "app_use",
                    value = "4",
                    toggle = toggle,
                    conversionWindow = listOf(
                        ConversionWindow(lowerWindow = 5, upperWindow = 7),
                        ConversionWindow(lowerWindow = 8, upperWindow = 15),
                    ),
                ),
                MetricsPixel(
                    metric = "app_use",
                    value = "6",
                    toggle = toggle,
                    conversionWindow = listOf(
                        ConversionWindow(lowerWindow = 5, upperWindow = 7),
                        ConversionWindow(lowerWindow = 8, upperWindow = 15),
                    ),
                ),
                MetricsPixel(
                    metric = "app_use",
                    value = "11",
                    toggle = toggle,
                    conversionWindow = listOf(
                        ConversionWindow(lowerWindow = 5, upperWindow = 7),
                        ConversionWindow(lowerWindow = 8, upperWindow = 15),
                    ),
                ),
                MetricsPixel(
                    metric = "app_use",
                    value = "21",
                    toggle = toggle,
                    conversionWindow = listOf(
                        ConversionWindow(lowerWindow = 5, upperWindow = 7),
                        ConversionWindow(lowerWindow = 8, upperWindow = 15),
                    ),
                ),
                MetricsPixel(
                    metric = "app_use",
                    value = "30",
                    toggle = toggle,
                    conversionWindow = listOf(
                        ConversionWindow(lowerWindow = 5, upperWindow = 7),
                        ConversionWindow(lowerWindow = 8, upperWindow = 15),
                    ),
                ),
            )
        }
    }
}
