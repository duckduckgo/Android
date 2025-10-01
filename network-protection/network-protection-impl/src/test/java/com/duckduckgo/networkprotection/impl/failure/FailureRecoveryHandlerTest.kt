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

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class FailureRecoveryHandlerTest {

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

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

        failureRecoveryHandler = FailureRecoveryHandler(
            vpnFeaturesRegistry,
            wgTunnel,
            wgTunnelConfig,
            currentTimeProvider,
            networkProtectionPixels,
            coroutineTestRule.testDispatcherProvider,
        )
    }

    @Test
    fun whenDiffFromHandshakeIsBelowThresholdThenDoNothing() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(15))

        verifyNoInteractions(vpnFeaturesRegistry)
        verifyNoInteractions(wgTunnel)
        verifyNoInteractions(wgTunnelConfig)
        verifyNoInteractions(networkProtectionPixels)
    }

    @Test
    fun whenOnTunnelFailureRecoveredThenMarkTunnelHealthy() = runTest {
        failureRecoveryHandler.onTunnelFailureRecovered(coroutineTestRule.testScope)

        verify(wgTunnel).markTunnelHealthy()
        verifyNoMoreInteractions(wgTunnel)
        verifyNoInteractions(networkProtectionPixels)
    }

    @Test
    fun whenFailureRecoveryAndServerChangedThenSetConfigAndRefreshNetp() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentServer)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away
        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels, never()).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenFailureRecoveryAndTunnelAddressChangedThenSetConfigAndRefreshNetp() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentAddress)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away
        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenFailureRecoveryAndServerDidnotChangedThenDoNothing() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(getWgConfig(defaultServerData)))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away
        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel, never()).markTunnelHealthy()
        verify(wgTunnelConfig, never()).setWgConfig(any())
        verify(vpnFeaturesRegistry, never()).refreshFeature(NetPVpnFeature.NETP_VPN)
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerHealthy()
    }

    @Test
    fun whenOnTunnelFailureCalledTwiceThenAttemptRecoveryOnceOnly() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentServer)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))
        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away. The second call should not do anything
        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels, never()).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenOnTunnelFailureCalledTwiceAndDifferentTunnelAddressThenAttemptRecoveryOnceOnly() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentAddress)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))
        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away. The second call should not do anything
        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
        verify(networkProtectionPixels).reportFailureRecoveryStarted()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithServerUnhealthy()
        verify(networkProtectionPixels).reportFailureRecoveryCompletedWithDifferentTunnelAddress()
    }

    @Test
    fun whenOnTunnelFailureCalledAfterRecoveryThenAttemptRecoveryTwice() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentServer)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))
        failureRecoveryHandler.onTunnelFailureRecovered(coroutineTestRule.testScope)
        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away. Since onTunnelFailureRecovered is called,
        // calling onTunnelFailure twice here should attempt recovery 2 times.
        verify(wgTunnel, times(2)).markTunnelUnhealthy()
        verify(wgTunnel, times(3)).markTunnelHealthy()
        verify(wgTunnelConfig, times(2)).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry, times(2)).refreshFeature(NetPVpnFeature.NETP_VPN)
        verify(networkProtectionPixels, times(2)).reportFailureRecoveryStarted()
        verify(networkProtectionPixels, times(2)).reportFailureRecoveryCompletedWithServerUnhealthy()
    }

    @Test
    fun whenOnTunnelFailureCalledAfterRecoveryAndDifferentTunnelAddrThenAttemptRecoveryTwice() = runTest {
        val newConfig = getWgConfig(updatedServerDataDifferentAddress)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(TimeUnit.MINUTES.toSeconds(20))
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(anyOrNull())).thenReturn(Result.success(newConfig))

        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))
        failureRecoveryHandler.onTunnelFailureRecovered(coroutineTestRule.testScope)
        failureRecoveryHandler.onTunnelFailure(coroutineTestRule.testScope, TimeUnit.MINUTES.toSeconds(3))

        // Only first recovery attempt should happen right away. Since onTunnelFailureRecovered is called,
        // calling onTunnelFailure twice here should attempt recovery 2 times.
        verify(wgTunnel, times(2)).markTunnelUnhealthy()
        verify(wgTunnel, times(3)).markTunnelHealthy()
        verify(wgTunnelConfig, times(2)).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry, times(2)).refreshFeature(NetPVpnFeature.NETP_VPN)
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
