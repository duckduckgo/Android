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

package com.duckduckgo.app.global.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestingEntity
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
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

        private val network = TestingEntity("Network", "Network", 1.0)
        private val majorNetwork = TestingEntity("MajorNetwork", "MajorNetwork", 10.0)

        private val unknownPractices = PrivacyPractices.UNKNOWN
    }

    @Test
    fun whenUrlIsHttpsThenHttpsStatusIsSecure() {
        val testee = SiteMonitor(httpsDocument, null)
        assertEquals(HttpsStatus.SECURE, testee.https)
    }

    @Test
    fun whenUrlIsHttpThenHttpsStatusIsNone() {
        val testee = SiteMonitor(httpDocument, null)
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenUrlIsHttpsWithHttpResourcesThenHttpsStatusIsMixed() {
        val testee = SiteMonitor(httpsDocument, null)
        testee.hasHttpResources = true
        assertEquals(HttpsStatus.MIXED, testee.https)
    }

    @Test
    fun whenUrlIsMalformedThenHttpsStatusIsNone() {
        val testee = SiteMonitor(malformedDocument, null)
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsCorrect() {
        val testee = SiteMonitor(document, null)
        assertEquals(document, testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedWithTermsThenTermsAreSet() {
        val testee = SiteMonitor(document, null)
        assertEquals(unknownPractices, testee.privacyPractices)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(document, null)
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenTrackersAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(document, null)
        testee.trackerDetected(TrackingEvent(document, trackerA, null, null, true, null))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, null, true, null))
        assertEquals(2, testee.trackerCount)
    }

    @Test
    fun whenNonMajorNetworkTrackerIsDetectedThenMajorNetworkCoutnIsZero() {
        val testee = SiteMonitor(document, null)
        testee.trackerDetected(TrackingEvent(document, trackerA, null, network, true, null))
        assertEquals(0, testee.majorNetworkCount)
    }

    @Test
    fun whenMajorNetworkTrackerIsDetectedThenMajorNetworkCountIsOne() {
        val testee = SiteMonitor(document, null)
        testee.trackerDetected(TrackingEvent(document, majorNetworkTracker, null, majorNetwork, true, null))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenDuplicateMajorNetworkIsDetectedThenMajorNetworkCountIsStillOne() {
        val testee = SiteMonitor(document, null)
        testee.trackerDetected(TrackingEvent(document, trackerA, null, majorNetwork, true, null))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, majorNetwork, true, null))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenSiteCreatedThenUpgradedHttpsIsFalse() {
        val testee = SiteMonitor(document, null)
        assertFalse(testee.upgradedHttps)
    }

    @Test
    fun whenSiteCreatedThenSurrogatesSizeIsZero() {
        val testee = SiteMonitor(document, null)
        assertEquals(0, testee.surrogates.size)
    }

    @Test
    fun whenSurrogatesAreDetectedThenSurrogatesListIsIncremented() {
        val testee = SiteMonitor(document, null)
        testee.surrogateDetected(SurrogateResponse())
        assertEquals(1, testee.surrogates.size)
    }
}
