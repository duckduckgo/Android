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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
class RestartReceiverTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val context: Context = mock()
    private val appBuildConfig: AppBuildConfig = mock()

    private lateinit var receiver: RestartReceiver

    @Before
    fun setup() {
        receiver = RestartReceiver(
            coroutineRule.testScope,
            context,
            appBuildConfig,
            coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenInternalBuildThenRegisterReceiverOnStartVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        receiver.onVpnStarted(coroutineRule.testScope)

        verify(context).unregisterReceiver(any())
        verify(context).registerReceiver(any(), any(), isNull(), isNull(), any())
    }

    @Test
    fun whenNotInternalBuildThenRegisterReceiverOnStartVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        receiver.onVpnStarted(coroutineRule.testScope)

        verify(context, never()).unregisterReceiver(any())
        verify(context, never()).registerReceiver(any(), any(), isNull(), isNull(), any())
    }

    @Test
    fun whenInternalBuildThenUnregisterReceiverOnStopVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        receiver.onVpnStopped(coroutineRule.testScope, VpnStateMonitor.VpnStopReason.SELF_STOP())

        verify(context).unregisterReceiver(any())
        verify(context, never()).registerReceiver(any(), any(), isNull(), isNull(), any())
    }

    @Test
    fun whenNotInternalBuildThenUnregisterReceiverOnStopVpn() {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        receiver.onVpnStopped(coroutineRule.testScope, VpnStateMonitor.VpnStopReason.SELF_STOP())

        verify(context).unregisterReceiver(any())
        verify(context, never()).registerReceiver(any(), any(), isNull(), isNull(), any())
    }
}
