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

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.mobile.android.vpn.dao.VpnAppTrackerBlockingDao
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.AppTrackerRecorder
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.*
import com.duckduckgo.vpn.network.api.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.*

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class NgVpnNetworkStackTest {

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private val appBuildConfig: AppBuildConfig = mock()
    private val vpnNetwork: VpnNetwork = mock()
    private val appTrackerRepository: AppTrackerRepository = mock()
    private val packageManager: PackageManager = mock()
    private val appTrackerRecorder: AppTrackerRecorder = mock()
    private val vpnDatabase: VpnDatabase = mock()
    private val runtime: Runtime = mock()
    private val vpnAppTrackerBlockingDao: VpnAppTrackerBlockingDao = mock()

    private lateinit var ngVpnNetworkStack: NgVpnNetworkStack

    @Before
    fun setup() {
        val appNameResolver = AppNameResolver(packageManager)
        whenever(packageManager.getApplicationLabel(packageManager.getApplicationInfo(anyString(), eq(PackageManager.GET_META_DATA))))
            .thenReturn("DuckDuckGo")
        whenever(vpnDatabase.vpnAppTrackerBlockingDao()).thenReturn(vpnAppTrackerBlockingDao)
        whenever(vpnNetwork.create()).thenReturn(111)
        whenever(vpnNetwork.mtu()).thenReturn(1500)

        ngVpnNetworkStack = NgVpnNetworkStack(
            InstrumentationRegistry.getInstrumentation().targetContext,
            appBuildConfig,
            { vpnNetwork },
            appTrackerRepository,
            appNameResolver,
            appTrackerRecorder,
            vpnDatabase,
            runtime
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
    fun whenMtuThenReturnMtu() {
        val mtu = ngVpnNetworkStack.mtu()

        assertTrue(mtu == 1500)
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
    fun whenIsAddressBlockedAndDomainIsThirdPartyTrackerThenReturnTrue() {
        val uid = 1200

        whenever(appTrackerRepository.findTracker(eq(THIRD_PARTY_TRACKER.tracker.hostname), anyString())).thenReturn(THIRD_PARTY_TRACKER)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(THIRD_PARTY_TRACKER.tracker.hostname)).thenReturn(null)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(THIRD_PARTY_TRACKER.tracker.hostname, "1.1.1.1"))

        assertTrue(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    @Test
    fun whenIsAddressBlockedAndDomainIsFirstPartyTrackerThenReturnFalse() {
        val uid = 1200

        whenever(appTrackerRepository.findTracker(eq(THIRD_PARTY_TRACKER.tracker.hostname), anyString())).thenReturn(FIRST_PARTY_TRACKER)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(THIRD_PARTY_TRACKER.tracker.hostname)).thenReturn(null)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(THIRD_PARTY_TRACKER.tracker.hostname, "1.1.1.1"))

        assertFalse(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    @Test
    fun whenIsAddressBlockedAndDomainIsNotTrackerThenReturnFalse() {
        val uid = 1200

        whenever(appTrackerRepository.findTracker(eq(THIRD_PARTY_TRACKER.tracker.hostname), anyString())).thenReturn(AppTrackerType.NotTracker)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(THIRD_PARTY_TRACKER.tracker.hostname)).thenReturn(null)
        ngVpnNetworkStack.onDnsResolved(createDnsRecord(THIRD_PARTY_TRACKER.tracker.hostname, "1.1.1.1"))

        assertFalse(ngVpnNetworkStack.isAddressBlocked(AddressRR("1.1.1.1", uid)))
    }

    @Test
    fun whenIsDomainBlockedAndDomainIsFirstPartyTrackerThenReturnFalse() {
        val uid = 1200
        val hostname = TEST_APP_TRACKER.hostname

        whenever(appTrackerRepository.findTracker(eq(hostname), anyString())).thenReturn(FIRST_PARTY_TRACKER)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(hostname)).thenReturn(null)

        assertFalse(ngVpnNetworkStack.isDomainBlocked(DomainRR(hostname, uid)))
    }

    @Test
    fun whenIsDomainBlockedAndDomainIsThirdPartyTrackerThenReturnTrue() {
        val uid = 1200
        val hostname = TEST_APP_TRACKER.hostname

        whenever(appTrackerRepository.findTracker(eq(hostname), anyString())).thenReturn(THIRD_PARTY_TRACKER)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(hostname)).thenReturn(null)

        assertTrue(ngVpnNetworkStack.isDomainBlocked(DomainRR(hostname, uid)))
    }

    @Test
    fun whenIsDomainBlockedAndDomainIsNotTrackerThenReturnFalse() {
        val uid = 1200
        val hostname = TEST_APP_TRACKER.hostname

        whenever(appTrackerRepository.findTracker(eq(hostname), anyString())).thenReturn(AppTrackerType.NotTracker)
        whenever(vpnAppTrackerBlockingDao.getRuleByTrackerDomain(hostname)).thenReturn(null)

        assertFalse(ngVpnNetworkStack.isDomainBlocked(DomainRR(hostname, uid)))
    }

    private fun createDnsRecord(domain: String, address: String): DnsRR {
        return DnsRR(
            time = 0,
            qName = domain,
            aName = domain,
            resource = address,
            ttl = 0,
        )
    }

    companion object {
        private val TEST_APP_TRACKER = AppTracker(
            hostname = "api2.branch.com",
            trackerCompanyId = 0,
            owner = TrackerOwner(
                name = "Branch",
                displayName = "Branch",
            ),
            app = TrackerApp(0, 0.0),
            isCdn = false
        )

        private val THIRD_PARTY_TRACKER = AppTrackerType.ThirdParty(TEST_APP_TRACKER)
        private val FIRST_PARTY_TRACKER = AppTrackerType.FirstParty(TEST_APP_TRACKER)
    }
}
