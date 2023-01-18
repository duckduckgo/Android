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

import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.RESTART
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelDataProvider.WgTunnelData
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.store.NetworkProtectionRepository.ServerDetails
import com.wireguard.config.BadConfigException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WgVpnNetworkStackTest {

    @Mock
    private lateinit var wgProtocol: WgProtocol

    @Mock
    private lateinit var wgTunnelDataProvider: WgTunnelDataProvider

    @Mock
    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider
    private lateinit var testee: WgVpnNetworkStack

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        testee = WgVpnNetworkStack(
            { wgProtocol },
            { wgTunnelDataProvider },
            { networkProtectionRepository },
            currentTimeProvider,
        )
    }

    @Test
    fun whenOnPrepareVpnThenReturnVpnTunnelConfigAndStoreServerDetails() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(
            WgTunnelData(
                userSpaceConfig = "testuserspaceconfig",
                serverIP = "10.10.10.10",
                serverLocation = "Stockholm, Sweden",
                tunnelAddress = emptyMap(),
            ),
        )

        assertEquals(
            Result.success(
                VpnTunnelConfig(
                    mtu = 1280,
                    addresses = emptyMap(),
                    dns = emptySet(),
                    routes = emptyMap(),
                    appExclusionList = emptySet(),
                ),
            ),
            testee.onPrepareVpn(),
        )

        verify(networkProtectionRepository).serverDetails = ServerDetails(
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenResetThenSetEnabledTimeInMillis() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(
            WgTunnelData(
                userSpaceConfig = "testuserspaceconfig",
                serverIP = "10.10.10.10",
                serverLocation = "Stockholm, Sweden",
                tunnelAddress = emptyMap(),
            ),
        )
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(-1L)
        whenever(currentTimeProvider.get()).thenReturn(1672229650358L)

        testee.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            testee.onStartVpn(mock()),
        )

        verify(networkProtectionRepository).enabledTimeInMillis = 1672229650358L
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenSetThenDoNotUpdateEnabledTime() = runTest {
        whenever(wgTunnelDataProvider.get()).thenReturn(
            WgTunnelData(
                userSpaceConfig = "testuserspaceconfig",
                serverIP = "10.10.10.10",
                serverLocation = "Stockholm, Sweden",
                tunnelAddress = emptyMap(),
            ),
        )
        whenever(networkProtectionRepository.enabledTimeInMillis).thenReturn(16722296505000L)
        whenever(currentTimeProvider.get()).thenReturn(1672229650358L)

        testee.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            testee.onStartVpn(mock()),
        )

        verify(networkProtectionRepository).serverDetails = ServerDetails(
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
        verify(networkProtectionRepository).enabledTimeInMillis
        verifyNoMoreInteractions(networkProtectionRepository)
    }

    @Test
    fun whenNoWgTunnelDataThenOnStartVpnReturnsFailure() = runTest {
        val result = testee.onStartVpn(mock())
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is BadConfigException)

        verifyNoInteractions(networkProtectionRepository)
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
}
