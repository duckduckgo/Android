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

package com.duckduckgo.networkprotection.impl

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.anrs.api.CrashLogger
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.data.store.api.FakeSharedPreferencesProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.mobile.android.vpn.network.DnsProvider
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.NeedsNetworkException
import com.duckduckgo.mobile.android.vpn.network.VpnNetworkStack.VpnTunnelConfig
import com.duckduckgo.networkprotection.impl.config.RealNetPDefaultConfigProvider
import com.duckduckgo.networkprotection.impl.configuration.NetPNetworkGate
import com.duckduckgo.networkprotection.impl.configuration.RealVPNExcludedDomainsFallback
import com.duckduckgo.networkprotection.impl.configuration.RealWgServerApi
import com.duckduckgo.networkprotection.impl.configuration.RealWgTunnel
import com.duckduckgo.networkprotection.impl.configuration.RealWgTunnelConfig
import com.duckduckgo.networkprotection.impl.configuration.WgServerDebugProvider
import com.duckduckgo.networkprotection.impl.configuration.WgTunnelStore
import com.duckduckgo.networkprotection.impl.configuration.WgVpnControllerServiceModule
import com.duckduckgo.networkprotection.impl.exclusion.NetPExclusionListRepository
import com.duckduckgo.networkprotection.impl.exclusion.systemapps.SystemAppsExclusionRepository
import com.duckduckgo.networkprotection.impl.pixels.NetworkProtectionPixels
import com.duckduckgo.networkprotection.impl.portprobing.PortProbeResult
import com.duckduckgo.networkprotection.impl.portprobing.PortProbingCoordinator
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider
import com.duckduckgo.networkprotection.impl.store.RealNetworkProtectionRepository
import com.duckduckgo.networkprotection.store.RealNetworkProtectionPrefs
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.wireguard.config.Config
import com.wireguard.crypto.KeyPair
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.BufferedReader
import java.io.StringReader
import java.net.InetAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

/**
 * An in-place restart prepares NetP with `networkAllowed = false` while the running tunnel routes this app, so no
 * network call may leave this app then: prepare either succeeds without one or reports that it needs the network.
 * The stack, tunnel, server API, controller client and config provider are the production ones; only the network
 * edges are faked, behind the production gating, so a call added anywhere on the prepare path is seen here.
 */
@RunWith(AndroidJUnit4::class)
class WgVpnNetworkStackPrepareNetworkTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val networkCalls = CopyOnWriteArrayList<String>()

    private val controller = Interceptor { chain ->
        val path = chain.request().url.encodedPath
        networkCalls.add("controller $path")
        val body = when {
            path.endsWith("/register") -> REGISTER_RESPONSE
            else -> "[]"
        }
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            networkCalls.add("dns $hostname")
            return listOf(InetAddress.getByName("20.93.77.32"))
        }
    }
    private val dnsProvider = object : DnsProvider {
        var privateDnsConfigured = false
        override fun getSystemDns(): List<InetAddress> = emptyList()
        override fun getPrivateDns(): List<InetAddress> {
            networkCalls.add("private dns lookup")
            return emptyList()
        }
        override fun isPrivateDnsConfigured(): Boolean = privateDnsConfigured
    }
    private val clock = object : CurrentTimeProvider {
        var nowMillis = 1_000_000L
        override fun getTimeInMillis(): Long = nowMillis
        override fun getTimeInEpochSeconds(): Long = TimeUnit.MILLISECONDS.toSeconds(nowMillis)
    }

    private val portProbingCoordinator = object : PortProbingCoordinator {
        var hold: CompletableDeferred<Unit>? = null
        override suspend fun probeAndSelect(): PortProbeResult? {
            hold?.also { hold = null }?.await()
            return null
        }
    }

    private val netpPixels: NetworkProtectionPixels = mock()
    private val crashLogger: CrashLogger = mock()
    private val vpnRemoteFeatures = FakeFeatureToggleFactory.create(VpnRemoteFeatures::class.java)
    private lateinit var wgTunnelStore: WgTunnelStore
    private lateinit var wgTunnel: RealWgTunnel
    private lateinit var stack: WgVpnNetworkStack

    @Before
    fun setUp() {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(Toggle.State(enable = true))
        vpnRemoteFeatures.endpointPortFallback().setRawStoredState(Toggle.State(enable = false))

        val networkGate = NetPNetworkGate()
        val controllerClient = WgVpnControllerServiceModule.provideInternalCustomHttpClient(
            okHttpClient = OkHttpClient.Builder().addInterceptor(controller).build(),
            dns = dns,
            networkGate = networkGate,
        )
        val controllerService = WgVpnControllerServiceModule.providesWgVpnControllerService(
            retrofit = Retrofit.Builder()
                .baseUrl("https://controller.netp.duckduckgo.com/")
                .addConverterFactory(MoshiConverterFactory.create(Moshi.Builder().add(KotlinJsonAdapterFactory()).build()))
                .build(),
            customClient = lazy { controllerClient },
        )
        val egressServersProvider: NetpEgressServersProvider = mock()
        val appBuildConfig: AppBuildConfig = mock { on { flavor } doReturn BuildFlavor.PLAY }
        val wgServerApi = RealWgServerApi(controllerService, object : WgServerDebugProvider {}, egressServersProvider, appBuildConfig)

        val netPExclusionListRepository: NetPExclusionListRepository = mock {
            onBlocking { getExcludedAppPackages() } doReturn emptyList()
        }
        val systemAppsExclusionRepository: SystemAppsExclusionRepository = mock {
            onBlocking { getAllExcludedSystemApps() } doReturn emptySet()
        }
        val configProvider = RealNetPDefaultConfigProvider(
            netPExclusionListRepository = netPExclusionListRepository,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create(),
            systemAppsExclusionRepository = systemAppsExclusionRepository,
            netpVpnSettingsDataStore = object : NetpVpnSettingsDataStore {
                override var customDns: String? = null
            },
            vpnRemoteFeatures = vpnRemoteFeatures,
            dns = dns,
            networkGate = networkGate,
            excludedDomainsFallback = RealVPNExcludedDomainsFallback(),
            currentTimeProvider = clock,
        )
        wgTunnelStore = WgTunnelStore(FakeSharedPreferencesProvider())
        wgTunnel = RealWgTunnel(wgServerApi, configProvider, wgTunnelStore)

        stack = WgVpnNetworkStack(
            wgProtocol = lazy { mock<WgProtocol>() },
            wgTunnelLazy = lazy { wgTunnel },
            wgTunnelConfigLazy = lazy { RealWgTunnelConfig(wgTunnelStore) },
            networkProtectionRepository = lazy {
                RealNetworkProtectionRepository(RealNetworkProtectionPrefs(FakeSharedPreferencesProvider()))
            },
            netPDefaultConfigProvider = configProvider,
            currentTimeProvider = clock,
            netpPixels = lazy { netpPixels },
            dnsProvider = dnsProvider,
            crashLogger = crashLogger,
            netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create(),
            vpnRemoteFeatures = vpnRemoteFeatures,
            portProbingCoordinator = lazy { portProbingCoordinator },
            networkGate = networkGate,
        )
    }

    @Test
    fun whenNoStoredConfigThenPrepareWithoutNetworkNeedsNetwork() = runTest {
        assertPrepareWithoutNetworkNeedsNetwork()
        assertNull(wgTunnelStore.wireguardConfig)
    }

    @Test
    fun whenStoredConfigAndExcludedHostsNotResolvedYetThenPrepareWithoutNetworkNeedsNetwork() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()

        assertPrepareWithoutNetworkNeedsNetwork()
    }

    @Test
    fun whenResolvedExcludedHostsExpireThenPrepareWithoutNetworkNeedsNetwork() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()
        stack.onPrepareVpn()
        clock.nowMillis += TimeUnit.HOURS.toMillis(1)

        assertPrepareWithoutNetworkNeedsNetwork()
    }

    @Test
    fun whenStoredConfigAndExcludedHostsResolvedThenPrepareWithoutNetworkSucceeds() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()
        stack.onPrepareVpn()

        assertPrepareWithoutNetworkSucceeds()
    }

    @Test
    fun whenTunnelMarkedUnhealthyThenPrepareWithoutNetworkSucceeds() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()
        stack.onPrepareVpn()
        wgTunnel.markTunnelUnhealthy()

        assertPrepareWithoutNetworkSucceeds()
    }

    @Test
    fun whenStrictPrivateDnsConfiguredThenPrepareWithoutNetworkSucceedsWithoutDns() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()
        stack.onPrepareVpn()
        dnsProvider.privateDnsConfigured = true

        val config = assertPrepareWithoutNetworkSucceeds()

        assertEquals(emptySet<InetAddress>(), config.dns)
    }

    @Test
    fun whenLocalControllerDnsDisabledThenPrepareWithoutNetworkSucceeds() = runTest {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(Toggle.State(enable = false))
        wgTunnelStore.wireguardConfig = storedConfig()

        assertPrepareWithoutNetworkSucceeds()
    }

    @Test
    fun whenNoStoredConfigThenPrepareWithNetworkRegistersAndStoresConfig() = runTest {
        stack.onPrepareVpn(networkAllowed = true).getOrThrow()

        assertTrue(networkCalls.contains("controller /register"))
        assertNotNull(wgTunnelStore.wireguardConfig)
    }

    @Test
    fun whenExcludedHostsNotResolvedThenPrepareWithNetworkResolvesThem() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()

        stack.onPrepareVpn(networkAllowed = true).getOrThrow()

        assertTrue(networkCalls.any { it.startsWith("dns ") })
    }

    @Test
    fun whenPrepareWithoutNetworkNeedsNetworkThenPrepareWithNetworkSucceedsAndCountsOneAttempt() = runTest {
        assertTrue(stack.onPrepareVpn(networkAllowed = false).exceptionOrNull() is NeedsNetworkException)

        stack.onPrepareVpn(networkAllowed = true).getOrThrow()

        assertTrue(networkCalls.contains("controller /register"))
        assertNotNull(wgTunnelStore.wireguardConfig)
        verify(netpPixels, times(1)).reportEnableAttempt()
    }

    @Test
    fun whenPrepareWithNetworkStartsDuringPrepareWithoutNetworkThenItSucceedsAfterIt() = runTest {
        wgTunnelStore.wireguardConfig = storedConfig()
        stack.onPrepareVpn()
        vpnRemoteFeatures.endpointPortFallback().setRawStoredState(Toggle.State(enable = true))
        val probing = CompletableDeferred<Unit>()
        portProbingCoordinator.hold = probing
        val withoutNetwork = async { stack.onPrepareVpn(networkAllowed = false) }
        runCurrent()
        clock.nowMillis += TimeUnit.HOURS.toMillis(1)
        networkCalls.clear()
        clearInvocations(netpPixels, crashLogger)

        val withNetwork = async { stack.onPrepareVpn(networkAllowed = true) }
        runCurrent()
        probing.complete(Unit)

        withoutNetwork.await().getOrThrow()
        withNetwork.await().getOrThrow()
        assertTrue(networkCalls.any { it.startsWith("dns ") })
        verifyNoInteractions(crashLogger)
        verify(netpPixels, never()).reportEnableAttemptFailure()
        verify(netpPixels, never()).reportErrorInRegistration()
    }

    private suspend fun assertPrepareWithoutNetworkSucceeds(): VpnTunnelConfig {
        networkCalls.clear()

        val result = stack.onPrepareVpn(networkAllowed = false)

        assertEquals(emptyList<String>(), networkCalls)
        return result.getOrThrow()
    }

    private suspend fun assertPrepareWithoutNetworkNeedsNetwork() {
        networkCalls.clear()
        val storedConfig = wgTunnelStore.wireguardConfig
        clearInvocations(netpPixels, crashLogger)

        val result = stack.onPrepareVpn(networkAllowed = false)

        assertTrue(result.exceptionOrNull() is NeedsNetworkException)
        assertEquals(emptyList<String>(), networkCalls)
        assertEquals(storedConfig, wgTunnelStore.wireguardConfig)
        verifyNoInteractions(netpPixels, crashLogger)
    }

    private fun storedConfig(): Config = Config.parse(BufferedReader(StringReader(STORED_CONFIG)))

    companion object {
        private val REGISTER_RESPONSE = """
            [{
              "publicKey": "${KeyPair().publicKey.toBase64()}",
              "allowedIPs": ["10.237.97.63/32"],
              "server": {
                "name": "euw.1",
                "internalIp": "1.2.3.4",
                "attributes": {"city": "Stockholm", "country": "se"},
                "publicKey": "${KeyPair().publicKey.toBase64()}",
                "hostnames": [],
                "ips": ["10.10.10.10"],
                "port": 443,
                "ports": [443]
              }
            }]
        """.trimIndent()

        private val STORED_CONFIG = """
            [Interface]
            Address = 10.237.97.63/32
            DNS = 1.2.3.4
            MTU = 1280
            PrivateKey = ${KeyPair().privateKey.toBase64()}

            [Peer]
            AllowedIPs = 0.0.0.0/0
            Endpoint = 10.10.10.10:443
            Name = euw.1
            Location = Stockholm, Sweden
            PublicKey = u4geRTVQHaZYwsQzb/LsJqEDpxU8Fqzb5VjxGeIHslM=
        """.trimIndent()
    }
}
