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

package com.duckduckgo.mobile.android.vpn.service.state

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.mobile.android.vpn.dao.HeartBeatEntity
import com.duckduckgo.mobile.android.vpn.dao.VpnHeartBeatDao
import com.duckduckgo.mobile.android.vpn.dao.VpnServiceStateStatsDao
import com.duckduckgo.mobile.android.vpn.heartbeat.VpnServiceHeartbeatMonitor
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.DISABLED
import com.duckduckgo.mobile.android.vpn.model.VpnServiceState.ENABLED
import com.duckduckgo.mobile.android.vpn.model.VpnServiceStateStats
import com.duckduckgo.mobile.android.vpn.model.VpnStoppingReason.SELF_STOP
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class RealVpnStateMonitorTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    @Mock
    private lateinit var vpnHeartBeatDao: VpnHeartBeatDao

    @Mock
    private lateinit var vpnServiceStateDao: VpnServiceStateStatsDao

    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry
    private lateinit var testee: RealVpnStateMonitor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        testee = RealVpnStateMonitor(
            vpnHeartBeatDao,
            vpnServiceStateDao,
            vpnFeaturesRegistry,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenIsAllaysOnEnabledThenReturnDefaultValueFalse() = runTest {
        Assert.assertFalse(testee.isAlwaysOnEnabled())
    }

    @Test
    fun whenVpnLastDisabledByAndroidAndVpnKilledBySystemThenReturnTrue() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(null)
        whenever(vpnHeartBeatDao.hearBeats()).thenReturn(listOf(HeartBeatEntity(type = VpnServiceHeartbeatMonitor.DATA_HEART_BEAT_TYPE_ALIVE)))
        whenever(vpnFeaturesRegistry.isAnyFeatureRunning()).thenReturn(false)

        Assert.assertTrue(testee.vpnLastDisabledByAndroid())
    }

    @Test
    fun whenVpnLastDisabledByAndroidAndVpnUnexpectedlyDisabledThenReturnTrue() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(
            VpnServiceStateStats(state = DISABLED),
        )

        Assert.assertTrue(testee.vpnLastDisabledByAndroid())
    }

    @Test
    fun whenVpnLastDisabledByAndroidAndVpnDisabledByUserThenReturnFalse() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(
            VpnServiceStateStats(state = DISABLED, stopReason = SELF_STOP),
        )

        Assert.assertFalse(testee.vpnLastDisabledByAndroid())
    }

    @Test
    fun whenVpnLastDisabledByAndroidAndVpnEnabledThenReturnFalse() = runTest {
        whenever(vpnServiceStateDao.getLastStateStats()).thenReturn(
            VpnServiceStateStats(state = ENABLED),
        )

        Assert.assertFalse(testee.vpnLastDisabledByAndroid())
    }
}
