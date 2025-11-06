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

/**
 * Search Days Attributed Metric
 * Trigger: on app start
 * Type: Daily pixel
 * Report: Bucketed value, how many days user searched last 7d. Not sent if count is 0.
 * Specs: https://app.asana.com/1/137249556945/project/1206716555947156/task/1211301604929609?focus=true
 */
@ContributesMultibinding(AppScope::class, AtbLifecyclePlugin::class)
@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@SingleInstanceIn(AppScope::class)
class SearchDaysAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val appInstall: AppInstall,
    private val statisticsDataStore: StatisticsDataStore,
    private val dateUtils: AttributedMetricsDateUtils,
    private val attributedMetricConfig: AttributedMetricConfig,
) : AttributedMetric, AtbLifecyclePlugin {

    companion object {
        private const val EVENT_NAME = "ddg_search_days"
        private const val PIXEL_NAME = "attributed_metric_active_past_week"
        private const val DAYS_WINDOW = 7
        private const val FEATURE_TOGGLE_NAME = "searchDaysAvg"
        private const val FEATURE_EMIT_TOGGLE_NAME = "canEmitSearchDaysAvg"
    }

    private val isEnabled: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val canEmit: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_EMIT_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val bucketConfiguration: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[PIXEL_NAME] ?: MetricBucket(
            buckets = listOf(2, 4),
            version = 0,
        )
    }

    override fun onAppRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled.await()) return@launch

            if (oldAtb == newAtb) {
                logcat(tag = "AttributedMetrics") {
                    "SearchDays: Skip emitting atb not changed"
                }
                return@launch
            }
            if (shouldSendPixel().not()) {
                logcat(tag = "AttributedMetrics") {
                    "SearchDays: Skip emitting, not enough data or no events"
                }
                return@launch
            }

            if (canEmit.await()) {
                attributedMetricClient.emitMetric(this@SearchDaysAttributedMetric)
            }
        }
    }

    override fun onSearchRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled.await()) return@launch
            attributedMetricClient.collectEvent(EVENT_NAME)
        }
    }

    override fun getPixelName(): String = PIXEL_NAME

    override suspend fun getMetricParameters(): Map<String, String> {
        val daysSinceInstalled = daysSinceInstalled()
        val hasCompleteDataWindow = daysSinceInstalled >= DAYS_WINDOW
        val stats = attributedMetricClient.getEventStats(EVENT_NAME, DAYS_WINDOW)
        val params = mutableMapOf(
            "days" to getBucketValue(stats.daysWithEvents).toString(),
            "version" to bucketConfiguration.await().version.toString(),
        )
        if (!hasCompleteDataWindow) {
            params["daysSinceInstalled"] = daysSinceInstalled.toString()
        }
        return params
    }

    override suspend fun getTag(): String {
        // Daily metric, on App start
        // rely on appRetentionAtb as mirrors the metric trigger event
        return statisticsDataStore.appRetentionAtb ?: "no-atb" // should not happen, but just in case
    }

    private suspend fun getBucketValue(days: Int): Int {
        val buckets = bucketConfiguration.await().buckets
        return buckets.indexOfFirst { bucket -> days <= bucket }.let { index ->
            if (index == -1) buckets.size else index
        }
    }

    private suspend fun shouldSendPixel(): Boolean {
        if (daysSinceInstalled() <= 0) {
            // installation day, we don't emit
            return false
        }

        val eventStats = attributedMetricClient.getEventStats(EVENT_NAME, DAYS_WINDOW)
        if (eventStats.daysWithEvents == 0) {
            // no events, nothing to emit
            return false
        }

        return true
    }

    private fun daysSinceInstalled(): Int {
        return dateUtils.daysSince(appInstall.getInstallationTimestamp())
    }

    private suspend fun getToggle(toggleName: String) =
        attributedMetricConfig.metricsToggles().firstOrNull { toggle ->
            toggle.featureName().name == toggleName
        }
}
