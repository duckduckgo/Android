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

import android.annotation.SuppressLint
import com.duckduckgo.app.startup_metrics.impl.collectors.StartupCollectorProvider
import com.duckduckgo.app.startup_metrics.impl.feature.StartupMetricsFeature
import com.duckduckgo.app.startup_metrics.impl.lifecycle.StartupTypeDetectionLifecycleObserver
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelName
import com.duckduckgo.app.startup_metrics.impl.pixels.StartupMetricsPixelParameters
import com.duckduckgo.app.startup_metrics.impl.sampling.SamplingDecider
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle.State
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@SuppressLint("DenyListedApi")
class RealStartupMetricsReporterTest {
    private val startupMetricsFeature = FakeFeatureToggleFactory.create(StartupMetricsFeature::class.java)
    private lateinit var collectorProvider: StartupCollectorProvider
    private lateinit var startupTypeDetectionObserver: StartupTypeDetectionLifecycleObserver
    private lateinit var samplingDecider: SamplingDecider
    private lateinit var pixel: Pixel
    private lateinit var testScope: TestScope
    private lateinit var reporter: RealStartupMetricsReporter

    @Before
    fun setup() {
        collectorProvider = mock()
        startupTypeDetectionObserver = mock()
        samplingDecider = mock()
        pixel = mock()
        testScope = TestScope()

        startupMetricsFeature.self().setRawStoredState(State(true))
        whenever(samplingDecider.shouldSample()).thenReturn(true)
        whenever(startupTypeDetectionObserver.getDetectedStartupType()).thenReturn(StartupType.COLD)

        reporter = RealStartupMetricsReporter(
            startupMetricsFeature = startupMetricsFeature,
            collectorProvider = collectorProvider,
            startupTypeDetectionObserver = startupTypeDetectionObserver,
            samplingDecider = samplingDecider,
            pixel = pixel,
            appCoroutineScope = testScope,
        )
    }

    @Test
    fun `when feature is disabled then reportStartupComplete does nothing`() = runTest {
        startupMetricsFeature.self().setRawStoredState(State(false))

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        verify(collectorProvider, never()).collectStartupMetrics(any())
        verify(pixel, never()).fire(
            pixel = any<Pixel.PixelName>(),
            parameters = any(),
            encodedParameters = any(),
            type = any(),
        )
    }

    @Test
    fun `when COLD start detected by global observer then uses COLD type`() = runTest {
        whenever(startupTypeDetectionObserver.getDetectedStartupType()).thenReturn(StartupType.COLD)
        val mockEvent = createMockStartupMetricEvent(StartupType.COLD)
        whenever(collectorProvider.collectStartupMetrics(any())).thenReturn(mockEvent)

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        // Verify COLD start was used
        verify(startupTypeDetectionObserver).getDetectedStartupType()
        verify(collectorProvider).collectStartupMetrics(startupType = StartupType.COLD)
    }

    @Test
    fun `when WARM start detected by global observer then uses WARM type`() = runTest {
        whenever(startupTypeDetectionObserver.getDetectedStartupType()).thenReturn(StartupType.WARM)
        val mockEvent = createMockStartupMetricEvent(StartupType.WARM)
        whenever(collectorProvider.collectStartupMetrics(any())).thenReturn(mockEvent)

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        // Verify WARM start was used
        verify(startupTypeDetectionObserver).getDetectedStartupType()
        verify(collectorProvider).collectStartupMetrics(startupType = StartupType.WARM)
    }

    @Test
    fun `when HOT start detected by global observer then uses HOT type`() = runTest {
        whenever(startupTypeDetectionObserver.getDetectedStartupType()).thenReturn(StartupType.HOT)
        val mockEvent = createMockStartupMetricEvent(StartupType.HOT)
        whenever(collectorProvider.collectStartupMetrics(any())).thenReturn(mockEvent)

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        // Verify HOT start was used
        verify(startupTypeDetectionObserver).getDetectedStartupType()
        verify(collectorProvider).collectStartupMetrics(startupType = StartupType.HOT)
    }

    @Test
    fun `when COLD start then reportStartupComplete emits pixel with correct parameters`() = runTest {
        val mockEvent = createMockStartupMetricEvent(StartupType.COLD)
        whenever(collectorProvider.collectStartupMetrics(any())).thenReturn(mockEvent)

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        // Verify pixel emitted with correct parameters
        val pixelNameCaptor = argumentCaptor<Pixel.PixelName>()
        val parametersCaptor = argumentCaptor<Map<String, String>>()
        val encodedParametersCaptor = argumentCaptor<Map<String, String>>()
        val typeCaptor = argumentCaptor<Pixel.PixelType>()

        verify(pixel).fire(
            pixelNameCaptor.capture(),
            parametersCaptor.capture(),
            encodedParametersCaptor.capture(),
            typeCaptor.capture(),
        )

        assert(pixelNameCaptor.firstValue == StartupMetricsPixelName.APP_STARTUP_TIME)
        assert(typeCaptor.firstValue == Pixel.PixelType.Count)

        val parameters = parametersCaptor.firstValue
        assert(parameters[StartupMetricsPixelParameters.STARTUP_TYPE] == "cold")
        assert(parameters[StartupMetricsPixelParameters.TTFD_MANUAL_DURATION_MS] == "1000")
        assert(parameters[StartupMetricsPixelParameters.DEVICE_RAM_BUCKET] == "4GB")
        assert(parameters[StartupMetricsPixelParameters.MEASUREMENT_METHOD] == "legacy_manual")
        assert(parameters[StartupMetricsPixelParameters.API_LEVEL] == "34")
        assert(parameters[StartupMetricsPixelParameters.CPU_ARCHITECTURE] == null)
        assert(parameters[StartupMetricsPixelParameters.TTID_DURATION_MS] == null) // Legacy doesn't have TTID
        assert(parameters[StartupMetricsPixelParameters.TTFD_DURATION_MS] == null) // Legacy uses manual
    }

    @Test
    fun `when CPU data present then reportStartupComplete emits pixel with CPU data`() = runTest {
        val mockEvent = StartupMetricEvent(
            startupType = StartupType.WARM,
            ttidDurationMs = 300L,
            ttfdDurationMs = 750L,
            deviceRamBucket = "2GB",
            cpuArchitecture = "arm64-v8a",
            measurementMethod = MeasurementMethod.API_35_NATIVE,
            apiLevel = 35,
        )
        whenever(collectorProvider.collectStartupMetrics(any())).thenReturn(mockEvent)

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        // Verify pixel emitted with CPU parameter
        val parametersCaptor = argumentCaptor<Map<String, String>>()
        verify(pixel).fire(
            any<Pixel.PixelName>(),
            parametersCaptor.capture(),
            any(),
            any(),
        )

        val parameters = parametersCaptor.firstValue
        assert(parameters[StartupMetricsPixelParameters.STARTUP_TYPE] == "warm")
        assert(parameters[StartupMetricsPixelParameters.TTID_DURATION_MS] == "300")
        assert(parameters[StartupMetricsPixelParameters.TTFD_DURATION_MS] == "750")
        assert(parameters[StartupMetricsPixelParameters.DEVICE_RAM_BUCKET] == "2GB")
        assert(parameters[StartupMetricsPixelParameters.CPU_ARCHITECTURE] == "arm64-v8a")
        assert(parameters[StartupMetricsPixelParameters.MEASUREMENT_METHOD] == "api_35_native")
        assert(parameters[StartupMetricsPixelParameters.API_LEVEL] == "35")
        assert(parameters[StartupMetricsPixelParameters.TTFD_MANUAL_DURATION_MS] == null) // API 35+ uses native TTFD
    }

    @Test
    fun `when sampling disabled then reportStartupComplete does not emit pixel`() = runTest {
        whenever(samplingDecider.shouldSample()).thenReturn(false)

        reporter.reportStartupComplete()
        testScope.testScheduler.advanceUntilIdle()

        // Verify sampling was checked but no metrics collected
        verify(samplingDecider).shouldSample()
        verify(collectorProvider, never()).collectStartupMetrics(any())
        verify(pixel, never()).fire(
            any<Pixel.PixelName>(),
            any(),
            any(),
            any(),
        )
    }

    private fun createMockStartupMetricEvent(type: StartupType) = StartupMetricEvent(
        startupType = type,
        ttidDurationMs = null, // Legacy doesn't support TTID
        ttfdDurationMs = 1000L,
        deviceRamBucket = "4GB",
        cpuArchitecture = null,
        measurementMethod = MeasurementMethod.LEGACY_MANUAL,
        apiLevel = 34,
    )
}
