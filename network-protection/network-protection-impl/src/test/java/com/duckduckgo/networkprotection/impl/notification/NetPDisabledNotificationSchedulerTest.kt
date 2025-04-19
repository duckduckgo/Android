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

import android.annotation.SuppressLint
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.Toggle.State
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
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

@RunWith(AndroidJUnit4::class)
class NetPDisabledNotificationSchedulerTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var testee: NetPDisabledNotificationScheduler
    private lateinit var notificationManager: NotificationManagerCompat
    private lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig

    @Mock
    private lateinit var netPDisabledNotificationBuilder: NetPDisabledNotificationBuilder

    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        notificationManager = NotificationManagerCompat.from(context)
        netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create()

        testee = NetPDisabledNotificationScheduler(
            context,
            notificationManager,
            netPDisabledNotificationBuilder,
            networkProtectionState,
            netPSettingsLocalConfig,
            TestScope(),
            coroutineRule.testDispatcherProvider,
        )
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenVpnManuallyStoppedThenDoNotShowSnooze() = runTest {
        netPSettingsLocalConfig.vpnNotificationAlerts().setRawStoredState(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)
        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP())

        verify(netPDisabledNotificationBuilder).buildDisabledNotification(any())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenVpnManuallyStoppedWithSnoozeButNoTriggerTimeThenDoNotShowSnooze() = runTest {
        netPSettingsLocalConfig.vpnNotificationAlerts().setRawStoredState(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP())

        verify(netPDisabledNotificationBuilder).buildDisabledNotification(any())
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenVpnSnoozedThenShowSnoozeNotification() = runTest {
        netPSettingsLocalConfig.vpnNotificationAlerts().setRawStoredState(State(enable = true))
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(networkProtectionState.isOnboarded()).thenReturn(true)

        testee.onVpnStarted(coroutineRule.testScope)
        testee.onVpnStopped(coroutineRule.testScope, VpnStopReason.SELF_STOP(20000L))

        verify(netPDisabledNotificationBuilder).buildSnoozeNotification(any(), eq(20000L))
    }
}
