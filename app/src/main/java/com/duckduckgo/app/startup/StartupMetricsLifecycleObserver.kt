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

package com.duckduckgo.app.startup

import android.annotation.SuppressLint
import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.RequiresApi
import com.duckduckgo.app.startup.metrics.MemoryCollector
import com.duckduckgo.app.startup.metrics.ProcessTimeProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import logcat.LogPriority
import logcat.logcat
import javax.inject.Inject

/**
 * Lifecycle observer that automatically collects and emits startup metrics on API 35+.
 * Metrics are collected once per launch when the first activity is paused.
 */
@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class StartupMetricsLifecycleObserver @Inject constructor(
    private val context: Context,
    private val buildConfig: AppBuildConfig,
    private val memoryCollector: MemoryCollector,
    private val processTimeProvider: ProcessTimeProvider,
    private val pixel: Pixel,
    private val startupMetricsFeature: StartupMetricsFeature,
    private val samplingDecider: SamplingDecider,
) : ActivityLifecycleCallbacks {
    // Guard to ensure we only collect and send metrics once per app launch.
    private var hasCollectedThisLaunch = false

    // Guard to ensure we only measure TTID once per launch, even if multiple activities are drawn.
    private var measured = false

    // Guard to ensure we only attach the frame listener to one activity at a time.
    private var frameListenerAttached = false

    // Count of started activities to detect when the app goes to background.
    private var startedActivityCount = 0

    // The activity we are currently listening to for the first frame.
    // This is needed to handle the case where a trampoline activity launches the main activity without drawing first
    private var listeningToActivity: Activity? = null

    // Store the manually measured TTID to compare with the system measurement when available.
    private var manualTtidMs: Long? = null

    // Schedules the provided action to run right after the next frame is drawn on the given decorView.
    // Extracted to a variable for easier testing.
    internal var scheduleFirstFrame: (View, Runnable) -> Unit = { decorView, action ->
        logcat(LogPriority.DEBUG) { "TTID: attaching OnDrawListener to decorView" }
        decorView.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                private var invoked = false

                override fun onDraw() {
                    if (invoked) return
                    invoked = true
                    logcat(LogPriority.DEBUG) { "TTID: onDraw fired, scheduling frame commit callback" }
                    // Remove the listener on the next looper pass to avoid mutating the
                    // ViewTreeObserver from within its own callback.
                    decorView.post { decorView.viewTreeObserver.removeOnDrawListener(this) }
                    // postAtFrontOfQueue fires after the current frame traversal completes,
                    // i.e. once the frame has been handed off to the render thread / swap chain.
                    Handler(Looper.getMainLooper()).postAtFrontOfQueue(action)
                }
            },
        )
    }

    override fun onActivityCreated(
        activity: Activity,
        savedInstanceState: Bundle?,
    ) {
        if (hasCollectedThisLaunch) return
        if (frameListenerAttached) {
            if (activity === listeningToActivity) return
            // A different activity is being created while we are still attached to another one.
            // The previous activity is a trampoline that launched this one without drawing first.
            logcat { "TTID: [${listeningToActivity?.javaClass?.simpleName}] is a trampoline — switching to [${activity.javaClass.simpleName}]" }
            frameListenerAttached = false
        }
        attachFirstFrameListener(
            activity = activity,
            startUptimeMs = resolveStartTime(),
        )
        if (startedActivityCount >= 1) return
        listeningToActivity = activity
    }

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
    }

    override fun onActivityResumed(activity: Activity) {
    }

    @SuppressLint("NewApi")
    override fun onActivityPaused(activity: Activity) {
        if (!hasCollectedThisLaunch && listeningToActivity == null) {
            logcat { "TTID: First activity paused, collecting startup metrics" }
            val systemMeasurement = if (buildConfig.sdkInt >= 35) {
                collectStartupMetrics()
            } else {
                null
            }
            hasCollectedThisLaunch = true
            sendPixel(systemMeasurement)
        }
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
        if (startedActivityCount == 0) {
            logcat { "TTID: App backgrounded, resetting collection flag for next launch" }
            hasCollectedThisLaunch = false
        }
    }

    override fun onActivityDestroyed(activity: Activity) {
        if (!hasCollectedThisLaunch && listeningToActivity == activity) {
            logcat { "TTID: Listening activity destroyed before pause, resetting collection flag" }
            listeningToActivity = null
        }
    }

    override fun onActivitySaveInstanceState(
        activity: Activity,
        outState: Bundle,
    ) {
    }

    private fun resolveStartTime(): Long {
        val processStartMs = processTimeProvider.startupTimeMs()
        val processAge = processTimeProvider.currentUptimeMs() - processStartMs
        val startTime = when {
            // Cold launch: process started recently for this launch.
            processAge in 0..COLD_LAUNCH_MAX_AGE_MS -> processStartMs
            // Warm launch or bogus device value: use the lifecycle timestamp instead.
            else -> processTimeProvider.currentUptimeMs()
        }
        logcat(LogPriority.DEBUG) { "TTID: resolveStartTime processAge=${processAge}ms startTime=$startTime" }
        return startTime
    }

    private fun attachFirstFrameListener(
        activity: Activity,
        startUptimeMs: Long,
    ) {
        frameListenerAttached = true
        scheduleFirstFrame(
            activity.window.decorView,
            Runnable {
                collectManualStartupTime(startUptimeMs)
                    ?.let { manualTtidMs = it }
            },
        )
    }

    private fun collectManualStartupTime(startUptimeMs: Long): Long? {
        logcat { "TTID: Collecting manual startup time [measure=$measured]" }
        if (measured) return null
        measured = true
        return processTimeProvider.currentUptimeMs() - startUptimeMs
    }

    @RequiresApi(35)
    private fun collectStartupMetrics(): Measurement? {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val startInfo = activityManager.getHistoricalProcessStartReasons(1).firstOrNull()
        if (startInfo == null) {
            logcat { "TTID: No ApplicationStartInfo available from ActivityManager" }
            return null
        }
        return systemMeasurement(startInfo)
    }

    @RequiresApi(35)
    private fun systemMeasurement(startInfo: ApplicationStartInfo): Measurement? {
        val launchTimeMs = startInfo.startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH]
            ?.let { it / 1_000_000L }
        if (launchTimeMs == null) {
            logcat { "TTID: No launch timestamp in ApplicationStartInfo" }
            return null
        } else {
            logcat { "TTID: Launch timestamp from ApplicationStartInfo: $launchTimeMs ms" }
        }

        val ttidDurationMs = startInfo.startupTimestamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]
            ?.let { (it / 1_000_000L) - launchTimeMs }
        if (ttidDurationMs == null || ttidDurationMs < 0) {
            logcat { "TTID: Invalid TTID duration: $ttidDurationMs" }
            return null
        }

        // Map startup type
        val startupType = when (startInfo.startType) {
            ApplicationStartInfo.START_TYPE_COLD -> StartupType.COLD
            ApplicationStartInfo.START_TYPE_WARM -> StartupType.WARM
            ApplicationStartInfo.START_TYPE_HOT -> StartupType.HOT
            else -> StartupType.UNKNOWN
        }

        logcat { "TTID: type=$startupType, ttid=$ttidDurationMs ms" }
        return Measurement(ttidMs = ttidDurationMs, startup = startupType)
    }

    private fun sendPixel(systemMeasurement: Measurement?) {
        if (manualTtidMs == null && systemMeasurement == null) {
            logcat { "TTID: No valid startup time measurement available, skipping pixel" }
            return
        }

        if (!startupMetricsFeature.self().isEnabled()) {
            logcat { "TTID: Startup metrics feature is disabled" }
            return
        }

        if (!samplingDecider.shouldSample()) {
            logcat { "TTID: Startup metrics not sampled" }
            return
        }

        logcat { "TTID: firing pixel — ours=${manualTtidMs}ms, system=${systemMeasurement?.ttidMs}ms" }

        val parameters = buildMap {
            if (systemMeasurement != null) {
                put(StartupMetricsPixelParameters.STARTUP_TYPE, systemMeasurement.startup.name.lowercase())
                put(StartupMetricsPixelParameters.TTID_DURATION_MS, systemMeasurement.ttidMs.toString())
            }
            manualTtidMs?.let {
                put(StartupMetricsPixelParameters.TTID_MANUAL_DURATION_MS, manualTtidMs.toString())
                manualTtidMs = null
            }
            put(StartupMetricsPixelParameters.API_LEVEL, buildConfig.sdkInt.toString())

            memoryCollector.collectDeviceRamBucket()?.let {
                put(StartupMetricsPixelParameters.DEVICE_RAM_BUCKET, it)
            }
        }

        pixel.fire(
            pixel = StartupMetricsPixelName.APP_STARTUP_TIME,
            parameters = parameters,
            encodedParameters = emptyMap(),
            type = Pixel.PixelType.Count,
        )
    }

    private data class Measurement(
        val ttidMs: Long,
        val startup: StartupType,
    )

    companion object {
        // Process age threshold for distinguishing cold from warm/hot launches.
        // Also guards against the known device bug where Process.getStartUptimeMillis()
        // can return a value far in the past before Application.onCreate() is reached.
        private const val COLD_LAUNCH_MAX_AGE_MS = 10_000L
    }
}
