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

import android.annotation.SuppressLint
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.vpn.network.FakeDnsProvider
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.RESTART
import com.duckduckgo.mobile.android.vpn.state.VpnStateMonitor.VpnStopReason.SELF_STOP
import com.duckduckgo.networkprotection.impl.config.NetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.ServerDetails
import com.duckduckgo.networkprotection.impl.configuration.WgTunnel
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.computeBlockMalwareDnsOrSame
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.store.NetworkProtectionRepository
import com.duckduckgo.networkprotection.impl.store.RealNetworkProtectionRepository
import com.duckduckgo.networkprotection.store.RealNetworkProtectionPrefs
import com.wireguard.config.Config
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*

class WgVpnNetworkStackTest {

    @Mock
    private lateinit var wgProtocol: WgProtocol

    @Mock
    private lateinit var wgTunnel: WgTunnel

    @Mock
    private lateinit var wgTunnelConfig: WgTunnelConfig

    private lateinit var networkProtectionRepository: NetworkProtectionRepository

    @Mock
    private lateinit var currentTimeProvider: CurrentTimeProvider

    @Mock
    private lateinit var netpPixels: NetworkProtectionPixels

    private fun Config.success(): Result<Config> {
        return Result.success(this)
    }

    private lateinit var privateDnsProvider: FakeDnsProvider

    private val netPDefaultConfigProvider = object : NetPDefaultConfigProvider {
        override fun fallbackDns(): Set<InetAddress> {
            return setOf(InetAddress.getByName("127.0.0.1"))
        }

        override suspend fun routes(): Map<String, Int> {
            return mapOf("10.11.12.1" to 32)
        }

        override suspend fun exclusionList(): Set<String> {
            return setOf("com.example.app")
        }
    }

    private val wgQuickConfig = """
        [Interface]
        Address = 10.237.97.63/32
        DNS = 1.2.3.4
        MTU = 1280
        PrivateKey = yD1fKxCG/HFbxOy4YfR6zG86YQ1nOswlsv8n7uypb14=
        
        [Peer]
        AllowedIPs = 0.0.0.0/0
        Endpoint = 10.10.10.10:443
        Name = euw.1
        Location = Stockholm, Sweden
        PublicKey = u4geRTVQHaZYwsQzb/LsJqEDpxU8Fqzb5VjxGeIHslM=
    """.trimIndent()
    private lateinit var wgConfig: Config

    private lateinit var wgVpnNetworkStack: WgVpnNetworkStack
    private lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig
    private lateinit var vpnRemoteFeatures: VpnRemoteFeatures

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create()
        vpnRemoteFeatures = FakeFeatureToggleFactory.create(VpnRemoteFeatures::class.java)

        privateDnsProvider = FakeDnsProvider()
        networkProtectionRepository = RealNetworkProtectionRepository(
            RealNetworkProtectionPrefs(FakeSharedPreferencesProvider()),
        )

        wgConfig = Config.parse(BufferedReader(StringReader(wgQuickConfig)))

        wgVpnNetworkStack = WgVpnNetworkStack(
            { wgProtocol },
            { wgTunnel },
            { wgTunnelConfig },
            { networkProtectionRepository },
            netPDefaultConfigProvider,
            currentTimeProvider,
            { netpPixels },
            privateDnsProvider,
            mock(),
            netPSettingsLocalConfig,
            vpnRemoteFeatures,
        )
    }

    @Test
    fun whenOnPrepareVpnThenReturnVpnTunnelConfigAndStoreServerDetails() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())

        val actual = wgVpnNetworkStack.onPrepareVpn().getOrNull()

        assertNotNull(actual)
        assertEquals(wgConfig.toTunnelConfig(), actual)

        val expectedServerDetails = ServerDetails(
            serverName = "euw.1",
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
        verify(netpPixels).reportEnableAttempt()
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenBlockMalwareIsConfigureDNSIsComputed() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())
        netPSettingsLocalConfig.blockMalware().setRawStoredState(Toggle.State(enable = true))
        vpnRemoteFeatures.allowBlockMalware().setRawStoredState(Toggle.State(enable = true))

        val actual = wgVpnNetworkStack.onPrepareVpn().getOrNull()
        val expected = wgConfig.toTunnelConfig().copy(
            dns = wgConfig.toTunnelConfig().dns.map { it.computeBlockMalwareDnsOrSame() }.toSet(),
        )
        assertNotNull(actual)
        assertEquals(expected, actual)

        verify(netpPixels).reportEnableAttempt()
    }

    @SuppressLint("DenyListedApi")
    @Test
    fun whenBlockMalwareKillSwitched() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())
        netPSettingsLocalConfig.blockMalware().setRawStoredState(Toggle.State(enable = true))
        vpnRemoteFeatures.allowBlockMalware().setRawStoredState(Toggle.State(enable = false))

        val actual = wgVpnNetworkStack.onPrepareVpn().getOrNull()
        val expected = wgConfig.toTunnelConfig().copy(
            dns = wgConfig.toTunnelConfig().dns.toSet(),
        )
        assertNotNull(actual)
        assertEquals(expected, actual)

        verify(netpPixels).reportEnableAttempt()
    }

    @Test
    fun whenOnPrepareVpnAndPrivateDnsConfiguredThenReturnEmptyDnsList() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())
        privateDnsProvider.mutablePrivateDns.add(InetAddress.getByName("1.1.1.1"))

        val actual = wgVpnNetworkStack.onPrepareVpn().getOrThrow()

        assertNotNull(actual)
        assertEquals(0, actual.dns.size)
        verify(netpPixels).reportEnableAttempt()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenResetThenSetEnabledTimeInMillis() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())
        whenever(currentTimeProvider.getTimeInMillis()).thenReturn(1672229650358L)

        wgVpnNetworkStack.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStartVpn(mock()),
        )

        assertEquals(1672229650358L, networkProtectionRepository.enabledTimeInMillis)
        verify(netpPixels).reportEnableAttempt()
        verify(netpPixels).reportEnableAttemptSuccess()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenOnStartVpnAndEnabledTimeHasBeenSetThenDoNotUpdateEnabledTime() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(Result.success(wgConfig))
        whenever(currentTimeProvider.getTimeInMillis()).thenReturn(1672229650358L)

        wgVpnNetworkStack.onPrepareVpn()

        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStartVpn(mock()),
        )

        val expectedServerDetails = ServerDetails(
            serverName = "euw.1",
            ipAddress = "10.10.10.10",
            location = "Stockholm, Sweden",
        )
        // assertEquals(expectedServerDetails, networkProtectionRepository.serverDetails)

        assertEquals(1672229650358L, networkProtectionRepository.enabledTimeInMillis)

        verify(netpPixels).reportEnableAttempt()
        verify(netpPixels).reportEnableAttemptSuccess()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenNoWgTunnelDataThenOnStartVpnReturnsFailure() = runTest {
        val result = wgVpnNetworkStack.onStartVpn(mock())
        assertTrue(result.isFailure)

        assertEquals(-1, networkProtectionRepository.enabledTimeInMillis)
        verify(netpPixels).reportErrorWgInvalidState()
        verify(netpPixels).reportEnableAttemptFailure()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenOnStopVpnWithSelfPauseThenResetEnabledTimeInMillisAndServerDetails() = runTest {
        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStopVpn(SELF_STOP()),
        )

        assertEquals(-1, networkProtectionRepository.enabledTimeInMillis)
        verify(wgTunnelConfig).clearWgConfig()
        // assertNull(networkProtectionRepository.serverDetails)
    }

    @Test
    fun whenOnPauseVpnWithRestartThenResetEnabledTimeInMillisAndServerDetails() = runTest {
        assertEquals(
            Result.success(Unit),
            wgVpnNetworkStack.onStopVpn(RESTART),
        )
        verify(wgTunnelConfig, never()).clearWgConfig()
    }

    @Test
    fun whenWgTunnelDataProviderThrowsExceptionThenOnPrepareShouldReturnFailure() = runTest {
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(Result.failure(NullPointerException("null")))

        assertTrue(wgVpnNetworkStack.onPrepareVpn().isFailure)
        verify(netpPixels).reportErrorInRegistration()
        verify(netpPixels).reportEnableAttempt()
        verify(netpPixels).reportEnableAttemptFailure()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenWgProtocolStartWgReturnsFailureThenOnStartVpnShouldReturnFailure() = runTest {
        whenever(wgProtocol.startWg(any(), any(), eq(null))).thenReturn(Result.failure(java.lang.IllegalStateException()))
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())

        wgVpnNetworkStack.onPrepareVpn()

        assertTrue(wgVpnNetworkStack.onStartVpn(mock()).isFailure)
        verify(netpPixels).reportErrorWgBackendCantStart()
        verify(netpPixels).reportEnableAttempt()
        verify(netpPixels).reportEnableAttemptFailure()
        verifyNoMoreInteractions(netpPixels)
    }

    @Test
    fun whenWgProtocolStartWgReturnsSuccessThenOnStartVpnShouldReturnSuccess() = runTest {
        whenever(wgProtocol.startWg(any(), any(), eq(null))).thenReturn(Result.success(Unit))
        whenever(wgTunnel.createAndSetWgConfig()).thenReturn(wgConfig.success())

        wgVpnNetworkStack.onPrepareVpn()

        assertTrue(wgVpnNetworkStack.onStartVpn(mock()).isSuccess)

        verify(netpPixels).reportEnableAttempt()
        verify(netpPixels).reportEnableAttemptSuccess()
        verifyNoMoreInteractions(netpPixels)
    }

    private fun Config.toTunnelConfig(): VpnTunnelConfig {
        return VpnTunnelConfig(
            mtu = this.`interface`?.mtu ?: 1280,
            addresses = this.`interface`.addresses.associate { Pair(it.address, it.mask) },
            // when Android private DNS are set, we return DO NOT configure any DNS.
            // why? no use intercepting encrypted DNS traffic, plus we can't configure any DNS that doesn't support DoT, otherwise Android
            // will enforce DoT and will stop passing any DNS traffic, resulting in no DNS resolution == connectivity is killed
            dns = this.`interface`.dnsServers,
            customDns = netPDefaultConfigProvider.fallbackDns(),
            routes = this.`interface`.routes.associate { it.address.hostAddress!! to it.mask },
            appExclusionList = this.`interface`.excludedApplications,
        )
    }
}
