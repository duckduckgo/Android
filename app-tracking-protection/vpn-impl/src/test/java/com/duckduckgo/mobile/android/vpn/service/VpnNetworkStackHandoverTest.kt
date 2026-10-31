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

package com.duckduckgo.mobile.android.vpn.service

import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class VpnNetworkStackHandoverTest {

    private val current: VpnNetworkStack = mock()
    private val next: VpnNetworkStack = mock()

    @Test
    fun whenNextIsSameInstanceThenIsSameStack() {
        val handover = VpnNetworkStackHandover(current = current, next = current)

        assertTrue(handover.isSameStack)
    }

    @Test
    fun whenNextIsDifferentInstanceThenIsNotSameStack() {
        val handover = VpnNetworkStackHandover(current = current, next = next)

        assertFalse(handover.isSameStack)
    }

    @Test
    fun whenStoppingCurrentFailsThenSwitchStillDestroysReplacedStack() {
        whenever(current.onStopVpn(any())).thenReturn(Result.failure(IllegalStateException("boom")))
        whenever(current.onDestroyVpn()).thenReturn(Result.success(Unit))
        val handover = VpnNetworkStackHandover(current = current, next = next)

        handover.switch()

        verify(current).onDestroyVpn()
    }

    @Test
    fun whenSameStackThenSwitchStopsForRestartAndKeepsStackAlive() {
        whenever(current.onStopVpn(any())).thenReturn(Result.success(Unit))
        val handover = VpnNetworkStackHandover(current = current, next = current)

        handover.switch()

        verify(current).onStopVpn(VpnStopReason.RESTART)
        verify(current, never()).onDestroyVpn()
    }

    @Test
    fun whenDifferentStackThenSwitchStopsThenDestroysCurrent() {
        whenever(current.onStopVpn(any())).thenReturn(Result.success(Unit))
        whenever(current.onDestroyVpn()).thenReturn(Result.success(Unit))
        val handover = VpnNetworkStackHandover(current = current, next = next)

        handover.switch()

        inOrder(current) {
            verify(current).onStopVpn(VpnStopReason.RESTART)
            verify(current).onDestroyVpn()
        }
        verify(next, never()).onStopVpn(any())
        verify(next, never()).onDestroyVpn()
    }

    @Test
    fun whenDifferentStackThenAbortDestroysOnlyNext() {
        whenever(next.onDestroyVpn()).thenReturn(Result.success(Unit))
        val handover = VpnNetworkStackHandover(current = current, next = next)

        handover.abort()

        verify(next).onDestroyVpn()
        verify(current, never()).onDestroyVpn()
        verify(current, never()).onStopVpn(any())
    }

    @Test
    fun whenSameStackThenAbortDoesNothing() {
        val handover = VpnNetworkStackHandover(current = current, next = current)

        handover.abort()

        verify(current, never()).onDestroyVpn()
        verify(current, never()).onStopVpn(any())
    }
}
