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

package com.duckduckgo.networkprotection.impl.configuration

import com.duckduckgo.app.global.plugins.PluginPoint
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor
import com.duckduckgo.networkprotection.impl.configuration.WgServerDataProvider.WgServerData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealWgServerDataProviderTest {
    @Mock
    private lateinit var wgVpnControllerService: WgVpnControllerService

    @Mock
    private lateinit var countryIsoProvider: CountryIsoProvider

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    private val testServers = listOf(
        EligibleServerInfo(
            publicKey = "testpublickey",
            allowedIPs = listOf("10.11.86.8/32"),
            server = Server(
                name = "egress.usc",
                attributes = emptyMap(),
                publicKey = "ovn9RpzUuvQ4XLQt6B3RKuEXGIxa5QpTnehjduZlcSE=",
                hostnames = listOf("usc.egress.np.duck.com"),
                ips = emptyList(),
                port = 443,
            ),
        ),
        EligibleServerInfo(
            publicKey = "testpublickey",
            allowedIPs = listOf("10.11.181.220/32"),
            server = Server(
                name = "egress.euw",
                attributes = mapOf(
                    "country" to "SE",
                    "city" to "Stockholm",
                ),
                publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                hostnames = listOf("euw.egress.np.duck.com"),
                ips = emptyList(),
                port = 443,
            ),
        ),
    )

    private lateinit var fakeWgServerDebugProvider: FakeWgServerDebugProvider
    private lateinit var testee: RealWgServerDataProvider

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        fakeWgServerDebugProvider = FakeWgServerDebugProvider()

        testee = RealWgServerDataProvider(
            wgVpnControllerService,
            countryIsoProvider,
            appBuildConfig,
            object : PluginPoint<WgServerDebugProvider> {
                override fun getPlugins(): Collection<WgServerDebugProvider> {
                    return setOf(fakeWgServerDebugProvider)
                }
            },
        )
    }

    @Test
    fun whenGetWgServerDataAndIsoIsUSThenReturnUSServerData() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(testServers)
        whenever(countryIsoProvider.getCountryIso()).thenReturn("us")

        assertEquals(
            WgServerData(
                publicKey = "ovn9RpzUuvQ4XLQt6B3RKuEXGIxa5QpTnehjduZlcSE=",
                publicEndpoint = "usc.egress.np.duck.com:443",
                address = "10.11.86.8/32",
                location = null,
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenGetWgServerDataAndIsoIsNotUSThenReturnOtherServerData() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(testServers)
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")

        assertEquals(
            WgServerData(
                publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                publicEndpoint = "euw.egress.np.duck.com:443",
                address = "10.11.181.220/32",
                location = "Stockholm, Sweden",
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenNoCountryAttributeThenReturnNoLocation() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(
            listOf(
                EligibleServerInfo(
                    publicKey = "testpublickey",
                    allowedIPs = listOf("10.11.181.220/32"),
                    server = Server(
                        name = "egress.euw",
                        attributes = mapOf(
                            "city" to "Stockholm",
                        ),
                        publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                        hostnames = listOf("euw.egress.np.duck.com"),
                        ips = emptyList(),
                        port = 443,
                    ),
                ),
            ),
        )
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")

        assertNull(testee.get("testpublickey").location)
    }

    @Test
    fun whenNoCityAttributeThenReturnNoLocation() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(
            listOf(
                EligibleServerInfo(
                    publicKey = "testpublickey",
                    allowedIPs = listOf("10.11.181.220/32"),
                    server = Server(
                        name = "egress.euw",
                        attributes = mapOf(
                            "country" to "se",
                        ),
                        publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                        hostnames = listOf("euw.egress.np.duck.com"),
                        ips = emptyList(),
                        port = 443,
                    ),
                ),
            ),
        )
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")

        assertNull(testee.get("testpublickey").location)
    }

    @Test
    fun whenFullCountryPassedThenReturnCorrectFullLocation() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(
            listOf(
                EligibleServerInfo(
                    publicKey = "testpublickey",
                    allowedIPs = listOf("10.11.181.220/32"),
                    server = Server(
                        name = "egress.euw",
                        attributes = mapOf(
                            "city" to "Stockholm",
                            "country" to "Sweden",
                        ),
                        publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                        hostnames = listOf("euw.egress.np.duck.com"),
                        ips = emptyList(),
                        port = 443,
                    ),
                ),
            ),
        )
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")

        assertEquals("Stockholm, Sweden", testee.get("testpublickey").location)
    }

    @Test
    fun whenInternalFlavorGetWgServerDataAndIsoIsNotUSThenReturnUSServerData() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(testServers)
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        assertEquals(
            WgServerData(
                publicKey = "ovn9RpzUuvQ4XLQt6B3RKuEXGIxa5QpTnehjduZlcSE=",
                publicEndpoint = "usc.egress.np.duck.com:443",
                address = "10.11.86.8/32",
                location = null,
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(testServers)
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.get("testpublickey")

        assertEquals(
            testServers,
            fakeWgServerDebugProvider.cachedServers,
        )
    }

    @Test
    fun whenNotInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        whenever(wgVpnControllerService.registerKey(any())).thenReturn(testServers)
        whenever(countryIsoProvider.getCountryIso()).thenReturn("se")

        testee.get("testpublickey")

        assertTrue(fakeWgServerDebugProvider.cachedServers.isEmpty())
    }
}

private class FakeWgServerDebugProvider : WgServerDebugProvider {
    val cachedServers = mutableListOf<EligibleServerInfo>()

    override suspend fun getSelectedServerName(): String? {
        return "egress.usc"
    }

    override suspend fun storeEligibleServers(servers: List<EligibleServerInfo>) {
        cachedServers.addAll(servers)
    }
}
