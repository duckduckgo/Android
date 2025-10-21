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

package com.duckduckgo.app.attributed.metrics.retention

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
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

@ContributesMultibinding(AppScope::class, AtbLifecyclePlugin::class)
@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@SingleInstanceIn(AppScope::class)
class RetentionMonthAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val appInstall: AppInstall,
    private val attributedMetricClient: AttributedMetricClient,
    private val dateUtils: AttributedMetricsDateUtils,
    private val attributedMetricConfig: AttributedMetricConfig,
) : AttributedMetric, AtbLifecyclePlugin {

    companion object {
        private const val PIXEL_NAME_FIRST_MONTH = "user_retention_month"
        private const val DAYS_IN_4_WEEKS = 28 // we consider 1 month after 4 weeks
        private const val MONTH_DAY_THRESHOLD = DAYS_IN_4_WEEKS + 1
        private const val START_MONTH_THRESHOLD = 2
        private const val FEATURE_TOGGLE_NAME = "retention"
        private const val FEATURE_EMIT_TOGGLE_NAME = "canEmitRetention"
    }

    private val isEnabled: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val canEmit: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_EMIT_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val bucketConfig: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[PIXEL_NAME_FIRST_MONTH] ?: MetricBucket(
            buckets = listOf(2, 3, 4, 5),
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
                    "RetentionMonth: Skip emitting atb not changed"
                }
                return@launch
            }
            if (shouldSendPixel().not()) {
                logcat(tag = "AttributedMetrics") {
                    "RetentionMonth: Skip emitting, outside window"
                }
                return@launch
            }
            if (canEmit.await()) {
                attributedMetricClient.emitMetric(this@RetentionMonthAttributedMetric)
            }
        }
    }

    override fun getPixelName(): String = PIXEL_NAME_FIRST_MONTH

    override suspend fun getMetricParameters(): Map<String, String> {
        val month = getMonthSinceInstall()
        if (month < START_MONTH_THRESHOLD) return emptyMap()

        return mutableMapOf("count" to bucketMonth(month).toString())
    }

    override suspend fun getTag(): String {
        val month = getMonthSinceInstall()
        return bucketMonth(month).toString()
    }

    private fun shouldSendPixel(): Boolean {
        val month = getMonthSinceInstall()
        if (month < START_MONTH_THRESHOLD) return false

        return true
    }

    private fun getMonthSinceInstall(): Int {
        val daysSinceInstall = daysSinceInstalled()
        return if (daysSinceInstall < MONTH_DAY_THRESHOLD) {
            return 1
        } else {
            ((daysSinceInstall - MONTH_DAY_THRESHOLD) / DAYS_IN_4_WEEKS) + 2
        }
    }

    private suspend fun bucketMonth(month: Int): Int {
        if (month < START_MONTH_THRESHOLD) return -1
        val buckets = bucketConfig.await().buckets
        return buckets.indexOfFirst { bucket -> month <= bucket }.let { index ->
            if (index == -1) buckets.size else index
        }
    }

    private fun daysSinceInstalled(): Int {
        return dateUtils.daysSince(appInstall.getInstallationTimestamp())
    }

    private suspend fun getToggle(toggleName: String) =
        attributedMetricConfig.metricsToggles().firstOrNull { toggle ->
            toggle.featureName().name == toggleName
        }
}
