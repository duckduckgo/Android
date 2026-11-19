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

package com.duckduckgo.networkprotection.impl.config

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.networkprotection.impl.CurrentTimeProvider
import com.duckduckgo.networkprotection.impl.VpnRemoteFeatures
import com.duckduckgo.networkprotection.impl.configuration.NetPNetworkGate
import com.duckduckgo.networkprotection.impl.configuration.RealVPNExcludedDomainsFallback
import com.duckduckgo.networkprotection.impl.settings.FakeNetPSettingsLocalConfigFactory
import com.duckduckgo.networkprotection.impl.settings.NetPSettingsLocalConfig
import com.duckduckgo.networkprotection.impl.settings.NetpVpnSettingsDataStore
import kotlinx.coroutines.test.runTest
import okhttp3.Dns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

class RealNetPDefaultConfigProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val lookups = mutableListOf<String>()
    private var resolvedIp = RESOLVED_IP
    private var lookupFails = false
    private val dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            lookups.add(hostname)
            if (lookupFails) throw UnknownHostException(hostname)
            return listOf(InetAddress.getByName(resolvedIp))
        }
    }
    private val clock = object : CurrentTimeProvider {
        var nowMillis = 1_000_000L
        override fun getTimeInMillis(): Long = nowMillis
        override fun getTimeInEpochSeconds(): Long = TimeUnit.MILLISECONDS.toSeconds(nowMillis)
    }
    private val settingsDataStore = object : NetpVpnSettingsDataStore {
        override var customDns: String? = null
    }

    private lateinit var netPSettingsLocalConfig: NetPSettingsLocalConfig
    private lateinit var vpnRemoteFeatures: VpnRemoteFeatures
    private lateinit var provider: RealNetPDefaultConfigProvider

    @Before
    fun setUp() {
        netPSettingsLocalConfig = FakeNetPSettingsLocalConfigFactory.create()
        vpnRemoteFeatures = FakeFeatureToggleFactory.create(VpnRemoteFeatures::class.java)
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(Toggle.State(enable = true))

        provider = RealNetPDefaultConfigProvider(
            netPExclusionListRepository = mock(),
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            netPSettingsLocalConfig = netPSettingsLocalConfig,
            systemAppsExclusionRepository = mock(),
            netpVpnSettingsDataStore = settingsDataStore,
            vpnRemoteFeatures = vpnRemoteFeatures,
            dns = dns,
            networkGate = NetPNetworkGate(),
            excludedDomainsFallback = RealVPNExcludedDomainsFallback(),
            currentTimeProvider = clock,
        )
    }

    @Test
    fun whenRoutesRequestedRepeatedlyThenExcludedHostsResolvedOnce() = runTest {
        repeat(3) { provider.routes() }

        assertEquals(RealVPNExcludedDomainsFallback().getFallback().keys.toList(), lookups)
    }

    @Test
    fun whenHostIpChangesWithinAnHourThenFirstResolvedIpStaysExcluded() = runTest {
        provider.routes()
        resolvedIp = OTHER_RESOLVED_IP
        clock.nowMillis += TimeUnit.MINUTES.toMillis(59)

        val routes = provider.routes()

        assertFalse(routes.covers(RESOLVED_IP))
        assertTrue(routes.covers(OTHER_RESOLVED_IP))
    }

    @Test
    fun whenAnHourPassesThenHostsResolvedAgain() = runTest {
        provider.routes()
        resolvedIp = OTHER_RESOLVED_IP
        clock.nowMillis += TimeUnit.HOURS.toMillis(1)

        val routes = provider.routes()

        assertFalse(routes.covers(OTHER_RESOLVED_IP))
        assertTrue(routes.covers(RESOLVED_IP))
    }

    @Test
    fun whenLookupThrowsThenNothingCachedAndNextRoutesResolveAgain() = runTest {
        lookupFails = true

        assertTrue(runCatching { provider.routes() }.exceptionOrNull() is UnknownHostException)

        lookupFails = false
        lookups.clear()
        provider.routes()
        assertTrue(lookups.isNotEmpty())
    }

    @Test
    fun whenExcludeLocalNetworkRoutesChangesBetweenCallsThenRoutesFollowIt() = runTest {
        netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setRawStoredState(Toggle.State(enable = true))
        assertFalse(provider.routes().covers(LOCAL_IP))

        netPSettingsLocalConfig.vpnExcludeLocalNetworkRoutes().setRawStoredState(Toggle.State(enable = false))
        assertTrue(provider.routes().covers(LOCAL_IP))
    }

    @Test
    fun whenCustomDnsChangesBetweenCallsThenRoutesFollowIt() = runTest {
        settingsDataStore.customDns = "8.8.8.8"
        assertEquals(32, provider.routes()["8.8.8.8"])

        settingsDataStore.customDns = "9.9.9.9"
        val routes = provider.routes()

        assertEquals(32, routes["9.9.9.9"])
        assertFalse(routes.containsKey("8.8.8.8"))
    }

    @Test
    fun whenLocalDnsDisabledThenRoutesMakeNoLookup() = runTest {
        vpnRemoteFeatures.localVpnControllerDns().setRawStoredState(Toggle.State(enable = false))

        provider.routes()
        assertTrue(lookups.isEmpty())
    }

    private fun Map<String, Int>.covers(ip: String): Boolean {
        val target = ip.toIpv4Int()
        return any { (address, mask) ->
            val netmask = if (mask == 0) 0L else (0xFFFFFFFFL shl (32 - mask)) and 0xFFFFFFFFL
            (address.toIpv4Int() and netmask) == (target and netmask)
        }
    }

    private fun String.toIpv4Int(): Long = split(".").fold(0L) { acc, octet -> (acc shl 8) or octet.toLong() }

    private companion object {
        const val RESOLVED_IP = "20.93.77.32"
        const val OTHER_RESOLVED_IP = "20.93.77.33"
        const val LOCAL_IP = "192.168.1.10"
    }
}
