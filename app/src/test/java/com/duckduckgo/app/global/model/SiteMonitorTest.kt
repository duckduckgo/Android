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
import com.duckduckgo.app.global.model.SiteFactory.SitePrivacyData
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.PrivacyPractices
import com.duckduckgo.app.privacy.model.TestingEntity
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.dashboard.api.PrivacyShield.PROTECTED
import com.duckduckgo.privacy.dashboard.api.PrivacyShield.UNKNOWN
import com.duckduckgo.privacy.dashboard.api.PrivacyShield.UNPROTECTED
import com.duckduckgo.trackerdetection.model.Entity
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

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
        private val majorNetwork = TestingEntity("MajorNetwork", "MajorNetwork", 26.0)

        private val unknownPractices = PrivacyPractices.UNKNOWN
    }

    private val mockWhitelistDao: UserWhitelistDao = mock()

    private val mockContentBlocking: ContentBlocking = mock()

    @Test
    fun whenUrlIsHttpsThenHttpsStatusIsSecure() {
        val testee = SiteMonitor(
            url = httpsDocument,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(HttpsStatus.SECURE, testee.https)
    }

    @Test
    fun whenUrlIsHttpThenHttpsStatusIsNone() {
        val testee = SiteMonitor(
            url = httpDocument,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenUrlIsHttpsWithHttpResourcesThenHttpsStatusIsMixed() {
        val testee = SiteMonitor(
            url = httpsDocument,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        testee.hasHttpResources = true
        assertEquals(HttpsStatus.MIXED, testee.https)
    }

    @Test
    fun whenUrlIsMalformedThenHttpsStatusIsNone() {
        val testee = SiteMonitor(
            url = malformedDocument,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsCorrect() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(document, testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedWithTermsThenTermsAreSet() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(unknownPractices, testee.privacyPractices)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenTrackersAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        testee.trackerDetected(TrackingEvent(document, trackerA, null, null, true, null))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, null, true, null))
        assertEquals(2, testee.trackerCount)
    }

    @Test
    fun whenNonMajorNetworkTrackerIsDetectedThenMajorNetworkCountIsZero() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        testee.trackerDetected(TrackingEvent(document, trackerA, null, network, true, null))
        assertEquals(0, testee.majorNetworkCount)
    }

    @Test
    fun whenMajorNetworkTrackerIsDetectedThenMajorNetworkCountIsOne() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        testee.trackerDetected(TrackingEvent(document, majorNetworkTracker, null, majorNetwork, true, null))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenDuplicateMajorNetworkIsDetectedThenMajorNetworkCountIsStillOne() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        testee.trackerDetected(TrackingEvent(document, trackerA, null, majorNetwork, true, null))
        testee.trackerDetected(TrackingEvent(document, trackerB, null, majorNetwork, true, null))
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenSiteCreatedThenUpgradedHttpsIsFalse() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertFalse(testee.upgradedHttps)
    }

    @Test
    fun whenSiteCreatedThenSurrogatesSizeIsZero() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        assertEquals(0, testee.surrogates.size)
    }

    @Test
    fun whenSurrogatesAreDetectedThenSurrogatesListIsIncremented() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            userWhitelistDao = mockWhitelistDao,
            contentBlocking = mockContentBlocking,
            appCoroutineScope = TestScope()
        )
        testee.surrogateDetected(SurrogateResponse())
        assertEquals(1, testee.surrogates.size)
    }

    @Test
    fun whenSiteBelongsToUserAllowListThenPrivacyShieldIsUnprotected() {
        val testee = givenASiteMonitor(url = document)
        whenever(mockWhitelistDao.contains(document)).thenReturn(true)

        assertEquals(UNPROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenSiteIsHttptThenPrivacyShieldIsUnprotected() {
        val testee = givenASiteMonitor(url = httpDocument)

        assertEquals(UNPROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenSiteIsNotExceptionOrHttpButFullDetailsNotAvailableThenReturnUnkown() {
        val testee = givenASiteMonitor(url = httpsDocument)

        assertEquals(UNKNOWN, testee.privacyProtection())
    }

    @Test
    fun whenSiteIsMajorNetworkThenPrivacyShieldIsUnprotected() {
        val testee = givenASiteMonitor(url = httpsDocument)

        testee.updatePrivacyData(givenSitePrivacyData(entity = majorNetwork))

        assertEquals(UNPROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenPrivacyIssuesNotFoundThenPrivacyShieldIsProtected() {
        val testee = givenASiteMonitor(url = httpsDocument)

        testee.updatePrivacyData(givenSitePrivacyData(entity = network))

        assertEquals(PROTECTED, testee.privacyProtection())
    }

    fun givenASiteMonitor(
        url: String = document,
        title: String? = null,
        upgradedHttps: Boolean = false
    ) = SiteMonitor(
        url = url,
        title = title,
        upgradedHttps = upgradedHttps,
        userWhitelistDao = mockWhitelistDao,
        contentBlocking = mockContentBlocking,
        appCoroutineScope = TestScope()
    )

    fun givenSitePrivacyData(
        url: String = document,
        practices: PrivacyPractices.Practices = unknownPractices,
        entity: Entity? = null,
        prevalence: Double? = null
    ) = SitePrivacyData(
        url = url,
        practices = practices,
        entity = entity,
        prevalence = null
    )
}
