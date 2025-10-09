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
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.api.AtbLifecyclePlugin
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

@ContributesMultibinding(AppScope::class, AtbLifecyclePlugin::class)
@ContributesMultibinding(AppScope::class, AttributedMetric::class)
@SingleInstanceIn(AppScope::class)
class RetentionWeekAttributedMetric @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val appInstall: AppInstall,
    private val attributedMetricClient: AttributedMetricClient,
    private val dateUtils: AttributedMetricsDateUtils,
) : AttributedMetric, AtbLifecyclePlugin {

    companion object {
        private const val PIXEL_NAME_FIRST_MONTH = "user_retention_week"
    }

    override fun onAppRetentionAtbRefreshed(
        oldAtb: String,
        newAtb: String,
    ) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (oldAtb == newAtb) {
                logcat(tag = "AttributedMetrics") {
                    "RetentionFirstMonth: Skip emitting atb not changed"
                }
                return@launch
            }
            if (shouldSendPixel().not()) {
                logcat(tag = "AttributedMetrics") {
                    "RetentionFirstMonth: Skip emitting, outside window"
                }
                return@launch
            }
            attributedMetricClient.emitMetric(this@RetentionWeekAttributedMetric)
        }
    }

    override fun getPixelName(): String = PIXEL_NAME_FIRST_MONTH

    override suspend fun getMetricParameters(): Map<String, String> {
        val days = daysSinceInstalled()
        if (days <= 0 || days >= 29) return emptyMap()

        val week = getWeekFromDays(days)
        return if (week > 0) {
            mutableMapOf("count" to week.toString())
        } else {
            emptyMap()
        }
    }

    override suspend fun getTag(): String {
        val days = daysSinceInstalled()
        return getWeekFromDays(days).toString()
    }

    private fun shouldSendPixel(): Boolean {
        val days = daysSinceInstalled()
        if (days <= 0 || days >= 29) return false

        return true
    }

    private fun getWeekFromDays(days: Int): Int {
        return when (days) {
            in 1..7 -> 1
            in 8..14 -> 2
            in 15..21 -> 3
            in 22..28 -> 4
            else -> -1
        }
    }

    private fun daysSinceInstalled(): Int {
        return dateUtils.daysSince(appInstall.getInstallationTimestamp())
    }
}
