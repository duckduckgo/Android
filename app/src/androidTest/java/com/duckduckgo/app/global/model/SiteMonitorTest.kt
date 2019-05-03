/*
 * Copyright (c) 2018 DuckDuckGo
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

package com.duckduckgo.app.global.model

import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.store.PrevalenceStore
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.nhaarman.mockitokotlin2.mock
import org.junit.Assert.assertEquals
import org.junit.Test

class SiteMonitorTest {

    companion object {
        private const val document = "http://example.com"
        private const val httpDocument = document
        private const val httpsDocument = "https://example.com"
        private const val malformedDocument = "[example com]"

        private const val trackerA = "http://standalonetrackerA.com/script.js"
        private const val trackerB = "http://standalonetrackerB.com/script.js"
        private const val trackerC = "http://standalonetrackerC.com/script.js"

        private const val majorNetworkTracker = "http://majorNetworkTracker.com/script.js"

        private val networkA = TrackerNetwork("NetworkA", "networkA.com")
        private val majorNetwork = TrackerNetwork("MajorNetwork", "majorNetwork.com", true)

        private val unknownPractices = PrivacyPractices.UNKNOWN
    }

    private var mockPrevalenceStore: PrevalenceStore = mock()

    @Test
    fun whenUrlIsHttpsThenHttpsStatusIsSecure() {
        val testee = SiteMonitor(httpsDocument, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(HttpsStatus.SECURE, testee.https)
    }

    @Test
    fun whenUrlIsHttpThenHttpsStatusIsNone() {
        val testee = SiteMonitor(httpDocument, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenUrlIsHttpsWithHttpResourcesThenHttpsStatusIsMixed() {
        val testee = SiteMonitor(httpsDocument, null, prevalenceStore = mockPrevalenceStore)
        testee.hasHttpResources = true
        assertEquals(HttpsStatus.MIXED, testee.https)
    }

    @Test
    fun whenUrlIsMalformedThenHttpsStatusIsNone() {
        val testee = SiteMonitor(malformedDocument, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsCorrect() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(document, testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedWithTermsThenTermsAreSet() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(unknownPractices, testee.privacyPractices)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenTrackersAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        testee.trackerDetected(TrackingEvent(document, trackerA, null, true))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, true))
        assertEquals(2, testee.trackerCount)
    }

    @Test
    fun whenNonMajorNetworkTrackerIsDetectedThenMajorNetworkCoutnIsZero() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        testee.trackerDetected(TrackingEvent(document, trackerA, networkA, true))
        assertEquals(0, testee.majorNetworkCount)
    }

    @Test
    fun whenMajorNetworkTrackerIsDetectedThenMajorNetworkCountIsOne() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        testee.trackerDetected(TrackingEvent(document, majorNetworkTracker, majorNetwork, true))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenDuplicateMajorNetworkIsDetectedThenMajorNetworkCountIsStillOne() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        testee.trackerDetected(TrackingEvent(document, trackerA, majorNetwork, true))
        testee.trackerDetected(TrackingEvent(document, trackerB, majorNetwork, true))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenNoTrackersDetectedThenDistinctTrackerByNetworkIsEmpty() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)
        assertEquals(0, testee.distinctTrackersByNetwork.size)
    }

    @Test
    fun whenTrackersDetectedThenDistinctTrackersByNetworkMapsTrackerByNetworkOrHost() {
        val testee = SiteMonitor(document, null, prevalenceStore = mockPrevalenceStore)

        // Two distinct trackers, trackerA and tracker B for network A
        testee.trackerDetected(TrackingEvent(document, trackerA, networkA, true))
        testee.trackerDetected(TrackingEvent(document, trackerA, networkA, true))
        testee.trackerDetected(TrackingEvent(document, trackerB, networkA, true))
        testee.trackerDetected(TrackingEvent(document, trackerB, networkA, true))

        // One distinct trackerC with no network
        testee.trackerDetected(TrackingEvent(document, trackerC, null, true))
        testee.trackerDetected(TrackingEvent(document, trackerC, null, true))

        val result = testee.distinctTrackersByNetwork
        assertEquals(2, result["NetworkA"]!!.size)
        assertEquals(1, result["standalonetrackerC.com"]!!.size)
    }
}