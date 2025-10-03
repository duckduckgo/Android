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

package com.duckduckgo.app.attributed.metrics

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.EventStats
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
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

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
class RealSearchAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val appInstall: AppInstall,
    private val statisticsDataStore: StatisticsDataStore,
    private val dateUtils: AttributedMetricsDateUtils,
) : AttributedMetric, AtbLifecyclePlugin {

    companion object {
        private const val EVENT_NAME = "ddg_search"
        private const val FIRST_MONTH_PIXEL = "user_average_searches_past_week_first_month"
        private const val PAST_WEEK_PIXEL_NAME = "user_average_searches_past_week"
        private const val DAYS_WINDOW = 7
        private const val FIRST_MONTH_DAY_THRESHOLD = 28 // we consider 1 month after 4 weeks
        private val SEARCH_BUCKETS = arrayOf(
            5,
            9,
        ) // TODO: default bucket, remote bucket implementation will happen in future PRs
    }

    override fun onSearchRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
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
            attributedMetricClient.emitMetric(this@RealSearchAttributedMetric)
        }
    }

    override fun getPixelName(): String = when (daysSinceInstalled()) {
        in 0..FIRST_MONTH_DAY_THRESHOLD -> FIRST_MONTH_PIXEL
        else -> PAST_WEEK_PIXEL_NAME
    }

    override suspend fun getMetricParameters(): Map<String, String> {
        val stats = getEventStats()
        val params = mutableMapOf(
            "count" to getBucketValue(stats.rollingAverage.toInt()).toString(),
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

    private fun getBucketValue(searches: Int): Int {
        return SEARCH_BUCKETS.indexOfFirst { bucket -> searches <= bucket }.let { index ->
            if (index == -1) SEARCH_BUCKETS.size else index
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

    private fun hasCompleteDataWindow(): Boolean {
        val daysSinceInstalled = daysSinceInstalled()
        return daysSinceInstalled >= DAYS_WINDOW
    }

    private fun daysSinceInstalled(): Int {
        return dateUtils.daysSince(appInstall.getInstallationTimestamp())
    }
}
