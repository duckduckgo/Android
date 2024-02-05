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

import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.NetPVpnFeature
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
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
import org.mockito.kotlin.atMost
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class FailureRecoveryHandlerTest {
    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Mock
    private lateinit var wgTunnel: WgTunnel

    @Mock
    private lateinit var wgTunnelConfig: WgTunnelConfig

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    private lateinit var testee: FailureRecoveryHandler

    private val keys = KeyPair()
    private val defaultServerData = WgServerData(
        serverName = "name",
        publicKey = "public key",
        publicEndpoint = "1.1.1.1:443",
        address = "10.0.0.1/32",
        location = "Furadouro",
        gateway = "10.1.1.1",
    )

    private val updatedServerData = WgServerData(
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

        testee = FailureRecoveryHandler(vpnFeaturesRegistry, wgTunnel, wgTunnelConfig, currentTimeProvider)
    }

    @Test
    fun whenDiffFromHandshakeIsBelowThresholdThenDoNothing() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(300)

        testee.onTunnelFailure(180)

        verifyNoInteractions(vpnFeaturesRegistry)
        verifyNoInteractions(wgTunnel)
        verifyNoInteractions(wgTunnelConfig)
    }

    @Test
    fun whenOnTunnelFailureRecoveredThenMarkTunnelHealthy() = runTest {
        testee.onTunnelFailureRecovered()

        verify(wgTunnel).markTunnelHealthy()
    }

    @Test
    fun whenFailureRecoveryAndServerChangedThenSetConfigAndRefreshNetp() = runTest {
        val newConfig = getWgConfig(updatedServerData)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.success(newConfig))

        testee.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
    }

    @Test
    fun whenFailureRecoveryAndServerChangedThenDoNothing() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.success(getWgConfig(defaultServerData)))

        testee.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel, never()).markTunnelHealthy()
        verify(wgTunnelConfig, never()).setWgConfig(any())
        verify(vpnFeaturesRegistry, never()).refreshFeature(NetPVpnFeature.NETP_VPN)
    }

    @Test
    fun whenFailureRecoveryAndCreateConfigFailedThenAttemptMax5TimesOnly() = runTest {
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.failure(RuntimeException()))

        testee.onTunnelFailure(180)

        verify(wgTunnel, atMost(5)).markTunnelUnhealthy()
    }

    @Test
    fun whenOnTunnelFailureCalledTwiceThenAttemptRecoveryOnceOnly() = runTest {
        val newConfig = getWgConfig(updatedServerData)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.success(newConfig))

        testee.onTunnelFailure(180)
        testee.onTunnelFailure(180)

        verify(wgTunnel).markTunnelUnhealthy()
        verify(wgTunnel).markTunnelHealthy()
        verify(wgTunnelConfig).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry).refreshFeature(NetPVpnFeature.NETP_VPN)
    }

    @Test
    fun whenOnTunnelFailureCalledAfterRecoveryThenAttemptRecoveryTwice() = runTest {
        val newConfig = getWgConfig(updatedServerData)
        whenever(currentTimeProvider.getTimeInEpochSeconds()).thenReturn(1080)
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NetPVpnFeature.NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfig()).thenReturn(getWgConfig(defaultServerData))
        whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.success(newConfig))

        testee.onTunnelFailure(180)
        testee.onTunnelFailureRecovered()
        testee.onTunnelFailure(180)

        verify(wgTunnel, times(2)).markTunnelUnhealthy()
        verify(wgTunnel, times(3)).markTunnelHealthy()
        verify(wgTunnelConfig, times(2)).setWgConfig(newConfig)
        verify(vpnFeaturesRegistry, times(2)).refreshFeature(NetPVpnFeature.NETP_VPN)
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
