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

package com.duckduckgo.networkprotection.impl.notification

import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.DISABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.ENABLED
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnRunningState.INVALID
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnState
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.waitlist.FakeNetPRemoteFeatureFactory
import com.duckduckgo.networkprotection.impl.waitlist.NetPRemoteFeature
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class NetPDisabledNotificationSchedulerTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testee: NetPDisabledNotificationScheduler
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig
    private lateinit var netPRemoteFeature: NetPRemoteFeature

    @Mock
    private lateinit var netPDisabledNotificationBuilder: NetPDisabledNotificationBuilder

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var vpnStateMonitor: VpnStateMonitor

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        notificationManager = NotificationManagerCompat.from(context)
        netPRemoteFeature = FakeNetPRemoteFeatureFactory.create()
        netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create()

        testee = NetPDisabledNotificationScheduler(
            context,
            notificationManager,
            netPDisabledNotificationBuilder,
            networkProtectionState,
            netPSettingsLocalConfig,
            TestScope(),
            coroutineRule.testDispatcherProvider,
            netPRemoteFeature,
            vpnStateMonitor,
        )
    }

    @Test
    fun whenVpnManuallyStoppedWithInvalidStateThenDoNotShowSnooze() = runTest {
        netPRemoteFeature.waitlistBetaActive().setEnabled(State(enable = true))
        netPSettingsLocalConfig.vpnNotificationAlerts().setEnabled(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(state = ENABLED),
                VpnState(state = INVALID),
            ),
        )

        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP)

        verify(netPDisabledNotificationBuilder).buildDisabledNotification(any())
    }

    @Test
    fun whenVpnManuallyStoppedWithNoVPNStateThenDoNotShowSnooze() = runTest {
        netPRemoteFeature.waitlistBetaActive().setEnabled(State(enable = true))
        netPSettingsLocalConfig.vpnNotificationAlerts().setEnabled(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(emptyFlow())

        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP)

        verify(netPDisabledNotificationBuilder).buildDisabledNotification(any())
    }

    @Test
    fun whenVpnManuallyStoppedWithButIsNotSnoozedThenShowDisabledNotification() = runTest {
        netPRemoteFeature.waitlistBetaActive().setEnabled(State(enable = true))
        netPSettingsLocalConfig.vpnNotificationAlerts().setEnabled(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(state = ENABLED),
                VpnState(state = DISABLED()),
            ),
        )

        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP)

        verify(netPDisabledNotificationBuilder).buildDisabledNotification(any())
    }

    @Test
    fun whenVpnManuallyStoppedAndSnoozedThenShowSnoozeNotification() = runTest {
        netPRemoteFeature.waitlistBetaActive().setEnabled(State(enable = true))
        netPSettingsLocalConfig.vpnNotificationAlerts().setEnabled(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        whenever(vpnStateMonitor.getStateFlow(NetPVpnFeature.NETP_VPN)).thenReturn(
            flowOf(
                VpnState(state = DISABLED(20000L)),
                VpnState(state = DISABLED(20000L)),
            ),
        )

        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP)

        verify(netPDisabledNotificationBuilder).buildSnoozeNotification(any(), eq(20000L))
    }
}
