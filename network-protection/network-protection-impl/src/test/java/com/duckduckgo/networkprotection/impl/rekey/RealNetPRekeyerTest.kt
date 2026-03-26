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

package com.duckduckgo.networkprotection.impl.rekey

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.mobile.android.vpn.VpnFeaturesRegistry
import com.duckduckgo.networkprotection.impl.NetPVpnFeature.NETP_VPN
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RealNetPRekeyerTest {
    @Mock
    private lateinit var vpnFeaturesRegistry: VpnFeaturesRegistry

    @Mock
    private lateinit var networkProtectionPixels: NetworkProtectionPixels

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    @Mock
    private lateinit var wgTunnel: WgTunnel

    @Mock
    private lateinit var wgTunnelConfig: WgTunnelConfig

    private var isDeviceLocked = false
    private val keys = KeyPair()

    private val wgQuickConfig = """
        [Interface]
        Address = 1.1.1.2
        DNS = 1.1.1.1
        MTU = 1280
        PrivateKey = ${keys.privateKey.toBase64()}

        [Peer]
        AllowedIPs = 0.0.0.0/0
        Endpoint = 12.12.12.12:443
        Name = name
        Location = Furadouro
        PublicKey = ${keys.publicKey.toBase64()}
    """.trimIndent()
    private val config = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

    private lateinit var testee: RealNetPRekeyer

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        runBlocking {
            whenever(wgTunnel.createWgConfig(any())).thenReturn(Result.success(config))
            whenever(wgTunnel.createAndSetWgConfig(any())).thenReturn(Result.success(config))
        }

        testee = RealNetPRekeyer(
            vpnFeaturesRegistry,
            networkProtectionPixels,
            "name",
            wgTunnel,
            wgTunnelConfig,
            appBuildConfig,
            { isDeviceLocked },
        )
    }

    @Test
    fun `do not rekey in production if time since last rekey is less than 24h`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(23))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey in internal if time since last rekey is less than 24h`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(23))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey if registering new key fails`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey device is not locked`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = false

        testee.doRekey()

        assertNoRekey()
    }

    @Test
    fun `do not rekey if not internal build and forced rekey`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.forceRekey()

        assertNoRekey()
    }

    @Test
    fun `do rekey if internal build and forced rekey`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.forceRekey()

        assertRekey()
    }

    @Test
    fun `do not rekey if internal build and forced rekey but vpn disabled`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(false)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.forceRekey()

        assertNoRekey()
    }

    @Test
    fun `do rekey if production build`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(true)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.doRekey()

        assertRekey()
    }

    @Test
    fun `do not rekey if production build but vpn disabled`() = runTest {
        whenever(vpnFeaturesRegistry.isFeatureRegistered(NETP_VPN)).thenReturn(false)
        whenever(wgTunnelConfig.getWgConfigCreatedAt())
            .thenReturn(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1))
        isDeviceLocked = true
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)

        testee.doRekey()

        assertNoRekey()
    }

    private suspend fun assertNoRekey() {
        verify(wgTunnel, never()).createWgConfig(any())
        verify(wgTunnel, never()).createAndSetWgConfig(any())
        verify(vpnFeaturesRegistry, never()).refreshFeature(NETP_VPN)
        verify(networkProtectionPixels, never()).reportRekeyCompleted()
    }

    private suspend fun assertRekey() {
        verify(wgTunnel, never()).createWgConfig(any())
        verify(wgTunnel).createAndSetWgConfig(any())
        verify(vpnFeaturesRegistry).refreshFeature(NETP_VPN)
        verify(networkProtectionPixels).reportRekeyCompleted()
    }
}
