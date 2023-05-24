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
import com.duckduckgo.networkprotection.impl.configuration.WgServerApi.WgServerData
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class RealWgServerApiTest() {
    private val wgVpnControllerService = FakeWgVpnControllerService()

    @Mock
    private lateinit var appBuildConfig: AppBuildConfig

    private lateinit var fakeWgServerDebugProvider: FakeWgServerDebugProvider
    private lateinit var testee: RealWgServerApi

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)

        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.PLAY)
        fakeWgServerDebugProvider = FakeWgServerDebugProvider()

        testee = RealWgServerApi(
            wgVpnControllerService,
            appBuildConfig,
            object : PluginPoint<WgServerDebugProvider> {
                override fun getPlugins(): Collection<WgServerDebugProvider> {
                    return setOf(fakeWgServerDebugProvider)
                }
            },
        )
    }

    @Test
    fun whenGetWgServerDataPlayBuildThenReturnFirstServer() = runTest {
        assertEquals(
            WgServerData(
                serverName = "egress.usw.1",
                publicKey = "R/BMR6Rr5rzvp7vSIWdAtgAmOLK9m7CqTcDynblM3Us=",
                publicEndpoint = "162.245.204.100:443",
                address = "",
                location = null,
                gateway = "1.2.3.4",
                allowedIPs = "0.0.0.0/0,::0/0",
            ),
            testee.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenInternalFlavorAndUserSelectedServerThenReturnUserSelectedServer() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)
        fakeWgServerDebugProvider.selectedServer = "egress.euw"

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
            testee.registerPublicKey("testpublickey"),
        )
    }

    @Test
    fun whenNotInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        testee.registerPublicKey("testpublickey")

        assertTrue(fakeWgServerDebugProvider.cachedServers.isEmpty())
    }

    @Test
    fun whenInternalFlavorGetWgServerDataThenStoreReturnedServers() = runTest {
        whenever(appBuildConfig.flavor).thenReturn(BuildFlavor.INTERNAL)

        testee.registerPublicKey("testpublickey")

        assertTrue(fakeWgServerDebugProvider.cachedServers.isNotEmpty())
    }
}

private class FakeWgServerDebugProvider : WgServerDebugProvider {
    val cachedServers = mutableListOf<Server>()
    var selectedServer: String? = null

    override suspend fun getSelectedServerName(): String? = selectedServer

    override suspend fun storeEligibleServers(servers: List<Server>) {
        cachedServers.clear()
        cachedServers.addAll(servers)
    }
}
