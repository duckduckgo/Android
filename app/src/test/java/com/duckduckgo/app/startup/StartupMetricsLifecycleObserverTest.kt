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
import android.view.View
import android.view.Window
import com.duckduckgo.app.startup.metrics.MemoryCollector
import com.duckduckgo.app.startup.metrics.ProcessTimeProvider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class StartupMetricsLifecycleObserverTest {

    private lateinit var context: Context
    private lateinit var apiLevelProvider: AppBuildConfig
    private lateinit var memoryCollector: MemoryCollector
    private lateinit var processTimeProvider: ProcessTimeProvider
    private lateinit var pixel: Pixel
    private val startupMetricsFeature = FakeFeatureToggleFactory.Companion.create(StartupMetricsFeature::class.java)
    private lateinit var samplingDecider: SamplingDecider
    private lateinit var activityManager: ActivityManager
    private lateinit var observer: StartupMetricsLifecycleObserver
    private var frameCallbacks = mutableListOf<Runnable>()

    @Before
    fun setup() {
        context = mock()
        apiLevelProvider = mock()
        memoryCollector = mock()
        processTimeProvider = mock()
        pixel = mock()
        samplingDecider = mock()
        activityManager = mock()

        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
        whenever(apiLevelProvider.sdkInt).thenReturn(35)
        whenever(samplingDecider.shouldSample()).thenReturn(true)
        whenever(processTimeProvider.currentUptimeMs()).thenReturn(10_000L)
        whenever(processTimeProvider.startupTimeMs()).thenReturn(1_000L)
        startupMetricsFeature.self().setRawStoredState(Toggle.State(true))

        observer = StartupMetricsLifecycleObserver(
            context = context,
            buildConfig = apiLevelProvider,
            memoryCollector = memoryCollector,
            processTimeProvider = processTimeProvider,
            pixel = pixel,
            startupMetricsFeature = startupMetricsFeature,
            samplingDecider = samplingDecider,
        )

        // Mock scheduleFirstFrame to capture frame callbacks
        observer.scheduleFirstFrame = { _, action ->
            frameCallbacks.add(action)
        }
    }

    @Test
    fun `when API level is below 35 then does not collect system metrics on pause`() {
        whenever(apiLevelProvider.sdkInt).thenReturn(34)
        val activity = createMockActivity()

        observer.onActivityPaused(activity)

        verify(activityManager, never()).getHistoricalProcessStartReasons(any())
    }

    @Test
    fun `when first activity paused then collects startup metrics`() {
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        observer.onActivityPaused(activity)

        verify(activityManager).getHistoricalProcessStartReasons(any())
    }

    @Test
    fun `when feature is disabled then does not fire pixel`() {
        startupMetricsFeature.self().setRawStoredState(Toggle.State(false))
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        observer.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when sampling returns false then does not fire pixel`() {
        whenever(samplingDecider.shouldSample()).thenReturn(false)
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        observer.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when startup info collected then fires pixel with correct parameters`() {
        val activity = createMockActivity()
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4gb")

        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        // Trigger manual TTID measurement
        observer.onActivityCreated(activity, null)
        observer.onActivityStarted(activity)
        triggerFirstFrame()
        observer.onActivityStopped(activity)
        observer.onActivityDestroyed(activity)

        // Now pause triggers pixel sending (listeningToActivity is now null)
        val activity2 = createMockActivity()
        observer.onActivityPaused(activity2)

        argumentCaptor<Map<String, String>>().apply {
            verify(pixel).fire(
                pixel = eq(StartupMetricsPixelName.APP_STARTUP_TIME),
                parameters = capture(),
                encodedParameters = any(),
                type = eq(Pixel.PixelType.Count),
            )

            val params = firstValue
            assertEquals("cold", params[StartupMetricsPixelParameters.STARTUP_TYPE])
            // Manual TTID: currentUptimeMs (10000) - startupTimeMs (1000) = 9000ms
            assertEquals("9000", params[StartupMetricsPixelParameters.TTID_MANUAL_DURATION_MS])
            // System TTID: 3000ms - 1000ms = 2000ms
            assertEquals("2000", params[StartupMetricsPixelParameters.TTID_DURATION_MS])
            assertEquals("35", params[StartupMetricsPixelParameters.API_LEVEL])
            assertEquals("4gb", params[StartupMetricsPixelParameters.DEVICE_RAM_BUCKET])
        }
    }

    @Test
    fun `when multiple activities paused then only collects once per process`() {
        val activity1 = createMockActivity()
        val activity2 = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        // Trigger manual measurement for first activity
        observer.onActivityCreated(activity1, null)
        observer.onActivityStarted(activity1)
        triggerFirstFrame()
        observer.onActivityStopped(activity1)
        observer.onActivityDestroyed(activity1)

        // First pause after destroy triggers collection
        observer.onActivityPaused(activity2)

        // Second activity pause should not trigger collection again
        val activity3 = createMockActivity()
        observer.onActivityPaused(activity3)

        verify(activityManager, times(1)).getHistoricalProcessStartReasons(any())
        verify(pixel, times(1)).fire(
            pixel = any<Pixel.PixelName>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `when launch timestamp missing then does not fire pixel`() {
        val activity1 = createMockActivity()
        val activity2 = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = null,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        // Trigger launch activity lifecyclees for first activity
        observer.onActivityCreated(activity1, null)
        observer.onActivityStarted(activity1)
        observer.onActivityStopped(activity1)
        observer.onActivityDestroyed(activity1)

        // First pause after destroy trigger
        observer.onActivityPaused(activity2)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when first frame timestamp missing then does not fire pixel`() {
        val activity1 = createMockActivity()
        val activity2 = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = null,
        )
        setupActivityManagerWithStartInfo(startInfo)

        // Trigger launch activity lifecyclees for first activity
        observer.onActivityCreated(activity1, null)
        observer.onActivityStarted(activity1)
        observer.onActivityStopped(activity1)
        observer.onActivityDestroyed(activity1)

        // First pause after destroy trigger
        observer.onActivityPaused(activity2)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when API level below 35 then fires pixel with manual TTID only`() {
        whenever(apiLevelProvider.sdkInt).thenReturn(34)
        val activity = createMockActivity()
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("8gb")

        observer.onActivityCreated(activity, null)
        observer.onActivityStarted(activity)
        triggerFirstFrame()
        observer.onActivityPaused(activity)
        observer.onActivityStopped(activity)
        observer.onActivityDestroyed(activity)

        // Now a second activity is paused, which triggers pixel sending
        val activity2 = createMockActivity()
        observer.onActivityPaused(activity2)

        argumentCaptor<Map<String, String>>().apply {
            verify(pixel).fire(
                pixel = eq(StartupMetricsPixelName.APP_STARTUP_TIME),
                parameters = capture(),
                encodedParameters = any(),
                type = eq(Pixel.PixelType.Count),
            )

            val params = firstValue
            // Manual TTID: currentUptimeMs (10000) - startupTimeMs (1000) = 9000ms
            assertEquals("9000", params[StartupMetricsPixelParameters.TTID_MANUAL_DURATION_MS])
            // No system TTID on API < 35
            assertNull(params[StartupMetricsPixelParameters.TTID_DURATION_MS])
            assertEquals("34", params[StartupMetricsPixelParameters.API_LEVEL])
            assertEquals("8gb", params[StartupMetricsPixelParameters.DEVICE_RAM_BUCKET])
        }
    }

    @Test
    fun `when trampoline activity detected then switches to real activity`() {
        val trampolineActivity = createMockActivity()
        val realActivity = createMockActivity()

        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        // First activity created but doesn't draw immediately (trampoline)
        observer.onActivityCreated(trampolineActivity, null)
        observer.onActivityStarted(trampolineActivity)
        // No triggerFirstFrame() call - simulates immediate start of real activity

        // Second activity created and draws (real activity)
        observer.onActivityCreated(realActivity, null)
        observer.onActivityStarted(realActivity)
        triggerFirstFrame()

        // Need to destroy trampoline to reset listeningToActivity
        observer.onActivityStopped(trampolineActivity)
        observer.onActivityDestroyed(trampolineActivity)

        // Now pause on real activity triggers collection with real activity as source
        observer.onActivityPaused(realActivity)
        observer.onActivityStopped(realActivity)
        observer.onActivityDestroyed(realActivity)

        // Should only fire pixel once with real activity's measurement
        verify(pixel, times(1)).fire(
            pixel = any<Pixel.PixelName>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    private fun createMockActivity(): Activity {
        val activity = mock<Activity>()
        val window = mock<Window>()
        val decorView = mock<View>()
        whenever(activity.window).thenReturn(window)
        whenever(window.decorView).thenReturn(decorView)
        return activity
    }

    private fun triggerFirstFrame() {
        frameCallbacks.forEach { it.run() }
        frameCallbacks.clear()
    }

    private fun createMockApplicationStartInfo(
        startType: Int,
        launchTimestamp: Long?,
        firstFrameTimestamp: Long?,
    ): ApplicationStartInfo {
        val startInfo = mock<ApplicationStartInfo>()
        whenever(startInfo.startType).thenReturn(startType)

        val timestamps = mutableMapOf<Int, Long>()
        launchTimestamp?.let { timestamps[ApplicationStartInfo.START_TIMESTAMP_LAUNCH] = it }
        firstFrameTimestamp?.let { timestamps[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME] = it }

        whenever(startInfo.startupTimestamps).thenReturn(timestamps)
        return startInfo
    }

    private fun setupActivityManagerWithStartInfo(startInfo: ApplicationStartInfo) {
        whenever(activityManager.getHistoricalProcessStartReasons(any()))
            .thenReturn(listOf(startInfo))
    }
}
