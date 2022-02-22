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

import com.duckduckgo.mobile.android.vpn.service.VpnStopReason
import com.duckduckgo.mobile.android.vpn.service.VpnStopReason.SelfStop
import kotlinx.coroutines.test.TestCoroutineScope
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class AppHealthMonitorManagerTest {

    @Mock private lateinit var appHealthMonitor: AppHealthMonitor

    private lateinit var appHealthMonitorManager: AppHealthMonitorManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)

        appHealthMonitorManager = AppHealthMonitorManager(appHealthMonitor)
    }

    @Test
    fun whenOnVpnStartedThenStartMonitoring() {
        appHealthMonitorManager.onVpnStarted(TestCoroutineScope())

        verify(appHealthMonitor).startMonitoring()
        verifyNoMoreInteractions(appHealthMonitor)
    }

    @Test
    fun whenOnVpnStoppedThenStopMonitoring() {
        appHealthMonitorManager.onVpnStopped(TestCoroutineScope(), SelfStop)

        verify(appHealthMonitor).stopMonitoring()
        verifyNoMoreInteractions(appHealthMonitor)
    }
}
