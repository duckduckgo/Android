package com.duckduckgo.app.startup_metrics.impl.collectors

import com.duckduckgo.app.startup_metrics.impl.StartupType
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.android.ProcessStartupTimeProvider
import com.duckduckgo.app.startup_metrics.impl.metrics.CpuCollector
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
import com.duckduckgo.app.startup_metrics.impl.metrics.MemoryCollector
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class LegacyStartupCollectorTest {
    private lateinit var memoryCollector: MemoryCollector
    private lateinit var cpuCollector: CpuCollector
    private lateinit var timeProvider: CurrentTimeProvider
    private lateinit var processStartupTimeProvider: ProcessStartupTimeProvider
    private lateinit var apiLevelProvider: ApiLevelProvider
    private lateinit var collector: LegacyStartupCollector

    @Before
    fun setup() {
        memoryCollector = mock()
        cpuCollector = mock()
        timeProvider = mock()
        processStartupTimeProvider = mock()
        apiLevelProvider = mock()

        // Default mock responses to prevent NullPointerException
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")
        whenever(timeProvider.currentTimeMillis()).thenReturn(10000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(12000L)
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(10000L)
        whenever(apiLevelProvider.getApiLevel()).thenReturn(30)

        collector = LegacyStartupCollector(
            memoryCollector,
            cpuCollector,
            timeProvider,
            processStartupTimeProvider,
            apiLevelProvider,
        )
    }

    @Test
    fun `when collectStartupMetrics called then returns complete event with all components`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(10000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(13000L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")

        val event = collector.collectStartupMetrics(StartupType.COLD)

        assertEquals(StartupType.COLD, event.startupType)
        assertNull(event.ttidDurationMs) // Legacy doesn't support TTID
        assertEquals(3000L, event.ttfdDurationMs)
        assertEquals("4GB", event.deviceRamBucket)
        assertEquals("arm64-v8a", event.cpuArchitecture)
        assertEquals(MeasurementMethod.LEGACY_MANUAL, event.measurementMethod)
        assertEquals(30, event.apiLevel)
    }

    @Test
    fun `when warm start scenario then handles correctly`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(14000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(14800L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("2GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("armeabi-v7a")

        val event = collector.collectStartupMetrics(StartupType.WARM)

        assertEquals(StartupType.WARM, event.startupType)
        assertEquals(800L, event.ttfdDurationMs)
        assertEquals("2GB", event.deviceRamBucket)
    }

    @Test
    fun `when hot start scenario then handles correctly`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(15000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(15075L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("6GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")

        val event = collector.collectStartupMetrics(StartupType.HOT)

        assertEquals(StartupType.HOT, event.startupType)
        assertEquals(75L, event.ttfdDurationMs)
        assertEquals("6GB", event.deviceRamBucket)
    }

    @Test
    fun `when collectStartupMetrics called then calls all collectors`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(8000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(10500L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")

        val event = collector.collectStartupMetrics(StartupType.COLD)

        // Verify all collectors called
        verify(memoryCollector).collectDeviceRamBucket()
        verify(cpuCollector).collectCpuArchitecture()
        verify(processStartupTimeProvider).getStartUptimeMillis()
        verify(timeProvider).uptimeMillis()
        assertEquals(2500L, event.ttfdDurationMs)
    }

    @Test
    fun `when negative duration detected then returns zero`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(15000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(10000L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")

        val event = collector.collectStartupMetrics(StartupType.COLD)

        assertEquals(0L, event.ttfdDurationMs)
    }

    @Test
    fun `when CPU collector unavailable then handles gracefully`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(8000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(9500L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn(null)

        val event = collector.collectStartupMetrics(StartupType.COLD)

        assertNull("CPU architecture should be null when unavailable", event.cpuArchitecture)
    }

    @Test
    fun `when dynamic baseline changes then uses updated baseline`() = runTest {
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(100L)
        whenever(timeProvider.uptimeMillis()).thenReturn(1200L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")

        val coldEvent = collector.collectStartupMetrics(StartupType.COLD)
        assertEquals(1100L, coldEvent.ttfdDurationMs)

        // Now baseline is reset to activity creation time for WARM start
        whenever(processStartupTimeProvider.getStartUptimeMillis()).thenReturn(5000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(5843L)

        val warmEvent = collector.collectStartupMetrics(StartupType.WARM)
        assertEquals(843L, warmEvent.ttfdDurationMs)
    }
}
