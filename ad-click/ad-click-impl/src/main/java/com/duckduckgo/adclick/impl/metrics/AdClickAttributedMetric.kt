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

package com.duckduckgo.adclick.impl.metrics

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt

interface AdClickCollector {
    fun onAdClick()
}

/**
 * Ad clicks 7d avg Attributed Metric
 * Trigger: on first Ad click of day
 * Type: Daily pixel
 * Report: 7d rolling average of ad clicks (bucketed value). Not sent if count is 0.
 * Specs: https://app.asana.com/1/137249556945/project/1206716555947156/task/1211301604929610?focus=true
 */
@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@ContributesBinding(AppScope::class, AdClickCollector::class)
@SingleInstanceIn(AppScope::class)
class RealAdClickAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val appInstall: AppInstall,
) : AttributedMetric, AdClickCollector {

    companion object {
        private const val EVENT_NAME = "ad_click"
        private const val PIXEL_NAME = "user_average_ad_clicks_past_week"
        private const val DAYS_WINDOW = 7
        private val AD_CLICK_BUCKETS = arrayOf(2, 5)
    }

    override fun onAdClick() {
        attributedMetricClient.collectEvent(EVENT_NAME)

        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (shouldSendPixel().not()) {
                logcat(tag = "AttributedMetrics") {
                    "AdClickCount7d: Skip emitting, not enough data or no events"
                }
                return@launch
            }
            attributedMetricClient.emitMetric(this@RealAdClickAttributedMetric)
        }
    }

    override fun getPixelName(): String = PIXEL_NAME

    override suspend fun getMetricParameters(): Map<String, String> {
        val stats = getEventStats()
        val params = mutableMapOf(
            "count" to getBucketValue(stats.rollingAverage.roundToInt()).toString(),
        )
        if (!hasCompleteDataWindow()) {
            params["dayAverage"] = daysSinceInstalled().toString()
        }
        return params
    }

    override suspend fun getTag(): String {
        return daysSinceInstalled().toString()
    }

    private fun getBucketValue(avg: Int): Int {
        return AD_CLICK_BUCKETS.indexOfFirst { bucket -> avg <= bucket }.let { index ->
            if (index == -1) AD_CLICK_BUCKETS.size else index
        }
    }

    private suspend fun shouldSendPixel(): Boolean {
        if (daysSinceInstalled() <= 0) {
            // installation day, we don't emit
            return false
        }

        val eventStats = getEventStats()
        if (eventStats.daysWithEvents == 0 || eventStats.rollingAverage == 0.0) {
            // no events, nothing to emit
            return false
        }

        return true
    }

    private fun hasCompleteDataWindow(): Boolean {
        val daysSinceInstalled = daysSinceInstalled()
        return daysSinceInstalled >= DAYS_WINDOW
    }

    private suspend fun getEventStats(): EventStats {
        val daysSinceInstall = daysSinceInstalled()
        val stats = if (daysSinceInstall >= DAYS_WINDOW) {
            attributedMetricClient.getEventStats(EVENT_NAME, DAYS_WINDOW)
        } else {
            attributedMetricClient.getEventStats(EVENT_NAME, daysSinceInstall)
        }

        return stats
    }

    private fun daysSinceInstalled(): Int {
        val etZone = ZoneId.of("America/New_York")
        val installInstant = Instant.ofEpochMilli(appInstall.getInstallationTimestamp())
        val nowInstant = Instant.now()

        val installInEt = installInstant.atZone(etZone)
        val nowInEt = nowInstant.atZone(etZone)

        return ChronoUnit.DAYS.between(installInEt.toLocalDate(), nowInEt.toLocalDate()).toInt()
    }
}
