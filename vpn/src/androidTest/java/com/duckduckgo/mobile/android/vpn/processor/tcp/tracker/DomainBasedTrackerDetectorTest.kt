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
import com.duckduckgo.mobile.android.vpn.dao.VpnTrackerDao
import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType.*
import com.duckduckgo.mobile.android.vpn.store.VpnDatabase
import com.jakewharton.threetenabp.AndroidThreeTen
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertTrue
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
    private lateinit var trackerDao: VpnTrackerDao
    private val tcb: TCB = mock()

    @Before
    fun setup() {

        val appContext = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        AndroidThreeTen.init(appContext)
        GlobalScope.launch(Dispatchers.Main) {
            ZoneRulesProvider.getAvailableZoneIds()
        }

        vpnDatabase = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, VpnDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        trackerDao = vpnDatabase.vpnTrackerDao()

        testee = DomainBasedTrackerDetector(hostnameExtractor, TrackerListProvider(), vpnDatabase)
    }

    @Test
    fun whenHostnameIsNullThenTrackerTypeUndetermined() {
        givenExtractedHostname(null)
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress())
        assertTrue(type == Undetermined)
    }

    @Test
    fun whenHostnameIsNotInTrackerListThenTrackerTypeNotTracker() {
        givenExtractedHostname("duckduckgo.com")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress())
        assertTrue(type == NotTracker)
    }

    @Test
    fun whenHostnameInTrackerListExactMatchThenTrackerTypeTracker() {
        givenExtractedHostname("doubleclick.net")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress())
        assertTrue(type == Tracker)
    }

    @Test
    fun whenHostnameSuffixMatchesThenTrackerTypeTracker() {
        givenExtractedHostname("foo.bar.doubleclick.net")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress())
        assertTrue(type == Tracker)
    }

    @Test
    fun whenHostnameSuffixDoesNotMatchThenTrackerTypeNotTracker() {
        givenExtractedHostname("doubleclick.net.unmatched")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aRemoteAddress())
        assertTrue(type == NotTracker)
    }

    @Test
    fun whenIsLocalAddressThenTrackerTypeNotTracker() {
        val type = testee.determinePacketType(tcb, aPacket(), aPayload(), aLocalAddress())
        assertTrue(type == NotTracker)
    }

    private fun givenExtractedHostname(desiredHostname: String?) {
        whenever(hostnameExtractor.extract(any(), any(), any())).thenReturn(desiredHostname)
    }

    private fun aPacket() = Packet(ByteBuffer.allocate(24))
    private fun aPayload() = ByteBuffer.allocate(0)
    private fun aLocalAddress() = true
    private fun aRemoteAddress() = false
}