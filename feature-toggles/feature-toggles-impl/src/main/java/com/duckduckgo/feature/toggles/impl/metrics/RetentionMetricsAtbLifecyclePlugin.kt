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
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.duckduckgo.feature.toggles.impl.MetricsPixelStore
import com.duckduckgo.feature.toggles.impl.RetentionMetric.APP_USE
import com.duckduckgo.feature.toggles.impl.RetentionMetric.SEARCH
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

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
                    if (isInConversionWindow(definition)) {
                        store.getMetricForPixelDefinition(definition, SEARCH).takeIf { it < metric.value.toInt() }?.let {
                            store.increaseMetricForPixelDefinition(definition, SEARCH).takeIf { it == metric.value.toInt() }?.apply {
                                pixel.fire(definition.pixelName, definition.params)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onAppRetentionAtbRefreshed(oldAtb: String, newAtb: String) {
        appCoroutineScope.launch {
            appUseMetricPixelsPlugin.getMetrics().forEach { metric ->
                metric.getPixelDefinitions().forEach { definition ->
                    if (isInConversionWindow(definition)) {
                        store.getMetricForPixelDefinition(definition, APP_USE).takeIf { it < metric.value.toInt() }?.let {
                            store.increaseMetricForPixelDefinition(definition, APP_USE).takeIf { it == metric.value.toInt() }?.apply {
                                pixel.fire(definition.pixelName, definition.params)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun isInConversionWindow(definition: PixelDefinition): Boolean {
        val enrollmentDate = definition.params["enrollmentDate"] ?: return false
        val lowerWindow = definition.params["conversionWindowDays"]?.split("-")?.first()?.toInt() ?: return false
        val upperWindow = definition.params["conversionWindowDays"]?.split("-")?.last()?.toInt() ?: return false
        val daysDiff = daysBetweenTodayAnd(enrollmentDate)

        return (daysDiff in lowerWindow..upperWindow)
    }

    private fun daysBetweenTodayAnd(date: String): Long {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val localDate = LocalDate.parse(date)
        val zoneDateTime: ZonedDateTime = localDate.atStartOfDay(ZoneId.of("America/New_York"))
        return ChronoUnit.DAYS.between(zoneDateTime, today)
    }
}
