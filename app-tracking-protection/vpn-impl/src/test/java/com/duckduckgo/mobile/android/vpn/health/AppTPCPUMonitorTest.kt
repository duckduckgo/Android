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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfigImpl
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.feature.FakeToggleConfigDao
import com.duckduckgo.mobile.android.vpn.remote_config.VpnConfigTogglesDao
import com.duckduckgo.mobile.android.vpn.remote_config.VpnRemoteConfigDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import org.junit.runner.RunWith
import org.mockito.kotlin.eq
import org.mockito.kotlin.verifyNoInteractions

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AppTPCPUMonitorTest {
    private lateinit var cpuMonitor: AppTPCPUMonitor

    @OptIn(ExperimentalCoroutinesApi::class)
    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private lateinit var config: AppTpFeatureConfigImpl
    private lateinit var toggleDao: VpnConfigTogglesDao

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    private val mockAppBuildConfig: AppBuildConfig = mock()
    private val mockVpnRemoteConfigDatabase: VpnRemoteConfigDatabase = mock()
    private val mockWorkManager: WorkManager = mock()
    private val mockCPUPerformanceLogger: CPUPerformanceLogger = mock()

    @Before
    fun setup() {
        toggleDao = FakeToggleConfigDao()
        whenever(mockAppBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        whenever(mockAppBuildConfig.isPerformanceTest).thenReturn(false)
        whenever(mockVpnRemoteConfigDatabase.vpnConfigTogglesDao()).thenReturn(toggleDao)

        config = AppTpFeatureConfigImpl(
            coroutineRule.testScope,
            mockAppBuildConfig,
            mockVpnRemoteConfigDatabase,
            coroutineRule.testDispatcherProvider
        )

        cpuMonitor = AppTPCPUMonitor(mockWorkManager, config, mockAppBuildConfig, context, mockCPUPerformanceLogger)
    }

    @Test
    fun whenConfigEnabledStartWorker() {
        config.setEnabled(AppTpSetting.CPUMonitoring, true)
        cpuMonitor.onVpnStarted(coroutineRule.testScope)

        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(AppTPCPUMonitor.APP_TRACKER_CPU_MONITOR_WORKER_TAG),
            eq(ExistingPeriodicWorkPolicy.KEEP),
            any()
        )

        // Never interact with performance logger in regular builds
        verifyNoInteractions(mockCPUPerformanceLogger)
    }

    @Test
    fun whenConfigDisabledDontStartWorker() {
        config.setEnabled(AppTpSetting.CPUMonitoring, false)
        cpuMonitor.onVpnStarted(coroutineRule.testScope)

        verifyNoInteractions(mockWorkManager)

        // Never interact with performance logger in regular builds
        verifyNoInteractions(mockCPUPerformanceLogger)
    }

    @Test
    fun whenVPNStoppedStopWorker() {
        cpuMonitor.onVpnStopped(coroutineRule.testScope, SELF_STOP)

        verify(mockWorkManager).cancelUniqueWork(AppTPCPUMonitor.APP_TRACKER_CPU_MONITOR_WORKER_TAG)

        // Never interact with performance logger in regular builds
        verifyNoInteractions(mockCPUPerformanceLogger)
    }

    @Test
    fun whenPerformanceTestStartAndStopLogger() {
        whenever(mockAppBuildConfig.isPerformanceTest).thenReturn(true)

        cpuMonitor.onVpnStarted(coroutineRule.testScope)
        verify(mockCPUPerformanceLogger).startLogging(coroutineRule.testScope)

        cpuMonitor.onVpnStopped(coroutineRule.testScope, SELF_STOP)
        verify(mockCPUPerformanceLogger).stopLogging(any())
    }
}
