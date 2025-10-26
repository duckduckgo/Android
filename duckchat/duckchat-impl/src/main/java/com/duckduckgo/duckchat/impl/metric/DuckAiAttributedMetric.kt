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

package com.duckduckgo.duckchat.impl.metric

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricConfig
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.api.MetricBucket
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import logcat.logcat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.roundToInt

interface DuckAiMetricCollector {
    fun onMessageSent()
}

@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@ContributesBinding(AppScope::class, DuckAiMetricCollector::class)
@SingleInstanceIn(AppScope::class)
class DuckAiAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val attributedMetricClient: AttributedMetricClient,
    private val appInstall: AppInstall,
    private val attributedMetricConfig: AttributedMetricConfig,
) : AttributedMetric, DuckAiMetricCollector {

    companion object {
        private const val EVENT_NAME = "submit_prompt"
        private const val PIXEL_NAME = "user_average_duck_ai_usage_past_week"
        private const val FEATURE_TOGGLE_NAME = "aiUsageAvg"
        private const val FEATURE_EMIT_TOGGLE_NAME = "canEmitAIUsageAvg"
        private const val DAYS_WINDOW = 7
    }

    private val isEnabled: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val canEmit: Deferred<Boolean> = appCoroutineScope.async(start = LAZY) {
        getToggle(FEATURE_EMIT_TOGGLE_NAME)?.isEnabled() ?: false
    }

    private val bucketConfig: Deferred<MetricBucket> = appCoroutineScope.async(start = LAZY) {
        attributedMetricConfig.getBucketConfiguration()[PIXEL_NAME] ?: MetricBucket(
            buckets = listOf(5, 9),
            version = 0,
        )
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

    override fun onMessageSent() {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!isEnabled.await()) return@launch
            attributedMetricClient.collectEvent(EVENT_NAME)
            if (shouldSendPixel().not()) {
                logcat(tag = "AttributedMetrics") {
                    "DuckAiUsage: Skip emitting, not enough data or no events"
                }
                return@launch
            }

            if (canEmit.await()) {
                attributedMetricClient.emitMetric(this@DuckAiAttributedMetric)
            }
        }
    }

    private suspend fun getBucketValue(avg: Int): Int {
        val buckets = bucketConfig.await().buckets
        return buckets.indexOfFirst { bucket -> avg <= bucket }.let { index ->
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

    private suspend fun getToggle(toggleName: String) =
        attributedMetricConfig.metricsToggles().firstOrNull { toggle ->
            toggle.featureName().name == toggleName
        }
}
