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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.impl.MetricsPixelStore
import com.duckduckgo.feature.toggles.impl.RetentionMetric.APP_USE
import com.duckduckgo.feature.toggles.impl.RetentionMetric.SEARCH
import com.squareup.anvil.annotations.ContributesMultibinding
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@ContributesMultibinding(AppScope::class)
class RetentionMetricsAtbLifecyclePlugin @Inject constructor(
    private val searchMetricPixelsPlugin: SearchMetricPixelsPlugin,
    private val appUseMetricPixelsPlugin: AppUseMetricPixelsPlugin,
    private val store: MetricsPixelStore,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : AtbLifecyclePlugin {

    override fun onSearchRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch {
            searchMetricPixelsPlugin.getMetrics().forEach { metric ->
                metric.getPixelDefinitions().forEach { definition ->
                    store.increaseMetricForPixelDefinition(definition, SEARCH)
                    val searches = store.getMetricForPixelDefinition(definition, SEARCH)
                    if (searches == metric.value.toInt()) {
                        pixel.fire(definition.pixelName, definition.params)
                    }
                }
            }
        }
    }

    override fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch {
            appUseMetricPixelsPlugin.getMetrics().forEach { metric ->
                metric.getPixelDefinitions().forEach { definition ->
                    store.increaseMetricForPixelDefinition(definition, APP_USE)
                    val appUse = store.getMetricForPixelDefinition(definition, APP_USE)
                    if (appUse == metric.value.toInt()) {
                        pixel.fire(definition.pixelName, definition.params)
                    }
                }
            }
        }
    }
}
