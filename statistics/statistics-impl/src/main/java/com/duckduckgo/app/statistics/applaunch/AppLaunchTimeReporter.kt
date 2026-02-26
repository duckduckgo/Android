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
import android.app.ActivityManager
import android.app.ApplicationStartInfo
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
 * On API 35+, also captures the system-reported TTID via [ApplicationStartInfo] and includes it
 * as [PARAM_SYSTEM_MS_BUCKET] for comparison. The pixel is held until both measurements arrive.
 *
 * Trampoline handling: if a new activity is created (or started) while we are already tracking a
 * different one that has not yet drawn, the previous one is treated as a trampoline and we switch
 * immediately. A [generation] counter makes any stale frame callback from the old activity a no-op.
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

    // Incremented whenever we abandon a previous attachment (trampoline detected or destroyed).
    // Each frame callback captures the generation at attach time; stale callbacks are ignored.
    private var generation = 0

    // Holds our ViewTreeObserver measurement until the system measurement is also ready.
    private var pendingMeasurement: PendingMeasurement? = null
    // System TTID from ApplicationStartInfo (null = not available or API < 35).
    private var systemTtidMs: Long? = null
    // True while we are still waiting for the ApplicationStartInfo callback on API 35+.
    private var awaitingSystemTtid = false
    // Ensures we register the ApplicationStartInfo listener at most once.
    private var systemListenerRegistered = false

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

    /**
     * Registers an [ApplicationStartInfo] completion listener and delivers the system TTID (in ms)
     * to the callback once available. On API < 35 the callback is invoked immediately with `null`.
     *
     * The callback always runs on the main thread so no additional synchronisation is needed.
     */
    @SuppressLint("NewApi")
    internal var registerSystemTtidListener: (Activity, (Long?) -> Unit) -> Unit = { activity, callback ->
        if (Build.VERSION.SDK_INT >= 35) {
            val am = activity.getSystemService(ActivityManager::class.java)
            am.addApplicationStartInfoCompletionListener(activity.mainExecutor) { startInfo ->
                val timestamps = startInfo.startupTimestamps
                val launchNs = timestamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH]
                val firstFrameNs = timestamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME]
                val systemTtid = if (launchNs != null && firstFrameNs != null) {
                    (firstFrameNs - launchNs) / 1_000_000L
                } else {
                    null
                }
                logcat(DEBUG) { "TTID: system START_TIMESTAMP_FIRST_FRAME → ${systemTtid}ms" }
                callback(systemTtid)
            }
        } else {
            callback(null)
        }
    }

    // ---------------------------------------------------------------------------
    // Warm/cold launch — activity created in a (possibly freshly started) process
    // ---------------------------------------------------------------------------

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        logcat(DEBUG) { "TTID: onActivityCreated [${activity::class.java.simpleName}] measured=$measured frameListenerAttached=$frameListenerAttached" }
        if (measured) return
        if (frameListenerAttached) {
            if (activity === listeningToActivity) return
            // A different activity is being created while we are still attached to another one.
            // The previous activity is a trampoline that launched this one without drawing first.
            logcat(DEBUG) { "TTID: [${listeningToActivity?.javaClass?.simpleName}] is a trampoline — switching to [${activity.javaClass.simpleName}]" }
            generation++
            frameListenerAttached = false
            listeningToActivity = null
        }
        maybeRegisterSystemListener(activity)
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
        if (measured) return
        if (frameListenerAttached) {
            if (activity === listeningToActivity) return
            // Same trampoline-in-background detection as in onActivityCreated.
            logcat(DEBUG) { "TTID: [${listeningToActivity?.javaClass?.simpleName}] is a trampoline — switching to [${activity.javaClass.simpleName}]" }
            generation++
            frameListenerAttached = false
            listeningToActivity = null
        }
        // onActivityCreated didn't fire, so this is a hot launch.
        maybeRegisterSystemListener(activity)
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
    // Trampoline detection (destruction path) — if the activity we attached to is
    // destroyed before drawing and no new activity has arrived yet, reset so the
    // next lifecycle callback can pick up measurement.
    // ---------------------------------------------------------------------------

    override fun onActivityDestroyed(activity: Activity) {
        if (!measured && activity === listeningToActivity) {
            logcat(DEBUG) { "TTID: [${activity::class.java.simpleName}] destroyed before first draw — resetting to attach to next activity" }
            frameListenerAttached = false
            listeningToActivity = null
            generation++ // invalidate any in-flight frame callback from this activity
        }
    }

    // ---------------------------------------------------------------------------
    // Internal helpers
    // ---------------------------------------------------------------------------

    private fun maybeRegisterSystemListener(activity: Activity) {
        if (systemListenerRegistered) return
        systemListenerRegistered = true
        awaitingSystemTtid = true
        registerSystemTtidListener(activity) { ttidMs ->
            systemTtidMs = ttidMs
            awaitingSystemTtid = false
            trySendPixel()
        }
    }

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
        val capturedGeneration = generation
        scheduleFirstFrame(activity.window.decorView, Runnable {
            storeMeasurement(startUptimeMs, temperature, scenario, capturedGeneration)
        })
    }

    private fun storeMeasurement(startUptimeMs: Long, temperature: String, scenario: String, capturedGeneration: Int) {
        // Discard stale callbacks from activities we have since abandoned (trampoline pattern).
        if (measured || capturedGeneration != generation) return
        val ttidMs = currentUptimeMs() - startUptimeMs
        logcat(DEBUG) { "TTID: our measurement=${ttidMs}ms [temperature=$temperature, scenario=$scenario] awaitingSystemTtid=$awaitingSystemTtid" }
        pendingMeasurement = PendingMeasurement(ttidMs, temperature, scenario)
        trySendPixel()
    }

    private fun trySendPixel() {
        if (awaitingSystemTtid) return
        val m = pendingMeasurement ?: return
        if (measured) return
        measured = true

        val params = mutableMapOf(
            PARAM_MS_BUCKET to bucketTtidMs(m.ttidMs),
            PARAM_TEMPERATURE to m.temperature,
            PARAM_SCENARIO to m.scenario,
        )
        systemTtidMs?.let { params[PARAM_SYSTEM_MS_BUCKET] = bucketTtidMs(it) }
        logcat { "TTID: firing pixel — ours=${m.ttidMs}ms system=${systemTtidMs}ms [temperature=${m.temperature}, scenario=${m.scenario}]" }

        pixel.fire(pixelName = PIXEL_NAME, parameters = params)
    }

    private fun bucketTtidMs(ms: Long): String = when {
        ms < 500 -> "<500ms"
        ms < 1_000 -> "500ms-1s"
        ms < 2_000 -> "1s-2s"
        ms < 3_000 -> "2s-3s"
        ms < 5_000 -> "3s-5s"
        else -> ">5s"
    }

    private data class PendingMeasurement(
        val ttidMs: Long,
        val temperature: String,
        val scenario: String,
    )

    companion object {
        private const val PIXEL_NAME = "m_ttid"
        private const val PARAM_MS_BUCKET = "ms_bucket"
        private const val PARAM_SYSTEM_MS_BUCKET = "system_ms_bucket"
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
