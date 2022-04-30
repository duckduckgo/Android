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

package com.duckduckgo.mobile.android.vpn.service

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import kotlinx.coroutines.test.TestScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class RestartReceiverTest {

    private val context: Context = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var receiver: RestartReceiver

    @Before
    fun setup() {
        receiver = RestartReceiver(TestScope(), context, appBuildConfig)
    }

    @Test
    fun whenInternalBuildThenRegisterReceiverOnStartVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        receiver.onVpnStarted(TestScope())

        verify(context).unregisterReceiver(any())
        verify(context).registerReceiver(any(), any())
    }

    @Test
    fun whenNotInternalBuildThenRegisterReceiverOnStartVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        receiver.onVpnStarted(TestScope())

        verify(context, never()).unregisterReceiver(any())
        verify(context, never()).registerReceiver(any(), any())
    }

    @Test
    fun whenInternalBuildThenUnregisterReceiverOnStopVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        receiver.onVpnStopped(TestScope(), VpnStateMonitor.VpnStopReason.SELF_STOP)

        verify(context).unregisterReceiver(any())
        verify(context, never()).registerReceiver(any(), any())
    }

    @Test
    fun whenNotInternalBuildThenUnregisterReceiverOnStopVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        receiver.onVpnStopped(TestScope(), VpnStateMonitor.VpnStopReason.SELF_STOP)

        verify(context).unregisterReceiver(any())
        verify(context, never()).registerReceiver(any(), any())
    }
}
