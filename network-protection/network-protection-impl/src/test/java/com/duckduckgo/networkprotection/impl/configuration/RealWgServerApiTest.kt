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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.BuildFlavor.INTERNAL
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider
import com.duckduckgo.networkprotection.impl.settings.geoswitching.NetpEgressServersProvider.PreferredLocation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class RealWgServerApiTest {
    private val wgVpnControllerService = FakeWgVpnControllerService()

    private lateinit var productionWgServerDebugProvider: DefaultWgServerDebugProvider
    private lateinit var internalWgServerDebugProvider: FakeWgServerDebugProvider
    private lateinit var productionApi: RealWgServerApi
    private lateinit var internalApi: RealWgServerApi

    @Mock
    private lateinit var netpEgressServersProvider: NetpEgressServersProvider

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        productionWgServerDebugProvider = DefaultWgServerDebugProvider()
        internalWgServerDebugProvider = FakeWgServerDebugProvider()

        internalApi = RealWgServerApi(
            wgVpnControllerService,
            internalWgServerDebugProvider,
            netpEgressServersProvider,
            appBuildConfig,
        )
        productionApi = RealWgServerApi(
            wgVpnControllerService,
            productionWgServerDebugProvider,
            netpEgressServersProvider,
            appBuildConfig,
        )
    }

    @Test
    fun whenRegisterInProductionThenReturnTheFirstServer() = runTest {
        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "10.64.169.158/32",
                location = "Newark, US",
                gateway = "1.2.3.4",
            ),
            productionApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenRegisterInInternalAndServerSelectedThenReturnSelectedServer() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        internalWgServerDebugProvider.selectedServer = "egress.euw.2"

        assertEquals(
            WgServerData(
                serverName = "egress.euw.2",
                publicKey = "4PnM/V0CodegK44rd9fKTxxS9QDVTw13j8fxKsVud3s=",
                publicEndpoint = "31.204.129.39:443",
                address = "10.64.169.158/32",
                location = "Rotterdam, NL",
                gateway = "1.2.3.4",
            ),
            internalApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenRegisterInInternalAndServerSelectedWithNoServerCountryThenReturnSelectedServerWithNullLocation() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        internalWgServerDebugProvider.selectedServer = "egress.euw"

        assertEquals(
            WgServerData(
                serverName = "egress.euw",
                publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                publicEndpoint = "euw.egress.np.duck.com:443",
                address = "10.64.169.158/32",
                location = null,
                gateway = "1.2.3.4",
            ),
            internalApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenRegisterInInternalAndWrongServerSelectedThenReturnFirstServer() = runTest {
        internalWgServerDebugProvider.selectedServer = "egress.wrong"

        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "10.64.169.158/32",
                location = "Newark, US",
                gateway = "1.2.3.4",
            ),
            internalApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenRegisterInProductionThenDoNotCacheServers() = runTest {
        productionApi.registerPublicKey("testpublickey")

        assertTrue(internalWgServerDebugProvider.cachedServers.isEmpty())
    }

    @Test
    fun whenInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        internalApi.registerPublicKey("testpublickey")

        assertEquals(8, internalWgServerDebugProvider.cachedServers.size)
    }

    @Test
    fun whenRegisterInProductionThenDownloadGeoswitchingData() = runTest {
        productionApi.registerPublicKey("testpublickey")

        verify(netpEgressServersProvider).updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations())
    }

    @Test
    fun whenRegisterInInternalThenDownloadGeoswitchingData() = runTest {
        internalApi.registerPublicKey("testpublickey")

        verify(netpEgressServersProvider).updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations())
    }

    @Test
    fun whenUserPreferredCountrySetThenRegisterPublicKeyShouldRequestForCountry() = runTest {
        whenever(netpEgressServersProvider.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations()))
            .thenReturn(PreferredLocation("nl"))

        assertEquals(
            WgServerData(
                serverName = "egress.euw.2",
                publicKey = "4PnM/V0CodegK44rd9fKTxxS9QDVTw13j8fxKsVud3s=",
                publicEndpoint = "31.204.129.39:443",
                address = "10.64.169.158/32",
                location = "Rotterdam, NL",
                gateway = "1.2.3.4",
            ),
            productionApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenUserPreferredLocationSetThenRegisterPublicKeyShouldRequestForCountryAndCity() = runTest {
        whenever(netpEgressServersProvider.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations())).thenReturn(
            PreferredLocation(countryCode = "us", cityName = "Des Moines"),
        )

        assertEquals(
            WgServerData(
                serverName = "egress.usc",
                publicKey = "ovn9RpzUuvQ4XLQt6B3RKuEXGIxa5QpTnehjduZlcSE=",
                publicEndpoint = "109.200.208.196:443",
                address = "10.64.169.158/32",
                location = "Des Moines, US",
                gateway = "1.2.3.4",
            ),
            productionApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenUserPreferredLocationSetAndInternalDebugServerSelectedThenRegisterPublicKeyShouldReturnDebugServer() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(INTERNAL)
        internalWgServerDebugProvider.selectedServer = "egress.euw.2"
        whenever(netpEgressServersProvider.updateServerLocationsAndReturnPreferred(wgVpnControllerService.getEligibleLocations())).thenReturn(
            PreferredLocation(countryCode = "us", cityName = "Des Moines"),
        )

        assertEquals(
            WgServerData(
                serverName = "egress.euw.2",
                publicKey = "4PnM/V0CodegK44rd9fKTxxS9QDVTw13j8fxKsVud3s=",
                publicEndpoint = "31.204.129.39:443",
                address = "10.64.169.158/32",
                location = "Rotterdam, NL",
                gateway = "1.2.3.4",
            ),
            internalApi.registerPublicKey("testpublickey"),
        )
    }
}

private class FakeWgServerDebugProvider() : WgServerDebugProvider {
    val cachedServers = mutableListOf<Server>()
    var selectedServer: String? = null

    override suspend fun getSelectedServerName(): String? = selectedServer

    override suspend fun cacheServers(servers: List<Server>) {
        cachedServers.clear()
        cachedServers.addAll(servers)
    }
}

private class DefaultWgServerDebugProvider : WgServerDebugProvider
