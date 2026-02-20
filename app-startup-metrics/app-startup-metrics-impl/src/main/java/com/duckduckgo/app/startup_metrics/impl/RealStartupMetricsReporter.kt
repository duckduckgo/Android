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

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.startup_metrics.api.StartupMetricsReporter
import com.duckduckgo.app.startup_metrics.impl.collectors.StartupCollectorProvider
import com.duckduckgo.app.startup_metrics.impl.feature.StartupMetricsFeature
import com.duckduckgo.app.startup_metrics.impl.lifecycle.StartupTypeDetectionLifecycleObserver
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
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
    private val collectorProvider: StartupCollectorProvider,
    private val startupTypeDetectionObserver: StartupTypeDetectionLifecycleObserver,
    private val samplingDecider: SamplingDecider,
    private val pixel: Pixel,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : StartupMetricsReporter {

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

            // Get the startup type detected by the global lifecycle observer
            val detectedStartupType = startupTypeDetectionObserver.getDetectedStartupType()
            val event = collectorProvider.collectStartupMetrics(startupType = detectedStartupType)
            if (event != null) {
                logcat { "Reporting startup metrics: $event" }
                emitStartupMetricsPixel(event)
            }
        }
    }

    /**
     * Emits a pixel with startup metrics to the analytics backend.
     */
    private fun emitStartupMetricsPixel(event: StartupMetricEvent) {
        val parameters = buildMap {
            put(StartupMetricsPixelParameters.STARTUP_TYPE, event.startupType.name.lowercase())
            put(StartupMetricsPixelParameters.MEASUREMENT_METHOD, event.measurementMethod.name.lowercase())
            put(StartupMetricsPixelParameters.API_LEVEL, event.apiLevel.toString())

            // API 35+ measurements include TTID and TTFD
            if (event.measurementMethod == MeasurementMethod.API_35_NATIVE) {
                event.ttidDurationMs?.let { put(StartupMetricsPixelParameters.TTID_DURATION_MS, it.toString()) }
                put(StartupMetricsPixelParameters.TTFD_DURATION_MS, event.ttfdDurationMs.toString())
            } else {
                // Legacy measurements only have manual TTFD
                put(StartupMetricsPixelParameters.TTFD_MANUAL_DURATION_MS, event.ttfdDurationMs.toString())
            }

            event.deviceRamBucket?.let { put(StartupMetricsPixelParameters.DEVICE_RAM_BUCKET, it) }
            event.cpuArchitecture?.let { put(StartupMetricsPixelParameters.CPU_ARCHITECTURE, it) }
        }

        pixel.fire(
            pixel = StartupMetricsPixelName.APP_STARTUP_TIME,
            parameters = parameters,
            encodedParameters = emptyMap(),
            type = Pixel.PixelType.Count,
        )
    }
}
