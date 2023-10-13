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

import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import com.duckduckgo.networkprotection.impl.settings.geoswitching.GeoSwitchingContentProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify

@OptIn(ExperimentalCoroutinesApi::class)
class RealWgServerApiTest {
    private val wgVpnControllerService = FakeWgVpnControllerService()

    private lateinit var productionWgServerDebugProvider: DefaultWgServerDebugProvider
    private lateinit var internalWgServerDebugProvider: FakeWgServerDebugProvider
    private lateinit var productionApi: RealWgServerApi
    private lateinit var internalApi: RealWgServerApi

    @Mock
    private lateinit var geoSwitchingContentProvider: GeoSwitchingContentProvider

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        productionWgServerDebugProvider = DefaultWgServerDebugProvider()
        internalWgServerDebugProvider = FakeWgServerDebugProvider(wgVpnControllerService)

        internalApi = RealWgServerApi(
            wgVpnControllerService,
            internalWgServerDebugProvider,
            geoSwitchingContentProvider,
        )
        productionApi = RealWgServerApi(
            wgVpnControllerService,
            productionWgServerDebugProvider,
            geoSwitchingContentProvider,
        )
    }

    @Test
    fun whenRegisterInProductionThenReturnTheFirstServer() = runTest {
        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "",
                location = "Newark, US",
                gateway = "1.2.3.4",
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            productionApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenRegisterInInternalAndServerSelectedThenReturnSelectedServer() = runTest {
        internalWgServerDebugProvider.selectedServer = "egress.euw.2"

        assertEquals(
            WgServerData(
                serverName = "egress.euw.2",
                publicKey = "4PnM/V0CodegK44rd9fKTxxS9QDVTw13j8fxKsVud3s=",
                publicEndpoint = "31.204.129.39:443",
                address = "",
                location = "Rotterdam, NL",
                gateway = "1.2.3.4",
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            internalApi.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenRegisterInInternalAndServerSelectedWithNoServerCountryThenReturnSelectedServerWithNullLocation() = runTest {
        internalWgServerDebugProvider.selectedServer = "egress.euw"

        assertEquals(
            WgServerData(
                serverName = "egress.euw",
                publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                publicEndpoint = "euw.egress.np.duck.com:443",
                address = "",
                location = null,
                gateway = "1.2.3.4",
                allowedIPs = "0.0.0.0/0,::0/0",
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
                address = "",
                location = "Newark, US",
                gateway = "1.2.3.4",
                allowedIPs = "0.0.0.0/0,::0/0",
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
        internalApi.registerPublicKey("testpublickey")

        assertEquals(8, internalWgServerDebugProvider.cachedServers.size)
    }

    @Test
    fun whenRegisterInProductionThenDownloadGeoswitchingData() = runTest {
        productionApi.registerPublicKey("testpublickey")

        verify(geoSwitchingContentProvider).downloadData()
    }

    @Test
    fun whenRegisterInInternalThenDownloadGeoswitchingData() = runTest {
        internalApi.registerPublicKey("testpublickey")

        verify(geoSwitchingContentProvider).downloadData()
    }
}

private class FakeWgServerDebugProvider(private val controllerService: WgVpnControllerService) : WgServerDebugProvider {
    val cachedServers = mutableListOf<Server>()
    var selectedServer: String? = null

    override suspend fun getSelectedServerName(): String? = selectedServer

    override suspend fun cacheServers(servers: List<Server>) {
        cachedServers.clear()
        cachedServers.addAll(servers)
    }

    override suspend fun fetchServers(): List<Server> {
        return controllerService.getServers().map { it.server }
    }
}

private class DefaultWgServerDebugProvider : WgServerDebugProvider
