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

package com.duckduckgo.feature.toggles.impl

import com.duckduckgo.anvil.annotations.ContributesPluginPoint
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.common.utils.plugins.pixel.PixelInterceptorPlugin
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.METRICS_PIXEL_PREFIX
import com.duckduckgo.feature.toggles.api.MetricsPixel
import com.duckduckgo.feature.toggles.api.MetricsPixelPlugin
import com.squareup.anvil.annotations.ContributesMultibinding
import kotlinx.coroutines.runBlocking
import logcat.logcat
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ByteString.Companion.encode
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@ContributesMultibinding(
    scope = AppScope::class,
    boundType = PixelInterceptorPlugin::class,
)
class MetricPixelInterceptor @Inject constructor(
    private val metricsPixelPluginPoint: PluginPoint<MetricsPixelPlugin>,
    private val pixelStore: MetricsPixelStore,
) : PixelInterceptorPlugin, Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
        val pixel = chain.request().url.pathSegments.last()

        // If not a metrics pixel, proceed
        if (!pixel.startsWith(METRICS_PIXEL_PREFIX)) return chain.proceed(request.build())

        // If one of the parameters doesn't exist or is empty, drop request
        val metricName = chain.request().url.queryParameter("metric")
        val value = chain.request().url.queryParameter("value")
        val conversionWindowDays = chain.request().url.queryParameter("conversionWindowDays")
        val enrollmentDate = chain.request().url.queryParameter("enrollmentDate")

        if (metricName.isNullOrEmpty() || value.isNullOrEmpty() || conversionWindowDays.isNullOrEmpty() || enrollmentDate.isNullOrEmpty()) {
            return dummyResponse(chain)
        }
        val lowerWindow = conversionWindowDays.split("-").first().toInt()
        val upperWindow = conversionWindowDays.split("-").last().toInt()

        val metricsPixel: MetricsPixel? = metricsPixelPluginPoint.getPlugins()
            .flatMap { runBlocking { it.getMetrics() } }
            .firstOrNull { metric ->
                val featureName = metric.toggle.featureName().name
                val cohortName = metric.toggle.getRawStoredState()?.assignedCohort?.name

                if (featureName.isEmpty()) return@firstOrNull false
                if (cohortName.isNullOrEmpty()) return@firstOrNull false

                (
                    pixel.startsWith("${METRICS_PIXEL_PREFIX}_${featureName}_$cohortName") &&
                        metric.value == value &&
                        metric.metric == metricName &&
                        metric.conversionWindow.any { window ->
                            window.lowerWindow == lowerWindow &&
                                window.upperWindow == upperWindow
                        }
                    )
            }

        // If pixels does not match any ExperimentMetricsPixelPlugin, drop
        if (metricsPixel == null) return dummyResponse(chain)

        // If pixel already fired, drop
        val tag = "$pixel{metric=${metricName}value=${value}conversionWindow=${conversionWindowDays}enrollmentDate=$enrollmentDate}".encode()
            .md5().hex()
        val wasPixelFired = runBlocking { pixelStore.wasPixelFired(tag) }
        if (wasPixelFired) return dummyResponse(chain)

        // If inside conversion window, proceed, if not drop
        val diffDays = daysBetweenTodayAnd(enrollmentDate)
        return if (diffDays in lowerWindow..upperWindow) {
            chain.proceed(request.build()).also { pixelStore.storePixelTag(tag) }
        } else {
            dummyResponse(chain)
        }
    }

    private fun daysBetweenTodayAnd(date: String): Long {
        val today = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val localDate = LocalDate.parse(date)
        val zoneDateTime: ZonedDateTime = localDate.atStartOfDay(ZoneId.of("America/New_York"))
        return ChronoUnit.DAYS.between(zoneDateTime, today)
    }

    private fun dummyResponse(chain: Interceptor.Chain): Response {
        logcat { "Pixel URL request dropped: ${chain.request()}" }

        return Response.Builder()
            .code(500)
            .protocol(Protocol.HTTP_2)
            .body("Experiment metrics pixel dropped".toResponseBody())
            .message("Dropped experiment metrics pixel")
            .request(chain.request())
            .build()
    }

    override fun getInterceptor(): Interceptor {
        return this
    }
}

@ContributesPluginPoint(
    scope = AppScope::class,
    boundType = MetricsPixelPlugin::class,
)
@Suppress("unused")
interface ExperimentMetricsPixelPluginPoint
