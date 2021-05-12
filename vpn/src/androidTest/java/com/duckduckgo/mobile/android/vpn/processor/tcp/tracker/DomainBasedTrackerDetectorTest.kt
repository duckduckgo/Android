/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.mobile.android.vpn.processor.tcp.tracker

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.mobile.android.vpn.pixels.DeviceShieldPixels
import com.duckduckgo.mobile.android.vpn.processor.requestingapp.AppNameResolver
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType.*
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.duckduckgo.mobile.android.vpn.trackers.*
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType.FirstParty
import com.duckduckgo.mobile.android.vpn.trackers.AppTrackerType.ThirdParty
import com.jakewharton.threetenabp.AndroidThreeTen
import com.nhaarman.mockitokotlin2.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.threeten.bp.zone.ZoneRulesProvider
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer

class DomainBasedTrackerDetectorTest {

    private lateinit var testee: DomainBasedTrackerDetector

    private val hostnameExtractor: HostnameExtractor = mock()
    private lateinit var vpnDatabase: VpnDatabase
    private val deviceShieldPixels: DeviceShieldPixels = mock()
    private val tcb: TCB = mock()
    private val appTrackerRepository: AppTrackerRepository = mock()
    private val defaultOriginatingApp = AppNameResolver.OriginatingApp("foo.id.com", "Foo App")

    @Before
    fun setup() {

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        AndroidThreeTen.init(appContext)
        GlobalScope.launch(Dispatchers.Main) {
            ZoneRulesProvider.getAvailableZoneIds()
        }

        vpnDatabase = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        testee = DomainBasedTrackerDetector(deviceShieldPixels, hostnameExtractor, appTrackerRepository, vpnDatabase)
    }

    @Test
    fun whenHostnameIsNullThenTrackerTypeUndetermined() {
        givenExtractedHostname(null)
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), defaultOriginatingApp)
        assertTrue(type == Undetermined)
    }

    @Test
    fun whenHostnameIsNotInTrackerListThenTrackerTypeNotTracker() {
        "duckduckgo.com".also { givenExtractedHostname(it) }
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), defaultOriginatingApp)
        type.assertNotTracker()
    }

    @Test
    fun whenTrackerType3rdPartyThenTrackerBlocked() {
        val trackerDomain = "doubleclick.net".also {
            givenExtractedHostname(it)
            whenever(appTrackerRepository.findTracker(eq(it), any())).thenReturn(aThirdPartyTracker(it))
        }
        testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), defaultOriginatingApp)
        assertTrue(tcb.isTracker)
        assertTrue(tcb.trackerTypeDetermined)
        assertEquals(trackerDomain, tcb.trackerHostName)
        verify(deviceShieldPixels).trackerBlocked()
    }

    @Test
    fun whenTrackerType1stPartyThenTrackerNotBlocked() {
        "doubleclick.net".also {
            givenExtractedHostname(it)
            whenever(appTrackerRepository.findTracker(eq(it), any())).thenReturn(aFirstPartyTracker(it))
        }
        testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), defaultOriginatingApp)
        assertFalse(tcb.isTracker)
        verify(deviceShieldPixels, never()).trackerBlocked()
    }

    @Test
    fun whenTrackerTypeNotATrackerThenTrackerNotBlocked() {
        "doubleclick.net".also {
            givenExtractedHostname(it)
            whenever(appTrackerRepository.findTracker(eq(it), any())).thenReturn(AppTrackerType.NotTracker)
        }
        testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), defaultOriginatingApp)
        assertFalse(tcb.isTracker)
        verify(deviceShieldPixels, never()).trackerBlocked()
    }

    @Test
    fun whenHostnameSuffixDoesNotMatchThenTrackerTypeNotTracker() {
        givenExtractedHostname("doubleclick.net.unmatched")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), defaultOriginatingApp)
        type.assertNotTracker()
    }

    @Test
    fun whenIsLocalAddressThenTrackerTypeNotTracker() {
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aLocalAddress(), defaultOriginatingApp)
        type.assertNotTracker()
    }

    @Test
    fun whenTrackerDetectedAndFindsMatchingExceptionRuleThenReturnNottracker() {
        vpnDatabase.vpnAppTrackerBlockingDao().insertTrackerExceptionRules(
            listOf(
                AppTrackerExceptionRule(rule = "doubleclick.net", listOf("foo.id.com"))
            )
        )

        "doubleclick.net".also { givenExtractedHostname(it) }
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress(), AppNameResolver.OriginatingApp("foo.id.com", "Foo App"))
        type.assertNotTracker()
    }

    private fun givenExtractedHostname(desiredHostname: String?) {
        whenever(hostnameExtractor.extract(any(), any(), any())).thenReturn(desiredHostname)
    }

    private fun RequestTrackerType.assertNotTracker() {
        assertTrue(this is NotTracker)
    }

    private fun aPacket() = Packet(ByteBuffer.allocate(24))
    private fun aPayload() = ByteBuffer.allocate(0)
    private fun aLocalAddress() = true
    private fun aRemoteAddress() = false

    private fun aThirdPartyTracker(trackerDomain: String) = ThirdParty(anAppTracker(trackerDomain))
    private fun aFirstPartyTracker(trackerDomain: String) = FirstParty(anAppTracker(trackerDomain))

    private fun anAppTracker(trackerDomain: String) = AppTracker(
        trackerDomain,
        0,
        TrackerOwner("", ""),
        TrackerApp(0, 0.0),
        false
    )

}
