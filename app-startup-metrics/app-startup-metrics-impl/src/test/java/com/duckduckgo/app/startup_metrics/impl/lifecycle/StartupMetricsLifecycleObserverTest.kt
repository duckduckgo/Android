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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.feature.StartupMetricsFeature
import com.duckduckgo.app.startup_metrics.impl.metrics.MemoryCollector
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelName
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelParameters
import com.duckduckgo.app.startup_metrics.impl.sampling.SamplingDecider
import com.duckduckgo.app.startup_metrics.impl.store.StartupMetricsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
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
import java.util.function.Consumer

@OptIn(ExperimentalCoroutinesApi::class)
@SuppressLint("DenyListedApi")
class StartupMetricsLifecycleObserverTest {

    private lateinit var context: Context
    private lateinit var application: Application
    private lateinit var apiLevelProvider: ApiLevelProvider
    private lateinit var dataStore: StartupMetricsDataStore
    private lateinit var memoryCollector: MemoryCollector
    private lateinit var pixel: Pixel
    private val startupMetricsFeature = FakeFeatureToggleFactory.create(StartupMetricsFeature::class.java)
    private lateinit var samplingDecider: SamplingDecider
    private lateinit var dispatcherProvider: DispatcherProvider
    private lateinit var testDispatcher: CoroutineDispatcher
    private lateinit var activityManager: ActivityManager
    private lateinit var lifecycleOwner: LifecycleOwner
    private lateinit var lifecycle: Lifecycle
    private lateinit var observer: StartupMetricsLifecycleObserver
    private lateinit var capturedCallbacks: Application.ActivityLifecycleCallbacks

    @Before
    fun setup() {
        context = mock()
        application = mock()
        apiLevelProvider = mock()
        dataStore = mock()
        memoryCollector = mock()
        pixel = mock()
        samplingDecider = mock()
        dispatcherProvider = mock()
        testDispatcher = UnconfinedTestDispatcher()
        activityManager = mock()
        lifecycleOwner = mock()
        lifecycle = mock()

        whenever(context.applicationContext).thenReturn(application)
        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
        whenever(lifecycleOwner.lifecycle).thenReturn(lifecycle)
        whenever(apiLevelProvider.getApiLevel()).thenReturn(35)
        whenever(dataStore.getLastCollectedLaunchTime()).thenReturn(0L)
        whenever(samplingDecider.shouldSample()).thenReturn(true)
        whenever(dispatcherProvider.io()).thenReturn(testDispatcher)
        startupMetricsFeature.self().setRawStoredState(Toggle.State(true))

        // Capture the registered callbacks
        argumentCaptor<Application.ActivityLifecycleCallbacks>().apply {
            whenever(application.registerActivityLifecycleCallbacks(capture())).then { }
        }

        observer = StartupMetricsLifecycleObserver(
            context = context,
            apiLevelProvider = apiLevelProvider,
            dataStore = dataStore,
            memoryCollector = memoryCollector,
            pixel = pixel,
            startupMetricsFeature = startupMetricsFeature,
            samplingDecider = samplingDecider,
            dispatcherProvider = dispatcherProvider,
        )
    }

    @Test
    fun `when API level is 35+ then registers activity lifecycle callbacks`() {
        whenever(apiLevelProvider.getApiLevel()).thenReturn(35)

        observer.onCreate(lifecycleOwner)

        verify(application).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when API level is below 35 then does not register callbacks`() {
        whenever(apiLevelProvider.getApiLevel()).thenReturn(34)

        observer.onCreate(lifecycleOwner)

        verify(application, never()).registerActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when onDestroy called then unregisters activity lifecycle callbacks`() {
        setupCallbackCapture()

        observer.onDestroy(lifecycleOwner)

        verify(application).unregisterActivityLifecycleCallbacks(capturedCallbacks)
    }

    @Test
    fun `when API level is below 35 then onDestroy does not unregister callbacks`() {
        whenever(apiLevelProvider.getApiLevel()).thenReturn(34)
        observer.onCreate(lifecycleOwner)

        observer.onDestroy(lifecycleOwner)

        verify(application, never()).unregisterActivityLifecycleCallbacks(any())
    }

    @Test
    fun `when first activity paused then collects startup metrics`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verify(activityManager).addApplicationStartInfoCompletionListener(any(), any())
    }

    @Test
    fun `when feature is disabled then does not fire pixel`() {
        setupCallbackCapture()
        startupMetricsFeature.self().setRawStoredState(Toggle.State(false))
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when sampling returns false then does not fire pixel`() {
        setupCallbackCapture()
        whenever(samplingDecider.shouldSample()).thenReturn(false)
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when startup info collected then fires pixel with correct parameters`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4gb")

        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        argumentCaptor<Map<String, String>>().apply {
            verify(pixel).fire(
                pixel = eq(StartupMetricsPixelName.APP_STARTUP_TIME),
                parameters = capture(),
                encodedParameters = any(),
                type = eq(Pixel.PixelType.Count),
            )

            val params = firstValue
            assertEquals("cold", params[StartupMetricsPixelParameters.STARTUP_TYPE])
            assertEquals("2000", params[StartupMetricsPixelParameters.TTID_DURATION_MS])
            assertEquals("35", params[StartupMetricsPixelParameters.API_LEVEL])
            assertEquals("4gb", params[StartupMetricsPixelParameters.DEVICE_RAM_BUCKET])
        }
    }

    @Test
    fun `when WARM start detected then fires pixel with warm type`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_WARM,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 2500L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        argumentCaptor<Map<String, String>>().apply {
            verify(pixel).fire(
                pixel = eq(StartupMetricsPixelName.APP_STARTUP_TIME),
                parameters = capture(),
                encodedParameters = any(),
                type = any(),
            )

            assertEquals("warm", firstValue[StartupMetricsPixelParameters.STARTUP_TYPE])
            assertEquals("1500", firstValue[StartupMetricsPixelParameters.TTID_DURATION_MS])
        }
    }

    @Test
    fun `when HOT start detected then fires pixel with hot type`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_HOT,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 1800L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        argumentCaptor<Map<String, String>>().apply {
            verify(pixel).fire(
                pixel = eq(StartupMetricsPixelName.APP_STARTUP_TIME),
                parameters = capture(),
                encodedParameters = any(),
                type = any(),
            )

            assertEquals("hot", firstValue[StartupMetricsPixelParameters.STARTUP_TYPE])
            assertEquals("800", firstValue[StartupMetricsPixelParameters.TTID_DURATION_MS])
        }
    }

    @Test
    fun `when launch timestamp is stale then does not fire pixel`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        whenever(dataStore.getLastCollectedLaunchTime()).thenReturn(2000L)

        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L, // Older than last collected
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when launch timestamp is same as last collected then does not fire pixel`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        whenever(dataStore.getLastCollectedLaunchTime()).thenReturn(1000L)

        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when multiple callbacks fire simultaneously then only processes first one`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        var storedLaunchTime = 0L

        // Mock dataStore to return the updated value after setLastCollectedLaunchTime
        whenever(dataStore.getLastCollectedLaunchTime()).thenAnswer { storedLaunchTime }
        whenever(dataStore.setLastCollectedLaunchTime(any())).thenAnswer { invocation ->
            storedLaunchTime = invocation.getArgument(0)
            null
        }

        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )

        // Setup ActivityManager to fire callback multiple times (simulating race condition)
        whenever(activityManager.addApplicationStartInfoCompletionListener(any(), any())).then { invocation ->
            val listener = invocation.getArgument<Consumer<ApplicationStartInfo>>(1)
            // Simulate 3 simultaneous callbacks
            listener.accept(startInfo)
            listener.accept(startInfo)
            listener.accept(startInfo)
            null
        }

        capturedCallbacks.onActivityPaused(activity)

        // Should only fire pixel once due to synchronized block
        verify(pixel, times(1)).fire(
            pixel = eq(StartupMetricsPixelName.APP_STARTUP_TIME),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
        // Should only store timestamp once
        verify(dataStore, times(1)).setLastCollectedLaunchTime(1000L)
    }

    @Test
    fun `when timestamps collected then stores launch timestamp`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 5000L * 1_000_000L,
            firstFrameTimestamp = 7000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verify(dataStore).setLastCollectedLaunchTime(5000L)
    }

    @Test
    fun `when multiple activities paused then only collects once`() {
        setupCallbackCapture()
        val activity1 = createMockActivity()
        val activity2 = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity1)
        capturedCallbacks.onActivityPaused(activity2)

        verify(activityManager, times(1)).addApplicationStartInfoCompletionListener(any(), any())
    }

    @Test
    fun `when app backgrounded then resets collection flag for next launch`() {
        setupCallbackCapture()
        val activity = createMockActivity()

        capturedCallbacks.onActivityStarted(activity)
        capturedCallbacks.onActivityStopped(activity)

        // Should be able to collect again on next pause
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_WARM,
            launchTimestamp = 2000L * 1_000_000L,
            firstFrameTimestamp = 4000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)
        capturedCallbacks.onActivityPaused(activity)

        verify(activityManager).addApplicationStartInfoCompletionListener(any(), any())
    }

    @Test
    fun `when launch timestamp missing then does not fire pixel`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = null,
            firstFrameTimestamp = 3000L * 1_000_000L,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    @Test
    fun `when first frame timestamp missing then does not fire pixel`() {
        setupCallbackCapture()
        val activity = createMockActivity()
        val startInfo = createMockApplicationStartInfo(
            startType = ApplicationStartInfo.START_TYPE_COLD,
            launchTimestamp = 1000L * 1_000_000L,
            firstFrameTimestamp = null,
        )
        setupActivityManagerWithStartInfo(startInfo)

        capturedCallbacks.onActivityPaused(activity)

        verifyNoInteractions(pixel)
    }

    private fun setupCallbackCapture() {
        observer.onCreate(lifecycleOwner)
        argumentCaptor<Application.ActivityLifecycleCallbacks>().apply {
            verify(application).registerActivityLifecycleCallbacks(capture())
            capturedCallbacks = firstValue
        }
    }

    private fun createMockActivity(): Activity {
        return mock()
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
        whenever(activityManager.addApplicationStartInfoCompletionListener(any(), any())).then { invocation ->
            val listener = invocation.getArgument<Consumer<ApplicationStartInfo>>(1)
            listener.accept(startInfo)
            null
        }
    }
}
