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

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.statistics.pixels.Pixel
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.robolectric.Robolectric

@RunWith(AndroidJUnit4::class)
class AppLaunchTimeReporterTest {

    private val pixel: Pixel = mock()
    private lateinit var reporter: AppLaunchTimeReporter

    // Fake time values — both start at 0; tests set them to whatever they need.
    private var fakeProcessStartUptimeMs = 0L
    private var fakeCurrentUptimeMs = 0L

    @Before
    fun setup() {
        reporter = AppLaunchTimeReporter(pixel).also {
            it.processStartUptimeMs = { fakeProcessStartUptimeMs }
            it.currentUptimeMs = { fakeCurrentUptimeMs }
            // Invoke the frame callback synchronously so tests don't need to drive a Looper.
            it.scheduleFirstFrame = { _, action -> action.run() }
        }
    }

    // ---------------------------------------------------------------------------
    // Scenario detection
    // ---------------------------------------------------------------------------

    @Test
    fun `when launched from launcher then scenario is launcher`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(scenario = "launcher")
    }

    @Test
    fun `when launched with ACTION_MAIN but no CATEGORY_LAUNCHER then scenario is launcher`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        reporter.onActivityCreated(activityWith(Intent(Intent.ACTION_MAIN)), savedInstanceState = null)
        verifyPixelFired(scenario = "launcher")
    }

    @Test
    fun `when launched with non-launcher intent then scenario is other`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        reporter.onActivityCreated(activityWith(Intent(Intent.ACTION_VIEW)), savedInstanceState = null)
        verifyPixelFired(scenario = "other")
    }

    @Test
    fun `when activity has saved instance state then scenario is recents`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = Bundle())
        verifyPixelFired(scenario = "recents")
    }

    @Test
    fun `when hot launch then scenario uses activity intent`() {
        givenWarmOrHotLaunch(nowMs = 15_000L)
        reporter.onActivityStarted(activityWith(launcherIntent()))
        verifyPixelFired(scenario = "launcher")
    }

    // ---------------------------------------------------------------------------
    // Temperature detection
    // ---------------------------------------------------------------------------

    @Test
    fun `when process started within threshold then temperature is cold`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 9_999L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(temperature = "cold")
    }

    @Test
    fun `when process started beyond threshold then temperature is warm`() {
        givenWarmOrHotLaunch(nowMs = 10_001L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(temperature = "warm")
    }

    @Test
    fun `when only onActivityStarted fires then temperature is hot`() {
        givenWarmOrHotLaunch(nowMs = 15_000L)
        reporter.onActivityStarted(activityWith(launcherIntent()))
        verifyPixelFired(temperature = "hot")
    }

    // ---------------------------------------------------------------------------
    // TTID bucketing (all cold launches so TTID = nowMs - processStartMs)
    // ---------------------------------------------------------------------------

    @Test
    fun `ttid under 500ms maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 499L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = "<500ms")
    }

    @Test
    fun `ttid 500ms maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = "500ms-1s")
    }

    @Test
    fun `ttid 999ms maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 999L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = "500ms-1s")
    }

    @Test
    fun `ttid 1s maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 1_000L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = "1s-2s")
    }

    @Test
    fun `ttid 2s maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 2_000L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = "2s-3s")
    }

    @Test
    fun `ttid 3s maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 3_000L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = "3s-5s")
    }

    @Test
    fun `ttid 5s or more maps to correct bucket`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 5_000L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFired(msBucket = ">5s")
    }

    // ---------------------------------------------------------------------------
    // Fires exactly once
    // ---------------------------------------------------------------------------

    @Test
    fun `when multiple activities are created pixel fires only once`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        verifyPixelFiredExactlyOnce()
    }

    @Test
    fun `when onActivityStarted follows onActivityCreated pixel fires only once`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)
        val activity = activityWith(launcherIntent())
        reporter.onActivityCreated(activity, savedInstanceState = null)
        reporter.onActivityStarted(activity)
        verifyPixelFiredExactlyOnce()
    }

    @Test
    fun `when no activity is created or started pixel does not fire`() {
        verifyNoInteractions(pixel)
    }

    // ---------------------------------------------------------------------------
    // Trampoline activity (e.g. LaunchBridgeActivity)
    // ---------------------------------------------------------------------------

    @Test
    fun `when trampoline activity is destroyed before drawing pixel fires for the next activity`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)

        val trampoline = activityWith(launcherIntent())
        val real = activityWith(launcherIntent())

        reporter.onActivityCreated(trampoline, savedInstanceState = null)
        // Trampoline finishes without drawing — scheduleFirstFrame never invokes the action.
        reporter.scheduleFirstFrame = { _, _ -> /* simulate no draw */ }
        reporter.onActivityDestroyed(trampoline)

        // Restore synchronous frame callback for the real activity.
        reporter.scheduleFirstFrame = { _, action -> action.run() }
        reporter.onActivityCreated(real, savedInstanceState = null)

        verifyPixelFiredExactlyOnce()
    }

    @Test
    fun `when trampoline activity draws before being destroyed pixel fires only once`() {
        givenColdLaunch(processStartMs = 0L, nowMs = 500L)

        val trampoline = activityWith(launcherIntent())
        reporter.onActivityCreated(trampoline, savedInstanceState = null)
        // onDraw fires (action.run() was called synchronously in setup) — pixel already sent.
        reporter.onActivityDestroyed(trampoline)

        // A second activity should not trigger another pixel.
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)

        verifyPixelFiredExactlyOnce()
    }

    // ---------------------------------------------------------------------------
    // Bogus process start time (known device bug)
    // ---------------------------------------------------------------------------

    @Test
    fun `when process start time is bogus falls back to lifecycle time and reports warm`() {
        // Device bug: process appears to have started 60s before onActivityCreated is reached.
        fakeProcessStartUptimeMs = 0L
        fakeCurrentUptimeMs = 60_000L
        reporter.onActivityCreated(activityWith(launcherIntent()), savedInstanceState = null)
        // Start time falls back to the lifecycle-captured warmHotStartUptimeMs (= nowMs),
        // so TTID ≈ 0 and temperature is "warm" (process age exceeds threshold).
        verifyPixelFired(temperature = "warm", msBucket = "<500ms")
    }

    // ---------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------

    private fun givenColdLaunch(processStartMs: Long, nowMs: Long) {
        // Cold = process age (nowMs - processStartMs) is within the 10s threshold.
        fakeProcessStartUptimeMs = processStartMs
        fakeCurrentUptimeMs = nowMs
    }

    private fun givenWarmOrHotLaunch(nowMs: Long) {
        // Warm/hot = process appears to have started more than 10s ago.
        fakeProcessStartUptimeMs = 0L
        fakeCurrentUptimeMs = nowMs
    }

    private fun launcherIntent(): Intent =
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    private fun activityWith(intent: Intent): Activity =
        Robolectric.buildActivity(TestActivity::class.java, intent).create().get()

    private fun verifyPixelFired(
        msBucket: String? = null,
        temperature: String? = null,
        scenario: String? = null,
    ) {
        verify(pixel).fire(
            pixelName = eq("m_ttid"),
            parameters = argThat {
                (msBucket == null || this["ms_bucket"] == msBucket) &&
                    (temperature == null || this["temperature"] == temperature) &&
                    (scenario == null || this["scenario"] == scenario)
            },
            encodedParameters = any(),
            type = any(),
        )
    }

    private fun verifyPixelFiredExactlyOnce() {
        verify(pixel, times(1)).fire(
            pixelName = any<String>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }
}

class TestActivity : Activity()
