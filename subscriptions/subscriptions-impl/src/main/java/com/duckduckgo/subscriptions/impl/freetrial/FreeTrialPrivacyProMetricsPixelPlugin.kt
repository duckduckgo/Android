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

package com.duckduckgo.subscriptions.impl.freetrial

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.ConversionWindow
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.duckduckgo.feature.toggles.api.PixelDefinition
import com.duckduckgo.subscriptions.impl.PrivacyProFeature
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.Lazy
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

internal suspend fun FreeTrialPrivacyProPixelsPlugin.onPaywallImpression() {
    val metricPixel = this.getMetrics().firstOrNull { it.metric == "paywallImpressions" }
    this.firePixelFor(metricPixel)
}

internal suspend fun FreeTrialPrivacyProPixelsPlugin.onStartClickedMonthly() {
    val metricPixel = this.getMetrics().firstOrNull { it.metric == "startClickedMonthly" }
    this.firePixelFor(metricPixel)
}

internal suspend fun FreeTrialPrivacyProPixelsPlugin.onStartClickedYearly() {
    val metricPixel = this.getMetrics().firstOrNull { it.metric == "startClickedYearly" }
    this.firePixelFor(metricPixel)
}

internal suspend fun FreeTrialPrivacyProPixelsPlugin.onSubscriptionStartedMonthly() {
    val metricPixel = this.getMetrics().firstOrNull { it.metric == "subscriptionStartedMonthly" }
    this.firePixelFor(metricPixel)
}

internal suspend fun FreeTrialPrivacyProPixelsPlugin.onSubscriptionStartedYearly() {
    val metricPixel = this.getMetrics().firstOrNull { it.metric == "subscriptionStartedYearly" }
    this.firePixelFor(metricPixel)
}

@ContributesMultibinding(AppScope::class)
class FreeTrialPrivacyProPixelsPlugin @Inject constructor(
    private val toggle: Lazy<PrivacyProFeature>,
    private val freeTrialExperimentDataStore: FreeTrialExperimentDataStore,
    private val pixel: Pixel,
) : MetricsPixelPlugin {

    override suspend fun getMetrics(): List<MetricsPixel> {
        return listOf(
            MetricsPixel(
                metric = "paywallImpressions",
                value = getMetricsPixelValue(freeTrialExperimentDataStore.paywallImpressions),
                toggle = toggle.get().privacyProFreeTrialJan25(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 3)),
            ),
            MetricsPixel(
                metric = "startClickedMonthly",
                value = getMetricsPixelValue(freeTrialExperimentDataStore.paywallImpressions),
                toggle = toggle.get().privacyProFreeTrialJan25(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 3)),
            ),
            MetricsPixel(
                metric = "startClickedYearly",
                value = getMetricsPixelValue(freeTrialExperimentDataStore.paywallImpressions),
                toggle = toggle.get().privacyProFreeTrialJan25(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 3)),
            ),
            MetricsPixel(
                metric = "subscriptionStartedMonthly",
                value = getMetricsPixelValue(freeTrialExperimentDataStore.paywallImpressions),
                toggle = toggle.get().privacyProFreeTrialJan25(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 3)),
            ),
            MetricsPixel(
                metric = "subscriptionStartedYearly",
                value = getMetricsPixelValue(freeTrialExperimentDataStore.paywallImpressions),
                toggle = toggle.get().privacyProFreeTrialJan25(),
                conversionWindow = listOf(ConversionWindow(lowerWindow = 0, upperWindow = 3)),
            ),
        )
    }

    internal fun getMetricsPixelValue(paywallImpressions: Int): String {
        return when (paywallImpressions) {
            1, 2, 3, 4, 5 -> paywallImpressions.toString()
            in 6..10 -> "6-10"
            in 11..50 -> "11-50"
            in 50..Int.MAX_VALUE -> "51+"
            else -> "0"
        }
    }

    internal suspend fun firePixelFor(metricsPixel: MetricsPixel?) {
        metricsPixel?.let { metric ->
            metric.getPixelDefinitions().forEach { definition ->
                val hasMetricValueChanged = freeTrialExperimentDataStore.getMetricForPixelDefinition(definition) != metric.value
                if (definition.isInConversionWindow() && hasMetricValueChanged) {
                    freeTrialExperimentDataStore.increaseMetricForPixelDefinition(definition, metric.value)
                    pixel.fire(definition.pixelName, definition.params)
                }
            }
        }
    }
}

private fun PixelDefinition.isInConversionWindow(): Boolean {
    val enrollmentDate = this.params["enrollmentDate"] ?: return false
    val lowerWindow = this.params["conversionWindowDays"]?.split("-")?.first()?.toInt() ?: return false
    val upperWindow = this.params["conversionWindowDays"]?.split("-")?.last()?.toInt() ?: return false
    val daysDiff = enrollmentDate.daysUntilToday()

    return (daysDiff in lowerWindow..upperWindow)
}

private fun String.daysUntilToday(): Long {
    val today = ZonedDateTime.now(ZoneId.of("America/New_York"))
    val localDate = LocalDate.parse(this)
    val zoneDateTime: ZonedDateTime = localDate.atStartOfDay(ZoneId.of("America/New_York"))
    return ChronoUnit.DAYS.between(zoneDateTime, today)
}
