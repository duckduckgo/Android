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

import com.duckduckgo.app.privacymonitor.model.TermsOfService
import com.duckduckgo.app.privacymonitor.model.HttpsStatus
import com.duckduckgo.app.trackerdetection.model.TrackerNetworks
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMonitorInstrumentationTest {

    companion object {
        private const val httpDocument = "http://example.com"
        private const val httpsDocument = "https://example.com"
        private const val malformedDocument = "[example com]"
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
        val testee = SiteMonitor(httpDocument, TermsOfService(), TrackerNetworks())
        testee.trackerDetected(TrackingEvent(httpDocument, "http://54.229.105.203/abc", null, true))
        assertTrue(testee.hasObscureTracker)
    }

}