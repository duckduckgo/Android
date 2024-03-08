/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.networkprotection.impl.failure

import com.duckduckgo.networkprotection.api.NetworkProtectionState
import com.duckduckgo.networkprotection.impl.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.BufferedReader
import java.io.StringReader
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.atMost
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FailureRecoveryHandlerTest {
    @Mock
    private lateinit var networkProtectionState: NetworkProtectionState

    @Mock
    private lateinit var wgTunnel: WgTunnel

    @Mock
    private lateinit var wgTunnelConfig: WgTunnelConfig

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels

    private lateinit var failureRecoveryHandler: FailureRecoveryHandler

    private val keys = KeyPair()
    private val defaultServerData = WgServerData(
        serverName = "name",
        publicKey = "public key",
        publicEndpoint = "1.1.1.1:443",
        address = "10.0.0.1/32",
        location = "Furadouro",
        gateway = "10.1.1.1",
    )

    private val updatedServerDataDifferentAddress = WgServerData(
        serverName = "name",
        publicKey = "public key",
        publicEndpoint = "1.1.1.1:443",
        address = "10.0.0.2/32",
        location = "Furadouro",
        gateway = "10.1.1.1",
    )

    private val updatedServerDataDifferentServer = WgServerData(
        serverName = "name2",
        publicKey = "public key",
        publicEndpoint = "1.1.1.1:443",
        address = "10.0.0.1/32",
        location = "Furadouro",
        gateway = "10.1.1.1",
    )

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        failureRecoveryHandler = FailureRecoveryHandler(networkProtectionState, wgTunnel, wgTunnelConfig, currentTimeProvider, networkProtectionPixels)
    }

    @Test
    fun whenDiffFromHandshakeIsBelowThresholdThenDoNothing() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(300)

        failureRecoveryHandler.onTunnelFailure(180)

        verifyNoInteractions(networkProtectionState)
        verifyNoInteractions(wgTunnel)
        verifyNoInteractions(wgTunnelConfig)
        verifyNoInteractions(networkProtectionPixels)
    }

    @Test
    fun whenOnTunnelFailureRecoveredThenMarkTunnelHealthy() = runTest {
        failureRecoveryHandler.onTunnelFailureRecovered()

        verify(wgTunnel).markTunnelHealthy()
        verifyNoInteractions(networkProtectionPixels)
    }

    @Test
    fun whenFailureRecoveryAndServerChangedThenSetConfigAndRefreshNetp() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentServer)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(networkProtectionState).restart()
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels, never()).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenFailureRecoveryAndTunnelAddressChangedThenSetConfigAndRefreshNetp() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentAddress)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(networkProtectionState).restart()
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenFailureRecoveryAndServerDidnotChangedThenDoNothing() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(getWgConfig(defaultServerData)))

        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel, never()).markTunnelHealthy()
        verify(wgTunnelConfig, never()).setWgConfig(any())
        verify(networkProtectionState, never()).restart()
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerHealthy()
    }

    @Test
    fun whenFailureRecoveryAndCreateConfigFailedThenAttemptMax5TimesOnly() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.failure(RuntimeException()))

        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel, atMost(5)).markTunnelUnhealthy()
        verify(networkProtectionPixels, atMost(5)).reportFailureRecoveryStarted()
        verify(networkProtectionPixels, atMost(5)).reportFailureRecoveryFailed()
    }

    @Test
    fun whenOnTunnelFailureCalledTwiceThenAttemptRecoveryOnceOnly() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentServer)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(180)
        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(networkProtectionState).restart()
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels, never()).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenOnTunnelFailureCalledTwiceAndDifferentTunnelAddressThenAttemptRecoveryOnceOnly() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentAddress)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(180)
        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(networkProtectionState).restart()
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenOnTunnelFailureCalledAfterRecoveryThenAttemptRecoveryTwice() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentServer)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(180)
        failureRecoveryHandler.onTunnelFailureRecovered()
        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel, times(2)).markTunnelUnhealthy()
        verify(wgTunnel, times(3)).markTunnelHealthy()
        verify(wgTunnelConfig, times(2)).setWgConfig(newConfig)
        verify(networkProtectionState, times(2)).restart()
        verify(networkProtectionPixels, times(2)).reportFailureRecoveryStarted()
        verify(networkProtectionPixels, times(2)).reportFailureRecoveryCompletedWithServerUnhealthy()
    }

    @Test
    fun whenOnTunnelFailureCalledAfterRecoveryAndDifferentTunnelAddrThenAttemptRecoveryTwice() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentAddress)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(networkProtectionState.isEnabled()).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(180)
        failureRecoveryHandler.onTunnelFailureRecovered()
        failureRecoveryHandler.onTunnelFailure(180)

        verify(wgTunnel, times(2)).markTunnelUnhealthy()
        verify(wgTunnel, times(3)).markTunnelHealthy()
        verify(wgTunnelConfig, times(2)).setWgConfig(newConfig)
        verify(networkProtectionState, times(2)).restart()
        verify(networkProtectionPixels, times(2)).reportFailureRecoveryStarted()
        verify(networkProtectionPixels, times(2)).reportFailureRecoveryCompletedWithServerUnhealthy()
    }

    private fun getWgConfig(serverData: WgServerData): Config {
        return Config.parse(
            BufferedReader(
                StringReader(
                    """
                    [Interface]
                    Address = ${serverData.address}
                    DNS = ${serverData.gateway}
                    MTU = 1280
                    PrivateKey = ${keys.privateKey.toBase64()}
                    
                    [Peer]
                    AllowedIPs = 0.0.0.0/0
                    Endpoint = ${serverData.publicEndpoint}
                    Name = ${serverData.serverName}
                    Location = ${serverData.location}
                    PublicKey = ${keys.publicKey.toBase64()}
                    """.trimIndent(),
                ),
            ),
        )
    }
}
