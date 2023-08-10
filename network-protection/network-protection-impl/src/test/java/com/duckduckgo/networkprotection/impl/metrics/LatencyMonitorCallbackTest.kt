/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.metrics

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.fakes.FakeNetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository.ServerDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LatencyMonitorCallbackTest {

    private val coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var mockWorkManager: WorkManager

    @Mock
    private lateinit var mockVpnFeaturesRegistry: VpnFeaturesRegistry

    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    private lateinit var testee: LatencyMonitorCallback

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        networkProtectionRepository = FakeNetworkProtectionRepository()
        networkProtectionRepository.serverDetails = ServerDetails(
            serverName = "euw.1",
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )

        testee = LatencyMonitorCallback(mockWorkManager, networkProtectionRepository, mockVpnFeaturesRegistry)
    }

    @Test
    fun onNativeStartedNetpOffDoNotEnqueueWork() = runTest {
        whenever(mockVpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(false)
        testee.onVpnStarted(coroutineRule.testScope)
        verifyNoInteractions(mockWorkManager)
    }

    @Test
    fun onNativeStartedNetpOnEnqueueWork() = runTest {
        whenever(mockVpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        testee.onVpnStarted(coroutineRule.testScope)
        verify(mockWorkManager).enqueueUniquePeriodicWork(
            eq(LatencyMonitorCallback.NETP_LATENCY_MONITOR_WORKER_TAG),
            eq(ExistingPeriodicWorkPolicy.REPLACE),
            any(),
        )
    }

    @Test
    fun onNativeStoppedCancelWork() {
        testee.onVpnStopped(coroutineRule.testScope, SELF_STOP)
        verify(mockWorkManager).cancelUniqueWork(LatencyMonitorCallback.NETP_LATENCY_MONITOR_WORKER_TAG)
    }
}
