package com.duckduckgo.app.startup_metrics.impl.collectors

import android.app.ActivityManager
import android.app.ApplicationStartInfo
import android.content.Context
import com.duckduckgo.app.startup_metrics.impl.StartupType
import com.duckduckgo.app.startup_metrics.impl.android.ApiLevelProvider
import com.duckduckgo.app.startup_metrics.impl.android.ProcessStartupTimeProvider
import com.duckduckgo.app.startup_metrics.impl.metrics.CpuCollector
import com.duckduckgo.app.startup_metrics.impl.metrics.MeasurementMethod
import com.duckduckgo.app.startup_metrics.impl.metrics.MemoryCollector
import com.duckduckgo.common.utils.CurrentTimeProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import java.util.concurrent.Executor
import java.util.function.Consumer

class Api35StartupCollectorTest {
    private lateinit var context: Context
    private lateinit var activityManager: ActivityManager
    private lateinit var memoryCollector: MemoryCollector
    private lateinit var cpuCollector: CpuCollector
    private lateinit var timeProvider: CurrentTimeProvider
    private lateinit var processStartupTimeProvider: ProcessStartupTimeProvider
    private lateinit var apiLevelProvider: ApiLevelProvider
    private lateinit var collector: Api35StartupCollector
    private lateinit var mainExecutor: Executor

    @Before
    fun setup() {
        context = mock()
        activityManager = mock()
        memoryCollector = mock()
        cpuCollector = mock()
        timeProvider = mock()
        processStartupTimeProvider = mock()
        apiLevelProvider = mock()

        // Create a direct executor for testing (runs callbacks immediately)
        mainExecutor = Executor { it.run() }

        whenever(context.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(activityManager)
        whenever(context.mainExecutor).thenReturn(mainExecutor)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("4GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("arm64-v8a")
        whenever(timeProvider.uptimeMillis()).thenReturn(12000L)
        whenever(apiLevelProvider.getApiLevel()).thenReturn(35)

        collector = Api35StartupCollector(context, memoryCollector, cpuCollector, timeProvider, apiLevelProvider)
    }

    @Test
    fun `when ApplicationStartInfo reports cold start then returns COLD with duration`() = runTest {
        setupCallbackMock(ApplicationStartInfo.START_TYPE_COLD, startTimeMs = 10000L, firstFrameTimeMs = 11000L)

        val event = collector.collectStartupMetrics(startupType = StartupType.COLD)!!

        assertEquals(StartupType.COLD, event.startupType)
        assertEquals(1000L, event.ttidDurationMs) // 11000 - 10000
        assertEquals(2000L, event.ttfdDurationMs) // 12000 - 10000
        assertEquals("4GB", event.deviceRamBucket)
        assertEquals("arm64-v8a", event.cpuArchitecture)
        assertEquals(MeasurementMethod.API_35_NATIVE, event.measurementMethod)
    }

    @Test
    fun `when ApplicationStartInfo reports WARM start then returns WARM`() = runTest {
        setupCallbackMock(ApplicationStartInfo.START_TYPE_WARM, startTimeMs = 10000L, firstFrameTimeMs = 11500L)

        val event = collector.collectStartupMetrics(startupType = StartupType.COLD)!!

        assertEquals(StartupType.WARM, event.startupType)
        assertEquals(1500L, event.ttidDurationMs) // 11500 - 10000
        assertEquals(2000L, event.ttfdDurationMs) // 12000 - 10000
    }

    @Test
    fun `when ApplicationStartInfo reports HOT start then returns HOT`() = runTest {
        setupCallbackMock(ApplicationStartInfo.START_TYPE_HOT, startTimeMs = 10000L, firstFrameTimeMs = 10500L)

        val event = collector.collectStartupMetrics(startupType = StartupType.COLD)!!

        assertEquals(StartupType.HOT, event.startupType)
        assertEquals(500L, event.ttidDurationMs) // 10500 - 10000
        assertEquals(2000L, event.ttfdDurationMs) // 12000 - 10000
    }

    @Test
    fun `when unrecognized start type then returns unknown type`() = runTest {
        setupCallbackMock(999, startTimeMs = 10000L, firstFrameTimeMs = 10200L)

        val event = collector.collectStartupMetrics(startupType = StartupType.COLD)!!

        assertEquals(StartupType.UNKNOWN, event.startupType)
        assertEquals(200L, event.ttidDurationMs)
        assertEquals(2000L, event.ttfdDurationMs)
    }

    @Test
    fun `when callback throws exception then returns null`() = runTest {
        whenever(activityManager.addApplicationStartInfoCompletionListener(any(), any()))
            .thenThrow(RuntimeException("Test exception"))

        val event = collector.collectStartupMetrics(startupType = StartupType.WARM)

        assertNull(event)
    }

    @Test
    fun `when collectStartupMetrics called then includes RAM and CPU metrics`() = runTest {
        setupCallbackMock(ApplicationStartInfo.START_TYPE_COLD, startTimeMs = 10000L)
        whenever(memoryCollector.collectDeviceRamBucket()).thenReturn("8GB")
        whenever(cpuCollector.collectCpuArchitecture()).thenReturn("armeabi-v7a")

        val event = collector.collectStartupMetrics(startupType = StartupType.COLD)!!

        assertEquals("8GB", event.deviceRamBucket)
        assertEquals("armeabi-v7a", event.cpuArchitecture)
    }

    @Test
    fun `when collector called then registers callback listener`() = runTest {
        setupCallbackMock(ApplicationStartInfo.START_TYPE_COLD, startTimeMs = 10000L)

        collector.collectStartupMetrics(startupType = StartupType.COLD)

        verify(activityManager).addApplicationStartInfoCompletionListener(eq(mainExecutor), any())
    }

    @Test
    fun `when duration is negative then returns zero`() = runTest {
        setupCallbackMock(ApplicationStartInfo.START_TYPE_COLD, startTimeMs = 15000L, firstFrameTimeMs = 14000L)
        whenever(timeProvider.uptimeMillis()).thenReturn(8000L)

        val event = collector.collectStartupMetrics(startupType = StartupType.COLD)!!

        assertEquals(0L, event.ttfdDurationMs) // 8000 - 15000 = -7000 → 0
        assertNull(event.ttidDurationMs) // 14000 - 15000 = -1000 → null
    }

    /**
     * Helper to setup the addApplicationStartInfoCompletionListener callback mock.
     * The mock will immediately invoke the callback with the specified start type and timestamp.
     *
     * @param startType The ApplicationStartInfo.START_TYPE_* constant
     * @param startTimeMs The startup timestamp in milliseconds (will be converted to nanoseconds)
     * @param firstFrameTimeMs The first frame timestamp in milliseconds (will be converted to nanoseconds)
     */
    private fun setupCallbackMock(startType: Int, startTimeMs: Long = 10000L, firstFrameTimeMs: Long? = null) {
        doAnswer { invocation ->
            val callback = invocation.getArgument<Consumer<ApplicationStartInfo>>(1)
            val startTimeNanos = startTimeMs * 1_000_000L
            val timestampMap = mutableMapOf(ApplicationStartInfo.START_TIMESTAMP_LAUNCH to startTimeNanos)
            firstFrameTimeMs?.let {
                timestampMap[ApplicationStartInfo.START_TIMESTAMP_FIRST_FRAME] = it * 1_000_000L
            }
            val startInfo = mock<ApplicationStartInfo> {
                on { this.startType } doReturn startType
                on { this.startupTimestamps } doReturn timestampMap
            }
            callback.accept(startInfo)
            null
        }.whenever(activityManager).addApplicationStartInfoCompletionListener(any(), any())
    }
}
