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

package com.duckduckgo.networkprotection.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.mobile.android.vpn.network.FakeDnsProvider
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.RESTART
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel.WgTunnelData
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ClientInterface
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import java.net.InetAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class WgVpnNetworkStackTest {

    @Mock
    private lateinit var wgProtocol: WgProtocol

    @Mock
    private lateinit var wgTunnel: WgTunnel

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    @Mock
    private lateinit var netpPixels: NetworkProtectionPixels

    private lateinit var wgTunnelData: WgTunnelData

    private lateinit var privateDnsProvider: FakeDnsProvider

    private val netPDefaultConfigProvider = object : NetPDefaultConfigProvider {
        override fun fallbackDns(): Set<InetAddress> {
            return setOf(InetAddress.getByName("127.0.0.1"))
        }

        override fun routes(): Map<String, Int> {
            return mapOf("10.11.12.1" to 32)
        }

        override fun exclusionList(): Set<String> {
            return setOf("com.example.app")
        }
    }

    private lateinit var wgVpnNetworkStack: WgVpnNetworkStack

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        privateDnsProvider = FakeDnsProvider()

        wgTunnelData = WgTunnelData(
            serverName = "euw.1",
            userSpaceConfig = "testuserspaceconfig",
            serverIP = "10.10.10.10",
            serverLocation = "Stockholm, Sweden",
            tunnelAddress = emptyMap(),
            gateway = "1.2.3.4",
        )

        wgVpnNetworkStack = WgVpnNetworkStack(
            { wgProtocol },
            { wgTunnel },
            { networkProtectionRepository },
            currentTimeProvider,
            { netpPixels },
            netPDefaultConfigProvider,
            privateDnsProvider,
        )
    }

    @Test
    fun whenOnPrepareVpnThenReturnVpnTunnelConfigAndStoreServerDetails() = runTest {
        whenever(wgTunnel.establish()).thenReturn(wgTunnelData)

        val actual = wgVpnNetworkStack.onPrepareVpn().getOrNull()
        val expectedDns = (netPDefaultConfigProvider.fallbackDns() + InetAddress.getByName(wgTunnelData.gateway))

        assertNotNull(actual)
        assertEquals(1280, actual!!.mtu)
        assertEquals(emptyMap<InetAddress, Int>(), actual.addresses)
        assertEquals(setOf("com.example.app"), actual.appExclusionList)
        assertEquals(mapOf("10.11.12.1" to 32), actual.routes)
        assertEquals(expectedDns.size, actual.dns.size)
        assertTrue(actual.dns.any { it.hostAddress == "1.2.3.4" })
        assertTrue(actual.dns.any { it.hostAddress == "127.0.0.1" })

        verify(networkProtectionRepository).serverDetails = ServerDetails(
            serverName = "euw.1",
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
        verify(networkProtectionRepository).clientInterface = ClientInterface(emptySet())
    }

    @Test
    fun whenOnPrepareVpnAndPrivateDnsConfiguredThenReturnEmptyDnsList() = runTest {
        whenever(wgTunnel.establish()).thenReturn(wgTunnelData)
        privateDnsProvider.mutablePrivateDns.add(InetAddress.getByName("1.1.1.1"))

        val actual = wgVpnNetworkStack.onPrepareVpn().getOrThrow()

        assertNotNull(actual)
        assertEquals(0, actual.dns.size)
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenResetThenSetEnabledTimeInMillis() = runTest {
        whenever(wgTunnel.establish()).thenReturn(wgTunnelData)
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(-1L)
        whenever(currentTimeProvider.getTimeInMillis()).thenReturn(1672229650358L)

        wgVpnNetworkStack.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStartVpn(mock()),
        )

        verify(networkProtectionRepository).enabledTimeInMillis = 1672229650358L
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenSetThenDoNotUpdateEnabledTime() = runTest {
        whenever(wgTunnel.establish()).thenReturn(wgTunnelData)
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(16722296505000L)
        whenever(currentTimeProvider.getTimeInMillis()).thenReturn(1672229650358L)

        wgVpnNetworkStack.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStartVpn(mock()),
        )

        verify(networkProtectionRepository).serverDetails = ServerDetails(
            serverName = "euw.1",
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
        verify(networkProtectionRepository).clientInterface = ClientInterface(
            emptySet(),
        )
        verify(networkProtectionRepository).enabledTimeInMillis
        verifyNoMoreInteractions(networkProtectionRepository)
    }

    @Test
    fun whenNoWgTunnelDataThenOnStartVpnReturnsFailure() = runTest {
        val result = wgVpnNetworkStack.onStartVpn(mock())
        assertTrue(result.isFailure)

        verifyNoInteractions(networkProtectionRepository)
        verify(netpPixels).reportErrorWgInvalidState()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenOnStopVpnWithSelfStopThenResetEnabledTimeInMillisAndServerDetails() = runTest {
        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStopVpn(SELF_STOP),
        )

        verify(networkProtectionRepository).enabledTimeInMillis = -1
        verify(networkProtectionRepository).serverDetails = null
    }

    @Test
    fun whenOnStopVpnWithRestartThenResetEnabledTimeInMillisAndServerDetails() = runTest {
        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStopVpn(RESTART),
        )

        verify(networkProtectionRepository).serverDetails = null
        verifyNoMoreInteractions(networkProtectionRepository)
    }

    @Test
    fun whenWgTunnelDataProviderThrowsExceptionThenOnPrepareShouldReturnFailure() = runTest {
        whenever(wgTunnel.establish()).thenReturn(null)

        assertTrue(wgVpnNetworkStack.onPrepareVpn().isFailure)
        verify(netpPixels).reportErrorInRegistration()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenWgProtocolStartWgReturnsFailureThenOnStartVpnShouldReturnFailure() = runTest {
        whenever(wgProtocol.startWg(any(), any(), eq(null))).thenReturn(Result.failure(java.lang.IllegalStateException()))
        whenever(wgTunnel.establish()).thenReturn(wgTunnelData)

        wgVpnNetworkStack.onPrepareVpn()

        assertTrue(wgVpnNetworkStack.onStartVpn(mock()).isFailure)
        verify(netpPixels).reportErrorWgBackendCantStart()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenWgProtocolStartWgReturnsSuccessThenOnStartVpnShouldReturnSuccess() = runTest {
        whenever(wgProtocol.startWg(any(), any(), eq(null))).thenReturn(Result.success(Unit))
        whenever(wgTunnel.establish()).thenReturn(wgTunnelData)

        wgVpnNetworkStack.onPrepareVpn()

        assertTrue(wgVpnNetworkStack.onStartVpn(mock()).isSuccess)
    }
}
