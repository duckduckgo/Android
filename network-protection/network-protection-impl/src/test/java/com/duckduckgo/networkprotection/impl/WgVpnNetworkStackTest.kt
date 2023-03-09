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
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.RESTART
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.config.NetPConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider.WgTunnelData
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ClientInterface
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import java.net.InetAddress
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private lateinit var wgTunnelDataProvider: WgTunnelDataProvider

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    @Mock
    private lateinit var netpPixels: NetworkProtectionPixels

    private lateinit var wgTunnelData: WgTunnelData

    private lateinit var testee: WgVpnNetworkStack

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        wgTunnelData = WgTunnelData(
            serverName = "euw.1",
            userSpaceConfig = "testuserspaceconfig",
            serverIP = "10.10.10.10",
            serverLocation = "Stockholm, Sweden",
            tunnelAddress = emptyMap(),
        )

        testee = WgVpnNetworkStack(
            { wgProtocol },
            { wgTunnelDataProvider },
            { networkProtectionRepository },
            currentTimeProvider,
            { netpPixels },
            object : NetPConfigProvider {
                override fun dns(): Set<InetAddress> {
                    return setOf(InetAddress.getLocalHost())
                }
            },
        )
    }

    @Test
    fun whenOnPrepareVpnThenReturnVpnTunnelConfigAndStoreServerDetails() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(wgTunnelData)

        assertEquals(
            Result.success(
                VpnTunnelConfig(
                    mtu = 1280,
                    addresses = emptyMap(),
                    dns = setOf(InetAddress.getLocalHost()),
                    routes = emptyMap(),
                    appExclusionList = setOf("com.google.android.gms"),
                ),
            ),
            testee.onPrepareVpn(),
        )

        verify(networkProtectionRepository).serverDetails = ServerDetails(
            serverName = "euw.1",
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
        verify(networkProtectionRepository).clientInterface = ClientInterface(emptySet())
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenResetThenSetEnabledTimeInMillis() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(wgTunnelData)
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(-1L)
        whenever(currentTimeProvider.getTimeInMillis()).thenReturn(1672229650358L)

        testee.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            testee.onStartVpn(mock()),
        )

        verify(networkProtectionRepository).enabledTimeInMillis = 1672229650358L
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenSetThenDoNotUpdateEnabledTime() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(wgTunnelData)
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(16722296505000L)
        whenever(currentTimeProvider.getTimeInMillis()).thenReturn(1672229650358L)

        testee.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            testee.onStartVpn(mock()),
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
        val result = testee.onStartVpn(mock())
        assertTrue(result.isFailure)

        verifyNoInteractions(networkProtectionRepository)
        verify(netpPixels).reportErrorWgInvalidState()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenOnStopVpnWithSelfStopThenResetEnabledTimeInMillisAndServerDetails() = runTest {
        assertEquals(
            Result.success(Unit),
            testee.onStopVpn(SELF_STOP),
        )

        verify(networkProtectionRepository).enabledTimeInMillis = -1
        verify(networkProtectionRepository).serverDetails = null
    }

    @Test
    fun whenOnStopVpnWithRestartThenResetEnabledTimeInMillisAndServerDetails() = runTest {
        assertEquals(
            Result.success(Unit),
            testee.onStopVpn(RESTART),
        )

        verify(networkProtectionRepository).serverDetails = null
        verifyNoMoreInteractions(networkProtectionRepository)
    }

    @Test
    fun whenWgTunnelDataProviderThrowsExceptionThenOnPrepareShouldReturnFailure() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(null)

        assertTrue(testee.onPrepareVpn().isFailure)
        verify(netpPixels).reportErrorInRegistration()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenWgProtocolStartWgReturnsFailureThenOnStartVpnShouldReturnFailure() = runTest {
        whenever(wgProtocol.startWg(any(), any(), eq(null))).thenReturn(Result.failure(java.lang.IllegalStateException()))
        whenever(wgTunnelDataProvider.get()).thenReturn(wgTunnelData)

        testee.onPrepareVpn()

        assertTrue(testee.onStartVpn(mock()).isFailure)
        verify(netpPixels).reportErrorWgBackendCantStart()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenWgProtocolStartWgReturnsSuccessThenOnStartVpnShouldReturnSuccess() = runTest {
        whenever(wgProtocol.startWg(any(), any(), eq(null))).thenReturn(Result.success(Unit))
        whenever(wgTunnelDataProvider.get()).thenReturn(wgTunnelData)

        testee.onPrepareVpn()

        assertTrue(testee.onStartVpn(mock()).isSuccess)
    }
}
