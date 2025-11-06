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

package com.duckduckgo.app.attributed.metrics.impl

import com.duckduckgo.app.attributed.metrics.api.AttributedMetric
import com.duckduckgo.app.attributed.metrics.api.AttributedMetricClient
import com.duckduckgo.app.attributed.metrics.api.EventStats
import com.duckduckgo.app.attributed.metrics.store.AttributedMetricsDateUtils
import com.duckduckgo.app.attributed.metrics.store.EventRepository
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Unique
import com.duckduckgo.browser.api.install.AppInstall
import com.duckduckgo.browser.api.referrer.AppReferrer
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.logcat
import javax.inject.Inject

@ContributesBinding(AppScope::class, AttributedMetricClient::class)
@SingleInstanceIn(AppScope::class)
class RealAttributedMetricClient @Inject constructor(
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
    private val eventRepository: EventRepository,
    private val pixel: Pixel,
    private val metricsState: AttributedMetricsState,
    private val appReferrer: AppReferrer,
    private val dateUtils: AttributedMetricsDateUtils,
    private val appInstall: AppInstall,
) : AttributedMetricClient {

    override fun collectEvent(eventName: String) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!metricsState.isActive()) {
                logcat(tag = "AttributedMetrics") {
                    "Discard collect event $eventName, client not active"
                }
                return@launch
            }
            eventRepository.collectEvent(eventName).also {
                logcat(tag = "AttributedMetrics") {
                    "Collected event $eventName"
                }
            }
        }
    }

    override suspend fun getEventStats(
        eventName: String,
        days: Int,
    ): EventStats =
        withContext(dispatcherProvider.io()) {
            if (!metricsState.isActive()) {
                logcat(tag = "AttributedMetrics") {
                    "Discard get stats for event $eventName, client not active"
                }
                return@withContext EventStats(daysWithEvents = 0, rollingAverage = 0.0, totalEvents = 0)
            }
            eventRepository.getEventStats(eventName, days).also {
                logcat(tag = "AttributedMetrics") {
                    "Returning Stats for Event $eventName($days days): $it"
                }
            }
        }

    override fun emitMetric(metric: AttributedMetric) {
        appCoroutineScope.launch(dispatcherProvider.io()) {
            if (!metricsState.isActive() || !metricsState.canEmitMetrics()) {
                logcat(tag = "AttributedMetrics") {
                    "Discard pixel, client not active"
                }
                return@launch
            }

            val pixelName = metric.getPixelName()
            val params = metric.getMetricParameters()
            val tag = metric.getTag()
            val pixelTag = "${pixelName}_$tag"

            val origin = appReferrer.getOriginAttributeCampaign()
            val paramsMutableMap = params.toMutableMap()
            if (!origin.isNullOrBlank()) {
                paramsMutableMap["origin"] = origin
            } else {
                paramsMutableMap["install_date"] = getInstallDate()
            }
            pixel.fire(pixelName = pixelName, parameters = paramsMutableMap, type = Unique(pixelTag)).also {
                logcat(tag = "AttributedMetrics") {
                    "Fired pixel $pixelName with params $paramsMutableMap"
                }
            }
        }
    }

    private fun getInstallDate(): String {
        return dateUtils.getDateFromTimestamp(appInstall.getInstallationTimestamp())
    }
}
