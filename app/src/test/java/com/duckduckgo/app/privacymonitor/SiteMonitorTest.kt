/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.privacymonitor


import com.duckduckgo.app.trackerdetection.model.NetworkTrackers
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Test


class SiteMonitorTest {

    private val mockNetworkTrackers: NetworkTrackers = mock()

    companion object {
        private const val document = "http://example.com"
        private const val tracker = "http://standalonetracker.com/script.js"
        private const val networkATracker = "http://networkAtracker.com/script.js"
        private const val networkBTracker = "http://networkBtracker.com/script.js"
        private const val majorNetworkATracker = "http://majorNetworkAtracker.com/script.js"
        private const val majorNetworkBTracker = "http://majorNetworkBtracker.com/script.js"
    }

    init {
        whenever(mockNetworkTrackers.network(tracker)).thenReturn(null)
        whenever(mockNetworkTrackers.network(networkATracker)).thenReturn("NetworkA")
        whenever(mockNetworkTrackers.network(networkBTracker)).thenReturn("NetworkB")
        whenever(mockNetworkTrackers.majorNetwork(majorNetworkATracker)).thenReturn("MajorNetworkA")
        whenever(mockNetworkTrackers.majorNetwork(majorNetworkBTracker)).thenReturn("MajorNetworkB")
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsCorrect() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        assertEquals(document, testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenSiteMonitorCreatedThenNetworkCountIsZero() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        assertEquals(0, testee.networkCount)
    }

    @Test
    fun whenTrackersAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(tracker, document, true))
        testee.trackerDetected(TrackingEvent(tracker, document, true))
        assertEquals(2, testee.trackerCount)
    }

    @Test
    fun whenUniqueTrackerNetworksAreDetectedThenNetworkCountIsIncrementedEachTime() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(networkATracker, document, true))
        testee.trackerDetected(TrackingEvent(networkBTracker, document, true))
        assertEquals(2, testee.networkCount)
    }

    @Test
    fun whenDuplicateTrackerNetworksDetectedThenNetworkCountIsIncrementedOnlyFirstTime() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(networkATracker, document, true))
        testee.trackerDetected(TrackingEvent(networkATracker, document, true))
        assertEquals(1, testee.networkCount)
    }

    @Test
    fun whenNonNetworkTrackersAreDetectedThenNetworkCountIsNotIncremented() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(tracker, document, true))
        testee.trackerDetected(TrackingEvent(tracker, document, true))
        assertEquals(0, testee.networkCount)
    }

    @Test
    fun whenUniqueMajorTrackerNetworksAreDetectedThenMajorNetworkCountIsIncrementedEachTime() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(majorNetworkATracker, document, true))
        testee.trackerDetected(TrackingEvent(majorNetworkBTracker, document, true))
        assertEquals(2, testee.majorNetworkCount)
    }

    @Test
    fun whenDuplicateMajorTrackerNetworksDetectedThenNetworkCountIsIncrementedOnlyFirstTime() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(majorNetworkATracker, document, true))
        testee.trackerDetected(TrackingEvent(majorNetworkATracker, document, true))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenNonMajorNetworkTrackersAreDetectedThenMajorNetworkCountIsNotIncremented() {
        val testee = SiteMonitor(document, mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(tracker, document, true))
        testee.trackerDetected(TrackingEvent(tracker, document, true))
        assertEquals(0, testee.majorNetworkCount)
    }
}