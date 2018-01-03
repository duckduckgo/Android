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

import com.duckduckgo.app.privacymonitor.model.HttpsStatus
import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.trackerdetection.model.TrackerNetwork
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMonitorTest {

    companion object {
        private const val document = "http://example.com"
        private const val httpDocument = document
        private const val httpsDocument = "https://example.com"
        private const val malformedDocument = "[example com]"

        private const val trackerA = "http://standalonetrackerA.com/script.js"
        private const val trackerB = "http://standalonetrackerB.com/script.js"
        private const val networkATracker = "http://networkAtracker.com/script.js"
        private const val networkBTracker = "http://networkBtracker.com/script.js"
        private const val majorNetworkTracker = "http://majorNetworkTracker.com/script.js"

        private val networkA = TrackerNetwork("NetworkA", "networkA.com")
        private val networkB = TrackerNetwork("NetworkB", "networkB.com")
        private val majorNetwork = TrackerNetwork("MajorNetwork", "majorNetwork.com", "", 0, true)
    }

    @Test
    fun whenUrlIsHttpsThenHttpsStatusIsSecure() {
        val testee = SiteMonitor(httpsDocument, TermsOfService(), TrackerNetworks())
        assertEquals(HttpsStatus.SECURE, testee.https)
    }

    @Test
    fun whenUrlIsHttpThenHttpsStatusIsNone() {
        val testee = SiteMonitor(httpDocument, TermsOfService(), TrackerNetworks())
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenUrlIsHttpsWithHttpResourcesThenHttpsStatusIsMixed() {
        val testee = SiteMonitor(httpsDocument, TermsOfService(), TrackerNetworks())
        testee.hasHttpResources = true
        assertEquals(HttpsStatus.MIXED, testee.https)
    }

    @Test
    fun whenUrlIsMalformedThenHttpsStatusIsNone() {
        val testee = SiteMonitor(malformedDocument, TermsOfService(), TrackerNetworks())
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenIpTrackerDetectedThenHasObscureTrackerIsTrue() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, "http://54.229.105.203/abc", null, true))
        assertTrue(testee.hasObscureTracker)
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsCorrect() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        assertEquals(document, testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedWithoutTermsThenTermsAreGenerated() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        Assert.assertNotNull(testee.termsOfService)
    }

    @Test
    fun whenSiteMonitorCreatedWithTermsThenTermsAreSet() {
        val terms = TermsOfService()
        val testee = SiteMonitor(document, terms, TrackerNetworks())
        assertEquals(terms, testee.termsOfService)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenSiteMonitorCreatedThenNetworkCountIsZero() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        assertEquals(0, testee.networkCount)
    }

    @Test
    fun whenTrackersAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, trackerA, null, true))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, true))
        assertEquals(2, testee.trackerCount)
    }

    @Test
    fun whenUniqueTrackerNetworksAreDetectedThenNetworkCountIsIncrementedEachTime() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, networkATracker, networkA, true))
        testee.trackerDetected(TrackingEvent(document, networkBTracker, networkB, true))
        assertEquals(2, testee.networkCount)
    }

    @Test
    fun whenDuplicateTrackerNetworksDetectedThenNetworkCountIsIncrementedOnlyFirstTime() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, networkATracker, networkA, true))
        testee.trackerDetected(TrackingEvent(document, networkATracker, networkA, true))
        assertEquals(1, testee.networkCount)
    }

    @Test
    fun whenNonNetworkTrackersAreDetectedThenNetworkCountIsIncrementedForEachDomain() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, trackerA, null, true))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, true))
        assertEquals(2, testee.networkCount)
    }

    @Test
    fun whenMajorNetworkTrackerIsDetectedThenHasTrackerFromMajorNetworkIsTrue() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, majorNetworkTracker, majorNetwork, true))
        assertTrue(testee.hasTrackerFromMajorNetwork)
    }

    @Test
    fun whenNonMajorNetworkTrackerIsDetectedThenHasTrackerFromMajorNetworkIsFalse() {
        val testee = SiteMonitor(document, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(document, trackerA, networkA, true))
        Assert.assertFalse(testee.hasTrackerFromMajorNetwork)
    }
}