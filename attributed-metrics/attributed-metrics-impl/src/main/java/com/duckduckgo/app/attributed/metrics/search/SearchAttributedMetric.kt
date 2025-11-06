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

package com.duckduckgo.app.attributed.metrics.search

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.app.statistics.store.StatisticsDataStore
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject
import kotlin.math.roundToInt

/**
 * Search Count 7d avg Attributed Metric
 * Trigger: on first search of day
 * Type: Daily pixel
 * Report: 7d rolling average of searches (bucketed value). Not sent if count is 0.
 * Specs: https://app.asana.com/1/137249556945/project/1206716555947156/task/1211313432282643?focus=true
 */
@ContributesMultibinding(AppScope::class, AtbLifecyclePlugin::class)
@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@SingleInstanceIn(AppScope::class)
class SearchAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val appInstall: AppInstall,
    private val statisticsDataStore: StatisticsDataStore,
    private val dateUtils: AttributedMetricsDateUtils,
    private val attributedMetricConfig: AttributedMetricConfig,
) : AttributedMetric, AtbLifecyclePlugin {

    companion object {
        private const val EVENT_NAME = "ddg_search"
        private const val FIRST_MONTH_PIXEL = "attributed_metric_average_searches_past_week_first_month"
        private const val PAST_WEEK_PIXEL_NAME = "attributed_metric_average_searches_past_week"
        private const val DAYS_WINDOW = 7
        private const val FIRST_MONTH_DAY_THRESHOLD = 28 // we consider 1 month after 4 weeks
        private const val FEATURE_TOGGLE_NAME = "searchCountAvg"
        private const val FEATURE_EMIT_TOGGLE_NAME = "canEmitSearchCountAvg"
    }

    private val isEnabled: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val canEmit: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_EMIT_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val bucketConfigFirstMonth: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[FIRST_MONTH_PIXEL] ?: MetricBucket(
            buckets = listOf(5, 9),
            version = 0,
        )
    }

    private val bucketConfigPastWeek: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[PAST_WEEK_PIXEL_NAME] ?: MetricBucket(
            buckets = listOf(5, 9),
            version = 0,
        )
    }

    override fun onSearchRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled.await()) return@launch
            attributedMetricClient.collectEvent(EVENT_NAME)

            if (oldAtb == newAtb) {
                logcat(tag = "AttributedMetrics") {
                    "SearchCount7d: Skip emitting, atb not changed"
                }
                return@launch
            }
            if (shouldSendPixel().not()) {
                logcat(tag = "AttributedMetrics") {
                    "SearchCount7d: Skip emitting, not enough data or no events"
                }
                return@launch
            }
            if (canEmit.await()) {
                attributedMetricClient.emitMetric(this@SearchAttributedMetric)
            }
        }
    }

    override fun getPixelName(): String = when (daysSinceInstalled()) {
        in 0..FIRST_MONTH_DAY_THRESHOLD -> FIRST_MONTH_PIXEL
        else -> PAST_WEEK_PIXEL_NAME
    }

    override suspend fun getMetricParameters(): Map<String, String> {
        val stats = getEventStats()
        val params = mutableMapOf(
            "count" to getBucketValue(stats.rollingAverage.roundToInt()).toString(),
            "version" to getBucketConfig().version.toString(),
        )
        if (!hasCompleteDataWindow()) {
            params["dayAverage"] = daysSinceInstalled().toString()
        }
        return params
    }

    override suspend fun getTag(): String {
        // Daily metric, on first search of day
        // rely on searchRetentionAtb as mirrors the metric trigger event
        return statisticsDataStore.searchRetentionAtb
            ?: "no-atb" // should not happen, but just in case
    }

    private suspend fun getBucketValue(searches: Int): Int {
        val buckets = getBucketConfig().buckets
        return buckets.indexOfFirst { bucket -> searches <= bucket }.let { index ->
            if (index == -1) buckets.size else index
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

    private suspend fun getEventStats(): EventStats {
        val stats = if (hasCompleteDataWindow()) {
            attributedMetricClient.getEventStats(EVENT_NAME, DAYS_WINDOW)
        } else {
            attributedMetricClient.getEventStats(
                EVENT_NAME,
                daysSinceInstalled(),
            )
        }
        return stats
    }

    private suspend fun getBucketConfig() = when (daysSinceInstalled()) {
        in 0..FIRST_MONTH_DAY_THRESHOLD -> bucketConfigFirstMonth.await()
        else -> bucketConfigPastWeek.await()
    }

    private fun hasCompleteDataWindow(): Boolean {
        val daysSinceInstalled = daysSinceInstalled()
        return daysSinceInstalled >= DAYS_WINDOW
    }

    private fun daysSinceInstalled(): Int {
        return dateUtils.daysSince(appInstall.getInstallationTimestamp())
    }

    private suspend fun getToggle(toggleName: String) =
        attributedMetricConfig.metricsToggles().firstOrNull { toggle ->
            toggle.featureName().name == toggleName
        }
}
