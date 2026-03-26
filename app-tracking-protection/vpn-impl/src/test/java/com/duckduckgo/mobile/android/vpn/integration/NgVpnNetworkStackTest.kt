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

package com.duckduckgo.mobile.android.vpn.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.mobile.android.app.tracking.AppTrackerDetector
import com.duckduckgo.mobile.android.vpn.apps.TrackingProtectionAppsRepository
import com.duckduckgo.mobile.android.vpn.feature.AppTpLocalFeature
import com.duckduckgo.mobile.android.vpn.network.FakeDnsProvider
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.vpn.network.api.*
import com.duckduckgo.vpn.network.api.AddressRR
import com.duckduckgo.vpn.network.api.DnsRR
import com.duckduckgo.vpn.network.api.VpnNetwork
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import java.net.InetAddress

private const val TRACKER_HOSTNAME = "api2.branch.com"

@RunWith(AndroidJUnit4::class)
class NgVpnNetworkStackTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val vpnNetwork: VpnNetwork = mock()
    private val runtime: Runtime = mock()
    private val appTrackerDetector: AppTrackerDetector = mock()
    private val trackingProtectionAppsRepository: TrackingProtectionAppsRepository = mock()
    private val appTpLocalFeature: AppTpLocalFeature = mock()
    private val deviceShieldPixels: DeviceShieldPixels = mock()

    private lateinit var fakeDnsProvider: FakeDnsProvider
    private lateinit var ngVpnNetworkStack: NgVpnNetworkStack

    @Before
    fun setup() {
        whenever(vpnNetwork.create()).thenReturn(111)
        whenever(vpnNetwork.mtu()).thenReturn(1500)
        fakeDnsProvider = FakeDnsProvider()

        ngVpnNetworkStack = NgVpnNetworkStack(
            appBuildConfig,
            { vpnNetwork },
            runtime,
            appTrackerDetector,
            trackingProtectionAppsRepository,
            appTpLocalFeature,
            deviceShieldPixels,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            fakeDnsProvider,
        )
    }

    @Test
    fun whenOnCreateVpnNoErrorThenReturnSuccess() {
        assertTrue(ngVpnNetworkStack.onCreateVpn().isSuccess)
    }

    @Test
    fun whenOnCreateVpnThenCreateVpnNetwork() {
        ngVpnNetworkStack.onCreateVpn()

        verify(vpnNetwork).create()
    }

    @Test
    fun whenOnCreateVpnMultipleTimesThenDestroyPreviousNetwork() {
        ngVpnNetworkStack.onCreateVpn()
        ngVpnNetworkStack.onCreateVpn()

        verify(vpnNetwork).stop(111)
        verify(vpnNetwork).destroy(111)
    }

    @Test
    fun whenOnCreateVpnMultipleTimesThenCreateVpnNetwork() {
        ngVpnNetworkStack.onCreateVpn()
        ngVpnNetworkStack.onCreateVpn()

        verify(vpnNetwork, times(2)).create()
    }

    @Test
    fun whenOnPrepareVpnThenReturnCorrectVpnTunnelConfig() = runTest {
        whenever(appBuildConfig.model).thenReturn("")
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenMotoGPlayThenConfigureSystemDns() = runTest {
        whenever(appBuildConfig.model).thenReturn("moto g play - 2023")
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertEquals(fakeDnsProvider.getSystemDns().toSet(), config.dns)
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenMotoGPowerThenConfigureSystemDns() = runTest {
        whenever(appBuildConfig.model).thenReturn("moto g stylus 5G")
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertEquals(fakeDnsProvider.getSystemDns().toSet(), config.dns)
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenPrivateDnsSetAndDeviceModelMatchesThenConfigureEmptyDns() = runTest {
        whenever(appBuildConfig.model).thenReturn("moto g stylus 5G")
        fakeDnsProvider.mutablePrivateDns.add(InetAddress.getLocalHost())
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenDeviceModelNotMatchedThenConfigureEmptyDns() = runTest {
        whenever(appBuildConfig.model).thenReturn("wrong device")
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenMotoGAndPrivateDnsSetThenReturnNoDns() = runTest {
        whenever(appBuildConfig.model).thenReturn("moto g play")
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        fakeDnsProvider.mutablePrivateDns.add(InetAddress.getLocalHost())
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenShortListedDeviceThenReturnSearchDomains() = runTest {
        whenever(appBuildConfig.model).thenReturn("moto g play")
        fakeDnsProvider.searchDomain = "internal.com"
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals("internal.com", config.searchDomains)
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenNotShortListedDeviceThenReturnSearchDomains() = runTest {
        whenever(appBuildConfig.model).thenReturn("wrong device")
        fakeDnsProvider.mutableSystemDns.add(InetAddress.getLocalHost())
        fakeDnsProvider.searchDomain = "internal.com"
        whenever(trackingProtectionAppsRepository.getExclusionAppsList()).thenReturn(listOf())

        val configResult = ngVpnNetworkStack.onPrepareVpn()
        assertTrue(configResult.isSuccess)

        val config = configResult.getOrThrow()
        assertEquals(1500, config.mtu)
        assertTrue(config.routes.isEmpty())
        assertTrue(config.dns.isEmpty())
        assertEquals("internal.com", config.searchDomains)
        assertEquals(
            mapOf(
                InetAddress.getByName("10.0.0.2") to 32,
                InetAddress.getByName("fd00:1:fd00:1:fd00:1:fd00:1") to 128, // Add IPv6 Unique Local Address
            ),
            config.addresses,
        )
    }

    @Test
    fun whenOnEmFileErrorThenKillProcess() {
        ngVpnNetworkStack.onError(24, "EmFileError")

        verify(runtime).exit(0)
    }

    @Test
    fun whenOnAnyErrorOtherThanEmFileThenNoop() {
        for (error in 0..23) {
            ngVpnNetworkStack.onError(error, "SomeError")
            verify(runtime, never()).exit(0)
        }

        for (error in 25..200) {
            ngVpnNetworkStack.onError(error, "SomeError")
            verify(runtime, never()).exit(0)
        }
    }

    @Test
    fun whenOnExitThenKillProcess() {
        ngVpnNetworkStack.onExit("SomeError")

        verify(runtime).exit(0)
    }

    @Test
    fun whenIsAddressBlockedAndDomainIsTrackerThenReturnFalse() { // false because we don't want to block based on IP addresses
        val uid = 1200
        val tracker = AppTrackerDetector.AppTracker(
            "hostname",
            33,
            "AppDisplayName",
            "app.package.name",
            "AppName",
        )

        whenever(appTrackerDetector.evaluate(TRACKER_HOSTNAME, uid)).thenReturn(tracker)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(TRACKER_HOSTNAME, "1.1.1.1"))

        assertFalse(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    @Test
    fun whenIsAddressBlockedAndDomainIsNotTrackerThenReturnFalse() {
        val uid = 1200

        whenever(appTrackerDetector.evaluate(TRACKER_HOSTNAME, uid)).thenReturn(null)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(TRACKER_HOSTNAME, "1.1.1.1"))

        Assert.assertFalse(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    private fun createDnsRecord(
        domain: String,
        address: String,
    ): DnsRR {
        return DnsRR(
            time = 0,
            qName = domain,
            aName = domain,
            resource = address,
            ttl = 0,
        )
    }
}
