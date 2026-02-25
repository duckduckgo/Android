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

package com.duckduckgo.app.statistics.applaunch

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.browser.api.ActivityLifecycleCallbacks
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesMultibinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import logcat.LogPriority.DEBUG
import logcat.logcat

/**
 * Measures Time to Initial Display (TTID) — from the moment the user triggered the launch to the
 * first committed frame — and reports it as a pixel, bucketed by duration and tagged with the
 * launch temperature and scenario.
 *
 * Start time:
 *  - Cold  (process freshly started): [Process.getStartRequestedUptimeMillis] (API 33+) or
 *    [Process.getStartUptimeMillis], with a sanity cap to guard against buggy device values.
 *  - Warm  (new activity, existing process): captured at [onActivityCreated].
 *  - Hot   (stopped activity restarted): captured at [onActivityStarted].
 *
 * End time: first frame submitted to the swap chain, via [Handler.postAtFrontOfQueue] triggered
 * from a [ViewTreeObserver.OnDrawListener].
 *
 * Fires exactly once per process lifetime.
 */
@ContributesMultibinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class AppLaunchTimeReporter @Inject constructor(
    private val pixel: Pixel,
) : ActivityLifecycleCallbacks {

    private var measured = false
    private var frameListenerAttached = false
    private var warmHotStartUptimeMs: Long? = null
    private var listeningToActivity: Activity? = null

    init {
        logcat(DEBUG) { "TTID: AppLaunchTimeReporter instantiated" }
    }

    // Testing seams — replaced in unit tests to eliminate static Android API dependencies.
    @SuppressLint("NewApi")
    internal var processStartUptimeMs: () -> Long = {
        if (Build.VERSION.SDK_INT >= 33) Process.getStartRequestedUptimeMillis()
        else Process.getStartUptimeMillis()
    }
    internal var currentUptimeMs: () -> Long = SystemClock::uptimeMillis
    internal var scheduleFirstFrame: (View, Runnable) -> Unit = { decorView, action ->
        logcat(DEBUG) { "TTID: attaching OnDrawListener to decorView" }
        decorView.viewTreeObserver.addOnDrawListener(object : ViewTreeObserver.OnDrawListener {
            private var invoked = false

            override fun onDraw() {
                if (invoked) return
                invoked = true
                logcat(DEBUG) { "TTID: onDraw fired, scheduling frame commit callback" }
                // Remove the listener on the next looper pass to avoid mutating the
                // ViewTreeObserver from within its own callback.
                decorView.post { decorView.viewTreeObserver.removeOnDrawListener(this) }
                // postAtFrontOfQueue fires after the current frame traversal completes,
                // i.e. once the frame has been handed off to the render thread / swap chain.
                Handler(Looper.getMainLooper()).postAtFrontOfQueue(action)
            }
        })
    }

    // ---------------------------------------------------------------------------
    // Warm launch — activity created in an already-running process
    // ---------------------------------------------------------------------------

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        logcat(DEBUG) { "TTID: onActivityCreated [${activity::class.java.simpleName}] measured=$measured frameListenerAttached=$frameListenerAttached" }
        if (measured || frameListenerAttached) return
        captureWarmHotStartIfNeeded()
        frameListenerAttached = true
        listeningToActivity = activity
        attachFirstFrameListener(
            activity = activity,
            startUptimeMs = resolveStartTime(),
            temperature = resolveTemperature(),
            scenario = resolveScenario(activity, savedInstanceState),
        )
    }

    // ---------------------------------------------------------------------------
    // Hot launch — stopped activity brought back to foreground without re-creation
    // ---------------------------------------------------------------------------

    override fun onActivityStarted(activity: Activity) {
        logcat(DEBUG) { "TTID: onActivityStarted [${activity::class.java.simpleName}] measured=$measured frameListenerAttached=$frameListenerAttached" }
        if (measured || frameListenerAttached) return
        // onActivityCreated didn't fire, so this is a hot launch.
        captureWarmHotStartIfNeeded()
        frameListenerAttached = true
        listeningToActivity = activity
        attachFirstFrameListener(
            activity = activity,
            startUptimeMs = warmHotStartUptimeMs ?: currentUptimeMs(),
            temperature = TEMP_HOT,
            scenario = resolveScenario(activity, savedInstanceState = null),
        )
    }

    // ---------------------------------------------------------------------------
    // Trampoline detection — if the activity we attached to is destroyed before
    // drawing (e.g. a bridge/redirect activity), reset and try the next one.
    // ---------------------------------------------------------------------------

    override fun onActivityDestroyed(activity: Activity) {
        if (!measured && activity === listeningToActivity) {
            logcat(DEBUG) { "TTID: [${activity::class.java.simpleName}] destroyed before first draw — resetting to attach to next activity" }
            frameListenerAttached = false
            listeningToActivity = null
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun captureWarmHotStartIfNeeded() {
        if (warmHotStartUptimeMs != null) return
        val processAge = currentUptimeMs() - processStartUptimeMs()
        logcat(DEBUG) { "TTID: process age=${processAge}ms (threshold=${COLD_LAUNCH_MAX_AGE_MS}ms)" }
        // Only meaningful for warm/hot launches where the process has been alive long enough
        // that processStartUptimeMs() represents a previous, unrelated launch.
        if (processAge > COLD_LAUNCH_MAX_AGE_MS) {
            warmHotStartUptimeMs = currentUptimeMs()
        }
    }

    private fun resolveStartTime(): Long {
        val processStartMs = processStartUptimeMs()
        val processAge = currentUptimeMs() - processStartMs
        val startTime = when {
            // Cold launch: process started recently for this launch.
            processAge in 0..COLD_LAUNCH_MAX_AGE_MS -> processStartMs
            // Warm launch or bogus device value: use the lifecycle timestamp instead.
            else -> warmHotStartUptimeMs ?: currentUptimeMs()
        }
        logcat(DEBUG) { "TTID: resolveStartTime processAge=${processAge}ms startTime=$startTime warmHotStartUptimeMs=$warmHotStartUptimeMs" }
        return startTime
    }

    private fun resolveTemperature(): String {
        return if (currentUptimeMs() - processStartUptimeMs() <= COLD_LAUNCH_MAX_AGE_MS) TEMP_COLD else TEMP_WARM
    }

    private fun resolveScenario(activity: Activity, savedInstanceState: Bundle?): String {
        if (savedInstanceState != null) return SCENARIO_RECENTS
        val intent = activity.intent ?: return SCENARIO_OTHER
        return when {
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) -> SCENARIO_LAUNCHER
            intent.action == Intent.ACTION_MAIN -> SCENARIO_LAUNCHER
            else -> SCENARIO_OTHER
        }
    }

    private fun attachFirstFrameListener(
        activity: Activity,
        startUptimeMs: Long,
        temperature: String,
        scenario: String,
    ) {
        scheduleFirstFrame(activity.window.decorView, Runnable {
            reportTtid(startUptimeMs, temperature, scenario)
        })
    }

    private fun reportTtid(startUptimeMs: Long, temperature: String, scenario: String) {
        if (measured) return
        measured = true
        val ttidMs = currentUptimeMs() - startUptimeMs
        logcat { "TTID: ${ttidMs}ms [temperature=$temperature, scenario=$scenario]" }
        pixel.fire(
            pixelName = PIXEL_NAME,
            parameters = mapOf(
                PARAM_MS_BUCKET to bucketTtidMs(ttidMs),
                PARAM_TEMPERATURE to temperature,
                PARAM_SCENARIO to scenario,
            ),
        )
    }

    private fun bucketTtidMs(ms: Long): String = when {
        ms < 500 -> "<500ms"
        ms < 1_000 -> "500ms-1s"
        ms < 2_000 -> "1s-2s"
        ms < 3_000 -> "2s-3s"
        ms < 5_000 -> "3s-5s"
        else -> ">5s"
    }

    companion object {
        private const val PIXEL_NAME = "m_ttid"
        private const val PARAM_MS_BUCKET = "ms_bucket"
        private const val PARAM_TEMPERATURE = "temperature"
        private const val PARAM_SCENARIO = "scenario"

        private const val TEMP_COLD = "cold"
        private const val TEMP_WARM = "warm"
        private const val TEMP_HOT = "hot"

        private const val SCENARIO_LAUNCHER = "launcher"
        private const val SCENARIO_RECENTS = "recents"
        private const val SCENARIO_OTHER = "other"

        // Process age threshold for distinguishing cold from warm/hot launches.
        // Also guards against the known device bug where Process.getStartUptimeMillis()
        // can return a value far in the past before Application.onCreate() is reached.
        private const val COLD_LAUNCH_MAX_AGE_MS = 10_000L
    }
}
