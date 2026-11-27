/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.subscription.onboarding.experiment

import androidx.lifecycle.LifecycleOwner
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTED
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.CONNECTING
import com.duckduckgo.networkprotection.api.NetworkProtectionState.ConnectionState.DISCONNECTED
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SubscriptionOnboardingVpnActivationObserverTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val networkProtectionState: NetworkProtectionState = mock()
    private val experimentMetrics: SubscriptionOnboardingExperimentMetrics = mock()
    private val lifecycleOwner: LifecycleOwner = mock()

    private val testee = SubscriptionOnboardingVpnActivationObserver(
        appCoroutineScope = coroutineRule.testScope,
        networkProtectionState = networkProtectionState,
        experimentMetrics = experimentMetrics,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
    )

    @Test
    fun whenVpnConnectsThenReportsVpnActivated() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED, CONNECTING, CONNECTED))

        testee.onCreate(lifecycleOwner)

        verify(experimentMetrics).fireVpnActivated()
    }

    @Test
    fun whenVpnNeverConnectsThenDoesNotReportVpnActivated() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(DISCONNECTED, CONNECTING, DISCONNECTED))

        testee.onCreate(lifecycleOwner)

        verify(experimentMetrics, never()).fireVpnActivated()
    }

    @Test
    fun whenVpnReconnectsThenReportsEachConnection() = runTest {
        whenever(networkProtectionState.getConnectionStateFlow()).thenReturn(flowOf(CONNECTED, DISCONNECTED, CONNECTED))

        testee.onCreate(lifecycleOwner)

        verify(experimentMetrics, times(2)).fireVpnActivated()
    }
}
