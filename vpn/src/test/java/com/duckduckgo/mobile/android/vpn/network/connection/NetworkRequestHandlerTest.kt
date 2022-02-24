/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.network.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkCapabilities.TRANSPORT_CELLULAR
import android.net.NetworkCapabilities.TRANSPORT_VPN
import android.net.NetworkCapabilities.TRANSPORT_WIFI
import android.os.Build.VERSION_CODES.M
import android.os.Build.VERSION_CODES.P
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities

@RunWith(AndroidJUnit4::class)
@Config(minSdk = M, maxSdk = P)
class NetworkRequestHandlerTest {
    @Mock lateinit var appBuildConfig: AppBuildConfig

    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private lateinit var networkRequestHandler: NetworkRequestHandler
    private lateinit var listener: Listener
    private lateinit var networkCapabilities: NetworkCapabilities

    @Before fun setup() {
        MockitoAnnotations.openMocks(this)

        listener = Listener()

        networkCapabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.activeNetwork, networkCapabilities)

        whenever(appBuildConfig.sdkInt).thenReturn(24)

        networkRequestHandler = NetworkRequestHandler(connectivityManager, appBuildConfig, listener)
    }

    @Test
    fun whenNoNetworksThenCallOnNetworkDisconnected() {
        networkRequestHandler.updateAllNetworks()

        assertTrue(listener.networks.isEmpty())
    }

    @Test
    fun whenActiveNetworkNoInternetThenCallOnNetworkDisconnected() {
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_WIFI)

        networkRequestHandler.updateAllNetworks()

        assertTrue(listener.networks.isEmpty())
    }

    @Test
    fun whenActiveNetworkAndInternetThenCallOnNetworkConnected() {
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_WIFI)
        shadowOf(networkCapabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        networkRequestHandler.updateAllNetworks()

        assertEquals(1, listener.networks.size)
        assertEquals(connectivityManager.activeNetwork, listener.networks.toTypedArray()[0])
    }

    @Test
    fun whenMultipleNetworksAndInternetThenCallOnNetworkConnected() {
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_WIFI)
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_CELLULAR)
        shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.allNetworks[1], networkCapabilities)

        shadowOf(networkCapabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        networkRequestHandler.updateAllNetworks()

        assertEquals(2, listener.networks.size)
        assertEquals(connectivityManager.activeNetwork, listener.networks.toTypedArray()[0])
    }

    @Test
    fun whenMultipleNetworksOnlyActiveHasInternetInternetThenCallOnNetworkConnected() {
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_WIFI)
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_CELLULAR)

        shadowOf(networkCapabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        networkRequestHandler.updateAllNetworks()

        assertEquals(1, listener.networks.size)
        assertEquals(connectivityManager.activeNetwork, listener.networks.toTypedArray()[0])
    }

    @Test
    fun whenNetworkIsVpnThenDoNotAddItToNetworkList() {
        shadowOf(networkCapabilities).addTransportType(TRANSPORT_VPN)

        shadowOf(networkCapabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)

        networkRequestHandler.updateAllNetworks()

        assertTrue(listener.networks.isEmpty())
    }

    class Listener : NetworkConnectionListener {
        var networks = linkedSetOf<Network>()

        override fun onNetworkDisconnected() {
            networks = linkedSetOf()
        }

        override fun onNetworkConnected(networks: LinkedHashSet<Network>) {
            this.networks = networks
        }
    }
}
