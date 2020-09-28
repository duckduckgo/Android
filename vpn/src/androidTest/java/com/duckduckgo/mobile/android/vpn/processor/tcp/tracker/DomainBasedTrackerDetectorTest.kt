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

import com.duckduckgo.mobile.android.vpn.processor.tcp.hostname.HostnameExtractor
import com.duckduckgo.mobile.android.vpn.processor.tcp.tracker.RequestTrackerType.*
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import xyz.hexene.localvpn.Packet
import xyz.hexene.localvpn.TCB
import java.nio.ByteBuffer

class DomainBasedTrackerDetectorTest {

    private lateinit var testee: DomainBasedTrackerDetector

    private val hostnameExtractor: HostnameExtractor = mock()
    private val tcb: TCB = mock()

    @Before
    fun setup() {
        testee = DomainBasedTrackerDetector(hostnameExtractor, TrackerListProvider().trackerList())
    }

    @Test
    fun whenHostnameIsNullThenTrackerTypeUndetermined() {
        givenExtractedHostname(null)
        val type = testee.determinePacketType(tcb, aPacket(), aPayload())
        assertTrue(type == Undetermined)
    }

    @Test
    fun whenHostnameIsNotInTrackerListThenTrackerTypeNotTracker() {
        givenExtractedHostname("duckduckgo.com")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload())
        assertTrue(type == NotTracker)
    }

    @Test
    fun whenHostnameInTrackerListExactMatchThenTrackerTypeTracker() {
        givenExtractedHostname("doubleclick.net")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload())
        assertTrue(type == Tracker)
    }

    @Test
    fun whenHostnameSuffixMatchesThenTrackerTypeTracker() {
        givenExtractedHostname("foo.bar.doubleclick.net")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload())
        assertTrue(type == Tracker)
    }

    @Test
    fun whenHostnameSuffixDoesNotMatchThenTrackerTypeTracker() {
        givenExtractedHostname("doubleclick.net.unmatched")
        val type = testee.determinePacketType(tcb, aPacket(), aPayload())
        assertTrue(type == NotTracker)
    }

    private fun givenExtractedHostname(desiredHostname: String?) {
        whenever(hostnameExtractor.extract(any(), any(), any())).thenReturn(desiredHostname)
    }

    private fun aPacket() = Packet(ByteBuffer.allocate(24))
    private fun aPayload() = ByteBuffer.allocate(0)
}