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

package com.duckduckgo.app.startup_metrics.impl.lifecycle

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.lifecycle.MainProcessLifecycleObserver
import com.duckduckgo.app.startup_metrics.impl.StartupType
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.feature.StartupMetricsFeature
import com.duckduckgo.app.startup_metrics.impl.metrics.CpuCollector
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
import com.duckduckgo.app.startup_metrics.impl.metrics.MemoryCollector
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelName
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelParameters
import com.duckduckgo.app.startup_metrics.impl.sampling.SamplingDecider
import com.duckduckgo.app.startup_metrics.impl.store.StartupMetricsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import logcat.logcat
import javax.inject.Inject

/**
 * Lifecycle observer that automatically collects and emits startup metrics on API 35+.
 * Metrics are collected once per launch when the first activity is paused.
 */
@ContributesMultibinding(AppScope::class)
class StartupMetricsLifecycleObserver @Inject constructor(
    private val context: Context,
    private val apiLevelProvider: ApiLevelProvider,
    private val dataStore: StartupMetricsDataStore,
    private val memoryCollector: MemoryCollector,
    private val cpuCollector: CpuCollector,
    private val pixel: Pixel,
    private val startupMetricsFeature: StartupMetricsFeature,
    private val samplingDecider: SamplingDecider,
) : MainProcessLifecycleObserver {

    @Volatile
    private var hasCollectedThisLaunch = false

    @Volatile
    private var startedActivityCount = 0

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityStarted(activity: Activity) {
            startedActivityCount++
        }

        @SuppressLint("NewApi")
        override fun onActivityPaused(activity: Activity) {
            if (!hasCollectedThisLaunch) {
                logcat { "First activity paused, collecting startup metrics" }
                hasCollectedThisLaunch = true
                collectAndEmitStartupMetrics()
            }
        }

        override fun onActivityStopped(activity: Activity) {
            startedActivityCount--
            if (startedActivityCount == 0) {
                logcat { "App backgrounded, resetting collection flag for next launch" }
                hasCollectedThisLaunch = false
            }
        }

        override fun onActivityCreated(
            activity: Activity,
            savedInstanceState: Bundle?,
        ) {
        }

        override fun onActivityResumed(activity: Activity) {
        }

        override fun onActivitySaveInstanceState(
            activity: Activity,
            outState: Bundle,
        ) {
        }

        override fun onActivityDestroyed(activity: Activity) {
        }
    }

    override fun onCreate(owner: LifecycleOwner) {
        super.onCreate(owner)
        if (apiLevelProvider.getApiLevel() >= 35) {
            (context.applicationContext as? Application)
                ?.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        }
    }

    @RequiresApi(35)
    private fun collectAndEmitStartupMetrics() {
        // Check if already collected this launch
        val lastCollectedLaunchTimeMs = dataStore.getLastCollectedLaunchTime()

        try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            activityManager.addApplicationStartInfoCompletionListener(context.mainExecutor) { startInfo ->
                processStartupInfo(startInfo, lastCollectedLaunchTimeMs)
            }
        } catch (e: Exception) {
            logcat { "Error querying ApplicationStartInfo: $e" }
        }
    }

    @RequiresApi(35)
    private fun processStartupInfo(
        startInfo: ApplicationStartInfo,
        lastCollectedLaunchTimeMs: Long,
    ) {
        val launchTimeMs = startInfo.startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH]
            ?.let { it / 1_000_000L }
        if (launchTimeMs == null) {
            logcat { "No launch timestamp in ApplicationStartInfo" }
            return
        }

        // Check if this is a new launch (not already collected)
        if (launchTimeMs <= lastCollectedLaunchTimeMs) {
            logcat { "Startup metrics already collected for this launch: $launchTimeMs" }
            return
        }
        dataStore.setLastCollectedLaunchTime(launchTimeMs)

        // Check feature flag and sampling
        if (!startupMetricsFeature.self().isEnabled()) {
            logcat { "Startup metrics feature is disabled" }
            return
        }

        if (!samplingDecider.shouldSample()) {
            logcat { "Startup metrics not sampled" }
            return
        }

        // Extract TTID
        val ttidDurationMs = startInfo.startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]
            ?.let { (it / 1_000_000L) - launchTimeMs }
        if (ttidDurationMs == null || ttidDurationMs < 0) {
            logcat { "Invalid TTID duration: $ttidDurationMs" }
            return
        }

        // Map startup type
        val startupType = when (startInfo.startType) {
            ApplicationStartInfo.START_TYPE_COLD -> StartupType.COLD
            ApplicationStartInfo.START_TYPE_WARM -> StartupType.WARM
            ApplicationStartInfo.START_TYPE_HOT -> StartupType.HOT
            else -> StartupType.UNKNOWN
        }

        logcat { "Emitting startup metrics: type=$startupType, ttid=$ttidDurationMs ms" }

        // Fire pixel immediately
        val parameters = buildMap {
            put(StartupMetricsPixelParameters.STARTUP_TYPE, startupType.name.lowercase())
            put(StartupMetricsPixelParameters.TTID_DURATION_MS, ttidDurationMs.toString())
            put(StartupMetricsPixelParameters.MEASUREMENT_METHOD, MeasurementMethod.API_35_NATIVE.name.lowercase())
            put(StartupMetricsPixelParameters.API_LEVEL, apiLevelProvider.getApiLevel().toString())

            memoryCollector.collectDeviceRamBucket()?.let {
                put(StartupMetricsPixelParameters.DEVICE_RAM_BUCKET, it)
            }
            cpuCollector.collectCpuArchitecture()?.let {
                put(StartupMetricsPixelParameters.CPU_ARCHITECTURE, it)
            }
        }

        pixel.fire(
            pixel = StartupMetricsPixelName.APP_STARTUP_TIME,
            parameters = parameters,
            encodedParameters = emptyMap(),
            type = Pixel.PixelType.Count,
        )
    }
}
