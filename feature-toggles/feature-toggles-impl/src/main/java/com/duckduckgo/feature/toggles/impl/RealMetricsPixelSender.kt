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

package com.duckduckgo.feature.toggles.impl

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.anvil.annotations.PriorityKey
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.MetricType
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelExtension
import com.duckduckgo.feature.toggles.api.MetricsPixelExtensionProvider
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.withContext
import okio.ByteString.Companion.encode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealMetricsPixelSender @Inject constructor(
    private val pixel: Pixel,
    private val store: MetricsPixelStore,
    private val dispatcherProvider: DispatcherProvider,
) : MetricsPixelExtension {

    override suspend fun send(metricsPixel: MetricsPixel): Boolean {
        val definitions = metricsPixel.getPixelDefinitions()
        if (definitions.isEmpty()) return false
        return withContext(dispatcherProvider.io()) {
            var sent = false
            definitions.forEach { definition ->
                val definitionSent = when (metricsPixel.type) {
                    MetricType.NORMAL -> sendNormal(definition)
                    MetricType.COUNT_WHEN_IN_WINDOW -> sendCount(definition, metricsPixel.value.toInt())
                    MetricType.COUNT_ALWAYS -> false // TODO: not implemented yet
                }
                sent = sent || definitionSent
            }
            sent
        }
    }

    private suspend fun sendNormal(definition: PixelDefinition): Boolean {
        if (!isInConversionWindow(definition)) return false
        val tag = tagFor(definition)
        if (store.wasPixelFired(tag)) return false
        pixel.fire(definition.pixelName, definition.params)
        store.storePixelTag(tag)
        return true
    }

    private suspend fun sendCount(definition: PixelDefinition, threshold: Int): Boolean {
        if (!isInConversionWindow(definition)) return false
        val count = store.getMetricForPixelDefinition(definition)
        if (count >= threshold) return false
        val newCount = store.increaseMetricForPixelDefinition(definition)
        if (newCount != threshold) return false
        val tag = tagFor(definition)
        if (store.wasPixelFired(tag)) return false
        pixel.fire(definition.pixelName, definition.params)
        store.storePixelTag(tag)
        return true
    }

    private fun tagFor(definition: PixelDefinition): String {
        return (
            "${definition.pixelName}{metric=${definition.params["metric"]}" +
                "value=${definition.params["value"]}" +
                "conversionWindow=${definition.params["conversionWindowDays"]}" +
                "enrollmentDate=${definition.params["enrollmentDate"]}}"
            ).encode().md5().hex()
    }

    private fun isInConversionWindow(definition: PixelDefinition): Boolean {
        val enrollmentDate = definition.params["enrollmentDate"] ?: return false
        val lowerWindow = definition.params["conversionWindowDays"]?.split("-")?.first()?.toInt() ?: return false
        val upperWindow = definition.params["conversionWindowDays"]?.split("-")?.last()?.toInt() ?: return false
        return daysBetweenTodayAnd(enrollmentDate) in lowerWindow..upperWindow
    }

    private fun daysBetweenTodayAnd(date: String): Long {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val localDate = LocalDate.parse(date)
        val zoneDateTime = localDate.atStartOfDay(ZoneId.of("America/New_York"))
        return ChronoUnit.DAYS.between(zoneDateTime, today)
    }
}

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = MainProcessLifecycleObserver::class,
)
@PriorityKey(1)
class MetricsPixelExtensionProviderObserver @Inject constructor(
    private val metricsPixelExtension: MetricsPixelExtension,
) : MainProcessLifecycleObserver {

    override fun onCreate(owner: LifecycleOwner) {
        MetricsPixelExtensionProvider.instance = metricsPixelExtension
    }
}
