/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.pixels

import com.duckduckgo.app.statistics.wideevents.FlowStatus
import com.duckduckgo.app.statistics.wideevents.WideEventClient
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class VpnEnableWideEventTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule(StandardTestDispatcher())

    private val wideEventClient: WideEventClient = mock()

    private lateinit var vpnEnableWideEvent: VpnEnableWideEventImpl

    @Before
    fun setup() {
        vpnEnableWideEvent = VpnEnableWideEventImpl(
            wideEventClient = wideEventClient,
            appCoroutineScope = coroutineRule.testScope,
            dispatchers = coroutineRule.testDispatcherProvider,
        )
    }

    @Test
    fun `onNotifyVpnStartSuccess sends flowStep`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(123L)))

        vpnEnableWideEvent.onNotifyVpnStartSuccess()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verify(wideEventClient).flowStep(
            wideEventId = 123L,
            stepName = "notify_vpn_start",
        )
    }

    @Test
    fun `onNotifyVpnStartFailed finishes flow with Failure`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(456L)))

        vpnEnableWideEvent.onNotifyVpnStartFailed()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verify(wideEventClient).flowFinish(
            wideEventId = 456L,
            FlowStatus.Failure("notify_vpn_start_failed"),
        )
    }

    @Test
    fun `onNullTunnelCreated sends flowStep`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(789L)))

        vpnEnableWideEvent.onNullTunnelCreated()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verify(wideEventClient).flowStep(
            wideEventId = 789L,
            stepName = "null_tunnel_created",
        )
    }

    @Test
    fun `onVpnPrepared sends flowStep`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(101L)))

        vpnEnableWideEvent.onVpnPrepared()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verify(wideEventClient).flowStep(
            wideEventId = 101L,
            stepName = "network_stack_initialized",
        )
    }

    @Test
    fun `onVpnStarted ends interval and finishes with Success`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(202L)))

        vpnEnableWideEvent.onVpnStarted()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verify(wideEventClient).intervalEnd(
            wideEventId = 202L,
            key = "service_start_duration_ms_bucketed",
        )
        verify(wideEventClient).flowFinish(
            wideEventId = 202L,
            status = FlowStatus.Success,
        )
    }

    @Test
    fun `onVpnStarted clears cachedFlowId`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(303L)))

        vpnEnableWideEvent.onVpnStarted()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")

        reset(wideEventClient)
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        // After cache is cleared, should call getFlowIds again
        vpnEnableWideEvent.onVpnPrepared()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
    }

    @Test
    fun `onVpnStop finishes flow with Failure and reason`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(404L)))

        val reason = VpnStateMonitor.VpnStopReason.SELF_STOP(0)
        vpnEnableWideEvent.onVpnStop(reason)
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verify(wideEventClient).flowFinish(
            wideEventId = 404L,
            status = FlowStatus.Failure(reason = "SELF_STOP"),
        )
    }

    @Test
    fun `onVpnStop clears cachedFlowId`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(505L)))

        vpnEnableWideEvent.onVpnStop(VpnStateMonitor.VpnStopReason.ERROR)
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")

        reset(wideEventClient)
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        // After cache is cleared, should call getFlowIds again
        vpnEnableWideEvent.onNullTunnelCreated()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
    }

    @Test
    fun `cached flow id is reused across multiple calls`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(606L)))

        vpnEnableWideEvent.onNotifyVpnStartSuccess()
        vpnEnableWideEvent.onNullTunnelCreated()
        vpnEnableWideEvent.onVpnPrepared()
        advanceUntilIdle()

        // getFlowIds should only be called once due to caching
        verify(wideEventClient, times(1)).getFlowIds("vpn-enable")
        verify(wideEventClient).flowStep(606L, "notify_vpn_start")
        verify(wideEventClient).flowStep(606L, "null_tunnel_created")
        verify(wideEventClient).flowStep(606L, "network_stack_initialized")
    }

    @Test
    fun `no flow id available results in no client interactions`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(emptyList()))

        vpnEnableWideEvent.onNotifyVpnStartSuccess()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verifyNoMoreInteractions(wideEventClient)
    }

    @Test
    fun `getFlowIds exception is handled gracefully`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenThrow(RuntimeException("Network error"))

        vpnEnableWideEvent.onVpnPrepared()
        advanceUntilIdle()

        verify(wideEventClient).getFlowIds("vpn-enable")
        verifyNoMoreInteractions(wideEventClient)
    }

    @Test
    fun `getFlowIds returns multiple ids uses last one`() = runTest {
        whenever(wideEventClient.getFlowIds(any()))
            .thenReturn(Result.success(listOf(100L, 200L, 300L)))

        vpnEnableWideEvent.onNotifyVpnStartSuccess()
        advanceUntilIdle()

        verify(wideEventClient).flowStep(
            wideEventId = 300L,
            stepName = "notify_vpn_start",
        )
    }
}
