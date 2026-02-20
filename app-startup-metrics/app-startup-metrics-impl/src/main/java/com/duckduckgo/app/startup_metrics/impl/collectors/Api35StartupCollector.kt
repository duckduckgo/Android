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

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import androidx.annotation.RequiresApi
import com.duckduckgo.app.startup_metrics.impl.StartupMetricEvent
import com.duckduckgo.app.startup_metrics.impl.StartupType
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.metrics.CpuCollector
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
import com.duckduckgo.app.startup_metrics.impl.metrics.MemoryCollector
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import logcat.logcat
import kotlin.coroutines.resume

/**
 * Collects startup metrics using Android 15+ (API 35+) ApplicationStartInfo API.
 */
@RequiresApi(35)
class Api35StartupCollector(
    private val context: Context,
    private val memoryCollector: MemoryCollector,
    private val cpuCollector: CpuCollector,
    private val timeProvider: CurrentTimeProvider,
    private val apiLevelProvider: ApiLevelProvider,
) : StartupCollector {

    private val activityManager: ActivityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    override suspend fun collectStartupMetrics(
        startupType: StartupType,
    ): StartupMetricEvent? {
        val fullyDrawnTimeMs = timeProvider.uptimeMillis()
        val startInfo = suspendCancellableCoroutine { continuation ->
            try {
                activityManager.addApplicationStartInfoCompletionListener(context.mainExecutor) { startInfo ->
                    continuation.resume(startInfo)
                }
            } catch (e: Exception) {
                logcat { "Error waiting for ApplicationStartInfo: $e" }
                continuation.resume(null)
            }
        }
        if (startInfo == null) {
            logcat { "Failed to retrieve ApplicationStartInfo, cannot collect startup metrics." }
            return null
        }
        val type = when (startInfo.startType) {
            ApplicationStartInfo.START_TYPE_COLD -> StartupType.COLD
            ApplicationStartInfo.START_TYPE_WARM -> StartupType.WARM
            ApplicationStartInfo.START_TYPE_HOT -> StartupType.HOT
            else -> StartupType.UNKNOWN
        }

        val startTimeNanos = startInfo.startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH] ?: return null
        val startTimeMs = startTimeNanos / 1_000_000L

        // TTID: Time To Initial Display (first frame rendered)
        val firstFrameTimeNanos = startInfo.startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]
        val ttidDurationMs = firstFrameTimeNanos?.let { (it / 1_000_000L) - startTimeMs }

        // TTFD: Time To Fully Drawn (reportFullyDrawn called)
        val ttfdDurationMs = fullyDrawnTimeMs - startTimeMs

        return StartupMetricEvent(
            startupType = type,
            ttidDurationMs = if (ttidDurationMs != null && ttidDurationMs >= 0) ttidDurationMs else null,
            ttfdDurationMs = if (ttfdDurationMs >= 0) ttfdDurationMs else 0L,
            deviceRamBucket = memoryCollector.collectDeviceRamBucket(),
            cpuArchitecture = cpuCollector.collectCpuArchitecture(),
            measurementMethod = MeasurementMethod.API_35_NATIVE,
            apiLevel = apiLevelProvider.getApiLevel(),
        )
    }
}
