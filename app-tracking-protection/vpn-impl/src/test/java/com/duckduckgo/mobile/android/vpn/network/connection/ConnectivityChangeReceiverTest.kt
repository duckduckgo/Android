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

package com.duckduckgo.mobile.android.vpn.network.connection

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.feature.AppTpFeatureConfig
import com.duckduckgo.mobile.android.vpn.feature.AppTpSetting
import com.duckduckgo.mobile.android.vpn.prefs.VpnPreferences
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class ConnectivityChangeReceiverTest {

    private val appTpFeatureConfig: AppTpFeatureConfig = mock()
    private val context: Context = mock()
    private val vpnPreferences: VpnPreferences = object : VpnPreferences {
        override var isPrivateDnsEnabled: Boolean = false
        override var activeNetworkType: String? = null
    }

    private lateinit var receiver: ConnectivityChangeReceiver

    @Before
    fun setup() {
        receiver = ConnectivityChangeReceiver(appTpFeatureConfig, vpnPreferences, context, TestScope())
    }

    @Test
    fun whenFeatureDisabledThenDoNotRegisterReceiver() {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.NetworkSwitchHandling)).thenReturn(false)
        receiver.onVpnStarted(TestScope())

        verify(context, never()).registerReceiver(any(), any())
    }

    @Test
    fun whenFeatureDisabledThenDoUnregisterReceiver() {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.NetworkSwitchHandling)).thenReturn(false)
        receiver.onVpnStopped(TestScope(), VpnStateMonitor.VpnStopReason.SELF_STOP)

        verify(context).unregisterReceiver(any())
    }

    @Test
    fun whenFeatureEnabledThenRegisterReceiver() {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.NetworkSwitchHandling)).thenReturn(true)
        receiver.onVpnStarted(TestScope())

        verify(context).registerReceiver(any(), any())
    }

    @Test
    fun whenFeatureEnabledThenUnregisterReceiver() {
        whenever(appTpFeatureConfig.isEnabled(AppTpSetting.NetworkSwitchHandling)).thenReturn(true)
        receiver.onVpnStopped(TestScope(), VpnStateMonitor.VpnStopReason.SELF_STOP)

        verify(context).unregisterReceiver(any())
    }
}
