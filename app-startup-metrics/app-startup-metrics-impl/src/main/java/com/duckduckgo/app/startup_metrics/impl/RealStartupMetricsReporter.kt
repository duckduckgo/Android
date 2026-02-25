/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.app.startup_metrics.impl

import android.annotation.SuppressLint
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.startup_metrics.api.StartupMetricsReporter
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.collectors.Api35StartupCollector
import com.duckduckgo.app.startup_metrics.impl.collectors.LegacyStartupCollector
import com.duckduckgo.app.startup_metrics.impl.feature.StartupMetricsFeature
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelName
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelParameters
import com.duckduckgo.app.startup_metrics.impl.sampling.SamplingDecider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import logcat.logcat
import javax.inject.Inject

/**
 * Implementation of startup metrics reporter.
 */
@ContributesBinding(AppScope::class)
class RealStartupMetricsReporter @Inject constructor(
    private val startupMetricsFeature: StartupMetricsFeature,
    private val legacyCollector: LegacyStartupCollector,
    private val api35Collector: Api35StartupCollector,
    private val apiLevelProvider: ApiLevelProvider,
    private val samplingDecider: SamplingDecider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : StartupMetricsReporter {

    @SuppressLint("NewApi")
    override fun reportStartupComplete() {
        appCoroutineScope.launch {
            if (!startupMetricsFeature.self().isEnabled()) {
                logcat { "Startup metrics feature is disabled" }
                return@launch
            }

            if (!samplingDecider.shouldSample()) {
                logcat { "Startup metrics not sampled" }
                return@launch
            }

            // Always collect manual TTFD measurement
            val manualEvent = legacyCollector.collectStartupMetrics()

            // On API 35+, also collect native TTID and TTFD
            val nativeEvent = if (apiLevelProvider.getApiLevel() >= 35) {
                api35Collector.collectStartupMetrics()
            } else {
                null
            }

            logcat { "Reporting startup metrics - Manual: $manualEvent, Native: $nativeEvent" }
            emitStartupMetricsPixel(manualEvent, nativeEvent)
        }
    }

    /**
     * Emits a pixel with startup metrics to the analytics backend.
     *
     * @param manualEvent Manual TTFD measurement (always available)
     * @param nativeEvent Native TTID/TTFD measurement (API 35+ only)
     */
    private fun emitStartupMetricsPixel(
        manualEvent: StartupMetricEvent,
        nativeEvent: StartupMetricEvent?,
    ) {
        val parameters = buildMap {
            put(StartupMetricsPixelParameters.STARTUP_TYPE, manualEvent.startupType.name.lowercase())
            put(StartupMetricsPixelParameters.API_LEVEL, manualEvent.apiLevel.toString())

            // Always include manual TTFD measurement
            put(StartupMetricsPixelParameters.TTFD_MANUAL_DURATION_MS, manualEvent.ttfdDurationMs.toString())

            // On API 35+, also include native measurements
            if (nativeEvent != null) {
                nativeEvent.ttidDurationMs?.let { put(StartupMetricsPixelParameters.TTID_DURATION_MS, it.toString()) }
                put(StartupMetricsPixelParameters.TTFD_DURATION_MS, nativeEvent.ttfdDurationMs.toString())
            }

            manualEvent.deviceRamBucket?.let { put(StartupMetricsPixelParameters.DEVICE_RAM_BUCKET, it) }
            manualEvent.cpuArchitecture?.let { put(StartupMetricsPixelParameters.CPU_ARCHITECTURE, it) }
        }

        pixel.fire(
            pixel = StartupMetricsPixelName.APP_STARTUP_TIME,
            parameters = parameters,
            encodedParameters = emptyMap(),
            type = Pixel.PixelType.Count,
        )
    }
}
