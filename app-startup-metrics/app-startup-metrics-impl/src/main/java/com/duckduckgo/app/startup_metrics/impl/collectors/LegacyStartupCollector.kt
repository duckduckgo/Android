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

package com.duckduckgo.app.startup_metrics.impl.collectors

import com.duckduckgo.app.startup_metrics.impl.StartupMetricEvent
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.android.ProcessStartupTimeProvider
import com.duckduckgo.app.startup_metrics.impl.lifecycle.StartupTypeDetectionLifecycleObserver
import com.duckduckgo.app.startup_metrics.impl.metrics.CpuCollector
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
import com.duckduckgo.app.startup_metrics.impl.metrics.MemoryCollector
import com.duckduckgo.common.utils.CurrentTimeProvider
import javax.inject.Inject

/**
 * Implementation of legacy startup metrics collection for API < 35.
 */
class LegacyStartupCollector @Inject constructor(
    private val memoryCollector: MemoryCollector,
    private val cpuCollector: CpuCollector,
    private val timeProvider: CurrentTimeProvider,
    private val processStartupTimeProvider: ProcessStartupTimeProvider,
    private val apiLevelProvider: ApiLevelProvider,
    private val startupTypeDetectionObserver: StartupTypeDetectionLifecycleObserver,
) : StartupCollector {
    override suspend fun collectStartupMetrics(): StartupMetricEvent {
        val startupType = startupTypeDetectionObserver.getDetectedStartupType()
        val startTimeMs = processStartupTimeProvider.getStartUptimeMillis()
        val currentUptimeMs = timeProvider.uptimeMillis()
        val duration = currentUptimeMs - startTimeMs
        val ttfdDurationMs = if (duration < 0) 0L else duration
        return StartupMetricEvent(
            startupType = startupType,
            ttidDurationMs = null, // Not available in legacy measurement
            ttfdDurationMs = ttfdDurationMs,
            deviceRamBucket = memoryCollector.collectDeviceRamBucket(),
            cpuArchitecture = cpuCollector.collectCpuArchitecture(),
            measurementMethod = MeasurementMethod.LEGACY_MANUAL,
            apiLevel = apiLevelProvider.getApiLevel(),
        )
    }
}
