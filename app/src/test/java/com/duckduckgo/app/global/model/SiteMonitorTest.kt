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
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.browser.omnibar.StandardizedLeadingIconFeatureToggle
import com.duckduckgo.app.global.model.PrivacyShield.PROTECTED
import com.duckduckgo.app.global.model.PrivacyShield.UNKNOWN
import com.duckduckgo.app.global.model.PrivacyShield.UNPROTECTED
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.privacy.model.HttpsStatus
import com.duckduckgo.app.privacy.model.TestingEntity
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.config.api.ContentBlocking
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class SiteMonitorTest {
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    companion object {
        private const val document = "http://example.com"
        private const val httpDocument = document
        private const val httpsDocument = "https://example.com"
        private const val malformedDocument = "[example com]"

        private const val trackerA = "http://standalonetrackerA.com/script.js"
        private const val trackerB = "http://standalonetrackerB.com/script.js"
        private const val trackerC = "http://standalonetrackerC.com/script.js"
        private const val trackerD = "http://standalonetrackerD.com/script.js"
        private const val trackerE = "http://standalonetrackerE.com/script.js"
        private const val trackerF = "http://standalonetrackerF.com/script.js"

        private const val majorNetworkTracker = "http://majorNetworkTracker.com/script.js"

        private val network = TestingEntity("Network", "Network", 1.0)
        private val majorNetwork = TestingEntity("MajorNetwork", "MajorNetwork", Entity.MAJOR_NETWORK_PREVALENCE + 1)
    }

    private val mockAllowListRepository: UserAllowListRepository = mock()

    private val mockContentBlocking: ContentBlocking = mock()

    private val mockBrokenSiteContext: BrokenSiteContext = mock()

    private val mockDuckPlayer: DuckPlayer = mock()

    private val mockBypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository = mock()

    private val mockStandardizedLeadingIconToggle: StandardizedLeadingIconFeatureToggle = mock()

    @Before
    fun setup() {
        whenever(mockDuckPlayer.isDuckPlayerUri(any())).thenReturn(false)
        // Default to disabled (legacy behavior) for existing tests
        val mockToggle: com.duckduckgo.feature.toggles.api.Toggle = mock()
        whenever(mockToggle.isEnabled()).thenReturn(false)
        whenever(mockStandardizedLeadingIconToggle.self()).thenReturn(mockToggle)
    }

    @Test
    fun whenUrlIsHttpsThenHttpsStatusIsSecure() {
        val testee = SiteMonitor(
            url = httpsDocument,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertEquals(HttpsStatus.SECURE, testee.https)
    }

    @Test
    fun whenUrlIsHttpThenHttpsStatusIsNone() {
        val testee = SiteMonitor(
            url = httpDocument,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenUrlIsHttpsWithHttpResourcesThenHttpsStatusIsMixed() {
        val testee = SiteMonitor(
            url = httpsDocument,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.hasHttpResources = true
        assertEquals(HttpsStatus.MIXED, testee.https)
    }

    @Test
    fun whenUrlIsMalformedThenHttpsStatusIsNone() {
        val testee = SiteMonitor(
            url = malformedDocument,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertEquals(HttpsStatus.NONE, testee.https)
    }

    @Test
    fun whenSiteMonitorCreatedThenUrlIsCorrect() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertEquals(document, testee.url)
    }

    @Test
    fun whenSiteMonitorCreatedThenTrackerCountIsZero() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertEquals(0, testee.trackerCount)
    }

    @Test
    fun whenTrackersBlockedOrAllowedByUserAreDetectedThenTrackerCountIsIncremented() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerB,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.USER_ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerC,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        assertEquals(1, testee.trackerCount)
    }

    @Test
    fun whenNoTrackersAllowedByUserAreDetectedThenAllTrackersBlockedIsTrue() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerB,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerC,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        Assert.assertTrue(testee.allTrackersBlocked)
    }

    @Test
    fun whenAtLeastOneTrackersAllowedByUserIsDetectedThenAllTrackersBlockedIsFalse() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerB,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.USER_ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerC,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        assertFalse(testee.allTrackersBlocked)
    }

    @Test
    fun whenNonMajorNetworkTrackerIsDetectedThenMajorNetworkCountIsZero() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = network,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        assertEquals(0, testee.majorNetworkCount)
    }

    @Test
    fun whenMajorNetworkTrackerIsDetectedThenMajorNetworkCountIsOne() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = majorNetworkTracker,
                categories = null,
                entity = majorNetwork,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenDuplicateMajorNetworkIsDetectedThenMajorNetworkCountIsStillOne() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = majorNetwork,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerB,
                categories = null,
                entity = majorNetwork,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        assertEquals(1, testee.majorNetworkCount)
    }

    @Test
    fun whenSiteCreatedThenUpgradedHttpsIsFalse() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertFalse(testee.upgradedHttps)
    }

    @Test
    fun whenSiteCreatedThenSurrogatesSizeIsZero() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        assertEquals(0, testee.surrogates.size)
    }

    @Test
    fun whenSurrogatesAreDetectedThenSurrogatesListIsIncremented() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.surrogateDetected(SurrogateResponse())
        assertEquals(1, testee.surrogates.size)
    }

    @Test
    fun whenOtherDomainsAreLoadedThenOtherDomainsLoadedCountIsIncremented() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.AD,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerB,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerC,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        assertEquals(2, testee.otherDomainsLoadedCount)
    }

    @Test
    fun whenSpecialDomainsAreLoadedThenSpecialDomainsLoadedCountIsIncremented() {
        val testee = SiteMonitor(
            url = document,
            title = null,
            externalLaunch = false,
            userAllowListRepository = mockAllowListRepository,
            contentBlocking = mockContentBlocking,
            bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
            appCoroutineScope = coroutineRule.testScope,
            dispatcherProvider = coroutineRule.testDispatcherProvider,
            brokenSiteContext = mockBrokenSiteContext,
            duckPlayer = mockDuckPlayer,
            standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerA,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.AD_ALLOWED,
                type = TrackerType.AD,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerB,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.SITE_BREAKAGE_ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerC,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.SAME_ENTITY_ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerD,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.USER_ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerE,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.BLOCKED,
                type = TrackerType.OTHER,
            ),
        )
        testee.trackerDetected(
            TrackingEvent(
                documentUrl = document,
                trackerUrl = trackerF,
                categories = null,
                entity = null,
                surrogateId = null,
                status = TrackerStatus.ALLOWED,
                type = TrackerType.OTHER,
            ),
        )
        assertEquals(4, testee.specialDomainsLoadedCount)
    }

    @Test
    fun whenSiteBelongsToUserAllowListThenPrivacyShieldIsUnprotected() {
        val testee = givenASiteMonitor(url = document)
        whenever(mockAllowListRepository.isDomainInUserAllowList(document)).thenReturn(true)

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
    fun whenSiteIsMajorNetworkThenPrivacyShieldIsProtected() {
        val testee = givenASiteMonitor(url = httpsDocument)

        testee.updatePrivacyData(givenSitePrivacyData(entity = majorNetwork))

        assertEquals(PROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenPrivacyIssuesNotFoundThenPrivacyShieldIsProtected() {
        val testee = givenASiteMonitor(url = httpsDocument)

        testee.updatePrivacyData(givenSitePrivacyData(entity = network))

        assertEquals(PROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenUserBypassedSslCertificateThenPrivacyShieldIsUnprotected() {
        val testee = givenASiteMonitor(url = document)
        whenever(mockBypassedSSLCertificatesRepository.contains(document)).thenReturn(false)

        assertEquals(UNPROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenToggleDisabledAndSiteIsRemoteConfigExceptionThenPrivacyShieldIsUnprotected() {
        // Legacy behavior: remote config exceptions show UNPROTECTED
        val testee = givenASiteMonitor(url = httpsDocument)
        whenever(mockAllowListRepository.isDomainInUserAllowList("example.com")).thenReturn(false)
        whenever(mockContentBlocking.isAnException("example.com")).thenReturn(true)

        assertEquals(UNPROTECTED, testee.privacyProtection())
    }

    @Test
    fun whenToggleEnabledAndSiteIsRemoteConfigExceptionThenPrivacyShieldIsNotUnprotected() {
        // New behavior: remote config exceptions do NOT show UNPROTECTED
        val enabledToggle: com.duckduckgo.feature.toggles.api.Toggle = mock()
        whenever(enabledToggle.isEnabled()).thenReturn(true)
        whenever(mockStandardizedLeadingIconToggle.self()).thenReturn(enabledToggle)

        val testee = givenASiteMonitor(url = httpsDocument)
        whenever(mockAllowListRepository.isDomainInUserAllowList("example.com")).thenReturn(false)
        whenever(mockContentBlocking.isAnException("example.com")).thenReturn(true)

        // Should return UNKNOWN (not UNPROTECTED) because fullSiteDetailsAvailable is false
        assertEquals(UNKNOWN, testee.privacyProtection())
    }

    @Test
    fun whenToggleEnabledAndSiteIsUserAllowListedThenPrivacyShieldIsUnprotected() {
        // Even with toggle enabled, user-initiated allowlist still shows UNPROTECTED
        val enabledToggle: com.duckduckgo.feature.toggles.api.Toggle = mock()
        whenever(enabledToggle.isEnabled()).thenReturn(true)
        whenever(mockStandardizedLeadingIconToggle.self()).thenReturn(enabledToggle)

        val testee = givenASiteMonitor(url = httpsDocument)
        whenever(mockAllowListRepository.isDomainInUserAllowList("example.com")).thenReturn(true)

        assertEquals(UNPROTECTED, testee.privacyProtection())
    }

    private fun givenASiteMonitor(
        url: String = document,
        title: String? = null,
        upgradedHttps: Boolean = false,
        externalLaunch: Boolean = false,
    ) = SiteMonitor(
        url = url,
        title = title,
        upgradedHttps = upgradedHttps,
        externalLaunch = externalLaunch,
        userAllowListRepository = mockAllowListRepository,
        contentBlocking = mockContentBlocking,
        bypassedSSLCertificatesRepository = mockBypassedSSLCertificatesRepository,
        appCoroutineScope = coroutineRule.testScope,
        dispatcherProvider = coroutineRule.testDispatcherProvider,
        brokenSiteContext = mockBrokenSiteContext,
        duckPlayer = mockDuckPlayer,
        standardizedLeadingIconToggle = mockStandardizedLeadingIconToggle,
    )

    private fun givenSitePrivacyData(
        url: String = document,
        entity: Entity? = null,
        prevalence: Double? = null,
    ) = SitePrivacyData(
        url = url,
        entity = entity,
        prevalence = null,
    )
}
