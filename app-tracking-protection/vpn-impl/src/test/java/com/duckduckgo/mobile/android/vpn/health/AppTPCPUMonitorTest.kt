/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.health

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.utils.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfigImpl
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.FakeToggleConfigDao
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.remote_config.VpnConfigTogglesDao
import com.duckduckgo.mobile.android.vpn.remote_config.VpnRemoteConfigDatabase
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlinx.coroutines.test.TestScope
import org.junit.After
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppTPCPUMonitorTest {
    private lateinit var cpuMonitor: AppTPCPUMonitor

    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    
    private lateinit var config: AppTpFeatureConfigImpl
    private lateinit var toggleDao: VpnConfigTogglesDao
    private lateinit var workManager: WorkManager

    private val mockDeviceShieldPixels: DeviceShieldPixels = mock()
    private val mockCPUUsageReader: CPUUsageReader = mock()
    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockVpnRemoteConfigDatabase: VpnRemoteConfigDatabase = mock()

    @Before
    fun setup() {
        toggleDao = FakeToggleConfigDao()
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        whenever(mockVpnRemoteConfigDatabase.vpnConfigTogglesDao()).thenReturn(toggleDao)

        config = AppTpFeatureConfigImpl(
            TestScope(),
            mockAppBuildConfig,
            mockVpnRemoteConfigDatabase,
            coroutineRule.testDispatcherProvider
        )

        val workManagerConfig = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .setWorkerFactory(testWorkerFactory())
            .build()

        WorkManagerTestInitHelper.initializeTestWorkManager(context, workManagerConfig)
        workManager = WorkManager.getInstance(context)

        cpuMonitor = AppTPCPUMonitor(workManager, config)
    }

    @After
    fun after() {
        workManager.cancelAllWork()
    }

    @Test
    fun whenConfigEnabledStartWorker() {
        assertStartWorker()

        verify(mockCPUUsageReader).readCPUUsage()
    }

    @Test
    fun whenConfigDisabledDontStartWorker() {
        assertWorkerNotRunning()

        config.setEnabled(AppTpSetting.CPUMonitoring, false)
        cpuMonitor.onVpnStarted(TestScope())

        assertWorkerNotRunning()

        verifyNoInteractions(mockCPUUsageReader)
    }

    @Test
    fun whenVPNStoppedStopWorker() {
        assertStartWorker()

        cpuMonitor.onVpnStopped(coroutineRule.testScope, SELF_STOP)
        assertWorkerNotRunning()
    }

    @Test
    fun whenCPUAbove30ThresholdSendAlert() {
        assertAlertSent(42.0, 30)
    }

    @Test
    fun whenCPUAbove20ThresholdSendAlert() {
        assertAlertSent(30.0, 20)
    }

    @Test
    fun whenCPUAbove10ThresholdSendAlert() {
        assertAlertSent(10.1, 10)
    }

    @Test
    fun whenCPUAbove5ThresholdSendAlert() {
        assertAlertSent(5.5, 5)
    }

    @Test
    fun whenCPUBelow5NoAlert() {
        whenever(mockCPUUsageReader.readCPUUsage()).thenReturn(2.0)

        assertStartWorker()

        verifyNoInteractions(mockDeviceShieldPixels)
    }

    private fun testWorkerFactory(): WorkerFactory {
        return object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters
            ): ListenableWorker {
                return CPUMonitorWorker(appContext, workerParameters).also {
                    it.deviceShieldPixels = mockDeviceShieldPixels
                    it.cpuUsageReader = mockCPUUsageReader
                    it.dispatcherProvider = coroutineRule.testDispatcherProvider
                }
            }
        }
    }

    private fun assertAlertSent(cpuValue: Double, expectedAlert: Int) {
        whenever(mockCPUUsageReader.readCPUUsage()).thenReturn(cpuValue)

        assertStartWorker()

        verify(mockDeviceShieldPixels).sendCPUUsageAlert(eq(expectedAlert))
        verifyNoMoreInteractions(mockDeviceShieldPixels)
    }

    private fun assertStartWorker() {
        assertWorkerNotRunning()
        config.setEnabled(AppTpSetting.CPUMonitoring, true)
        cpuMonitor.onVpnStarted(TestScope())
        assertWorkerRunning()
    }

    private fun assertWorkerRunning() {
        val scheduledWorkers = getScheduledWorkers()
        assertEquals(1, scheduledWorkers.size)
        assertEquals(WorkInfo.State.RUNNING, scheduledWorkers[0].state)
    }

    private fun assertWorkerNotRunning() {
        val scheduledWorkers = getScheduledWorkers()

        // Either no workers at all or one cancelled worker
        if (scheduledWorkers.isNotEmpty()) {
            assertEquals(1, scheduledWorkers.size)
            assertEquals(WorkInfo.State.CANCELLED, scheduledWorkers[0].state)
        }
    }

    private fun getScheduledWorkers(): List<WorkInfo> {
        return workManager
            .getWorkInfosForUniqueWork(AppTPCPUMonitor.APP_TRACKER_CPU_MONITOR_WORKER_TAG)
            .get()
    }
}
