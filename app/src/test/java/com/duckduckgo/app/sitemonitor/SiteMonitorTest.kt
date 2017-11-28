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

package com.duckduckgo.app.sitemonitor


import com.duckduckgo.app.trackerdetection.model.NetworkTrackers
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test


class SiteMonitorTest {

    private val mockNetworkTrackers: NetworkTrackers = mock()

    companion object {
        private const val documentUrl = "http://example.com"
        private const val tracker = "http://standalonetracker.com/script.js"
        private const val networkATracker = "http://networkAtracker.com/script.js"
        private const val networkBTracker = "http://networkBtracker.com/script.js"
    }

    init {
        whenever(mockNetworkTrackers.network(tracker)).thenReturn(null)
        whenever(mockNetworkTrackers.network(networkATracker)).thenReturn("NetworkA")
        whenever(mockNetworkTrackers.network(networkBTracker)).thenReturn("NetworkB")
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsNull() {
        val testee = SiteMonitor(mockNetworkTrackers)
        assertNull(testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(mockNetworkTrackers)
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenSiteMonitorCreatedThenNetworkCountIsZero() {
        val testee = SiteMonitor(mockNetworkTrackers)
        assertEquals(0, testee.trackerNetworkCount)
    }

    @Test
    fun whenUrlIsSetThenUrlIsUpdated() {
        val testee = SiteMonitor(mockNetworkTrackers)
        testee.url = documentUrl
        assertEquals(documentUrl, testee.url)
    }

    @Test
    fun whenTrackersAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(tracker, documentUrl, true))
        testee.trackerDetected(TrackingEvent(tracker, documentUrl, true))
        assertEquals(2, testee.trackerCount)
    }

    @Test
    fun whenUniqueTrackerNetworksAreDetectedThenTrackerNetworkCountIsIncrementedEachTime() {
        val testee = SiteMonitor(mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(networkATracker, documentUrl, true))
        testee.trackerDetected(TrackingEvent(networkBTracker, documentUrl, true))
        assertEquals(2, testee.trackerNetworkCount)
    }

    @Test
    fun whenDuplicateTrackerNetworksDetectedThenTrackerNetworkCountIsIncrementedOnlyFirstTime() {
        val testee = SiteMonitor(mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(networkATracker, documentUrl, true))
        testee.trackerDetected(TrackingEvent(networkATracker, documentUrl, true))
        assertEquals(1, testee.trackerNetworkCount)
    }

    @Test
    fun whenNonNetworkTrackersAreDetectedThenTrackerNetworkCountIsNotIncremented() {
        val testee = SiteMonitor(mockNetworkTrackers)
        testee.trackerDetected(TrackingEvent(tracker, documentUrl, true))
        testee.trackerDetected(TrackingEvent(tracker, documentUrl, true))
        assertEquals(0, testee.trackerNetworkCount)
    }
}