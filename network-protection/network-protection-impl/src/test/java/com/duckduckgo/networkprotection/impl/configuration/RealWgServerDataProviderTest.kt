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
import java.util.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealWgServerDataProviderTest() {
    private val wgVpnControllerService = FakeWgVpnControllerService()

    @Mock
    private lateinit var deviceTimezoneProvider: DeviceTimezoneProvider

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    private lateinit var fakeWgServerDebugProvider: FakeWgServerDebugProvider
    private lateinit var testee: RealWgServerDataProvider

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        fakeWgServerDebugProvider = FakeWgServerDebugProvider()

        testee = RealWgServerDataProvider(
            wgVpnControllerService,
            deviceTimezoneProvider,
            appBuildConfig,
            object : PluginPoint<WgServerDebugProvider> {
                override fun getPlugins(): Collection<WgServerDebugProvider> {
                    return setOf(fakeWgServerDebugProvider)
                }
            },
        )
    }

    @Test
    fun whenGetWgServerDataAndUTCEuropeThenReturnCorrectServerData() = runTest {
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-0:00"))

        assertEquals(
            WgServerData(
                serverName = "egress.euw",
                publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                publicEndpoint = "euw.egress.np.duck.com:443",
                address = "",
                location = null,
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenGetWgServerDataAndUTCEastCoastThenReturnCorrectServerData() = runTest {
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-4:00"))

        assertEquals(
            WgServerData(
                serverName = "egress.use.2",
                publicKey = "q3YJJUwMNP31J8qSvMdVsxASKNcjrm8ep8cLcI0qViY=",
                publicEndpoint = "109.200.208.198:443",
                address = "",
                location = "Newark, United states",
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenGetWgServerDataAndUTCCenterUSAThenReturnCorrectServerData() = runTest {
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-7:00"))

        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "",
                location = null,
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenGetWgServerDataAndUTCWestUSAThenReturnCorrectServerData() = runTest {
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-10:00"))

        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "",
                location = null,
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-1:00"))
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.get("testpublickey")

        assertEquals(
            wgVpnControllerService.getServers().map { it.server },
            fakeWgServerDebugProvider.cachedServers,
        )
    }

    @Test
    fun whenNotInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-1:00"))

        testee.get("testpublickey")

        assertTrue(fakeWgServerDebugProvider.cachedServers.isEmpty())
    }

    @Test
    fun whenInternalFlavorAndUserSelectedServerThenReturnUserSelectedServer() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-10:00"))
        fakeWgServerDebugProvider.selectedServer = "egress.euw"

        assertEquals(
            WgServerData(
                serverName = "egress.euw",
                publicKey = "CLQMP4SFzpyvAzMj3rXwShm+3n6Yt68hGHBF67At+x0=",
                publicEndpoint = "euw.egress.np.duck.com:443",
                address = "",
                location = null,
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.get("testpublickey"),
        )
    }

    @Test
    fun whenInternalFlavorAndUserSelectedServerIsAutomaticThenReturnClosestServer() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        whenever(deviceTimezoneProvider.getTimeZone()).thenReturn(TimeZone.getTimeZone("GMT-10:00"))
        fakeWgServerDebugProvider.selectedServer = null

        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "",
                location = null,
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.get("testpublickey"),
        )
    }
}

private class FakeWgServerDebugProvider : WgServerDebugProvider {
    val cachedServers = mutableListOf<Server>()
    var selectedServer: String? = "egress.usc"

    override suspend fun getSelectedServerName(): String? = selectedServer

    override suspend fun storeEligibleServers(servers: List<Server>) {
        cachedServers.addAll(servers)
    }
}
