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

package com.duckduckgo.app.brokensite

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.browser.certificates.BypassedSSLCertificatesRepository
import com.duckduckgo.app.browser.omnibar.StandardizedLeadingIconFeatureToggle
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteMonitor
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.api.brokensite.BrokenSiteContext
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.browser.api.brokensite.BrokenSiteData.ReportFlow.MENU
import com.duckduckgo.browser.api.brokensite.BrokenSiteOpenerContext
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.duckplayer.api.DuckPlayer
import com.duckduckgo.privacy.config.api.ContentBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class BrokenSiteDataTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockAllowListRepository: UserAllowListRepository = mock()

    private val mockContentBlocking: ContentBlocking = mock()
    private val mockBrokenSiteContext: BrokenSiteContext = mock()
    private val mockDuckPlayer: DuckPlayer = mock()
    private val mockBypassedSSLCertificatesRepository: BypassedSSLCertificatesRepository = mock()
    private val mockStandardizedLeadingIconToggle: StandardizedLeadingIconFeatureToggle = mock()

    @Before
    fun setup() {
        whenever(mockDuckPlayer.isDuckPlayerUri(any())).thenReturn(false)
        val mockToggle: com.duckduckgo.feature.toggles.api.Toggle = mock()
        whenever(mockToggle.isEnabled()).thenReturn(false)
        whenever(mockStandardizedLeadingIconToggle.self()).thenReturn(mockToggle)
    }

    @Test
    fun whenSiteIsNullThenDataIsEmptyAndUpgradedIsFalse() {
        val data = BrokenSiteData.fromSite(null, reportFlow = MENU)
        assertTrue(data.url.isEmpty())
        assertTrue(data.blockedTrackers.isEmpty())
        assertTrue(data.surrogates.isEmpty())
        assertFalse(data.upgradedToHttps)
    }

    @Test
    fun whenSiteExistsThenDataContainsUrl() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertEquals(SITE_URL, data.url)
    }

    @Test
    fun whenSiteUpgradedThenHttpsUpgradedIsTrue() {
        val site = buildSite(SITE_URL, httpsUpgraded = true)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertTrue(data.upgradedToHttps)
    }

    @Test
    fun whenSiteNotUpgradedThenHttpsUpgradedIsFalse() {
        val site = buildSite(SITE_URL, httpsUpgraded = false)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertFalse(data.upgradedToHttps)
    }

    @Test
    fun whenUrlParametersRemovedThenUrlParametersRemovedIsTrue() {
        val site = buildSite(SITE_URL)
        site.urlParametersRemoved = true
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertTrue(data.urlParametersRemoved)
    }

    @Test
    fun whenUrlParametersNotRemovedThenUrlParametersRemovedIsFalse() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertFalse(data.urlParametersRemoved)
    }

    @Test
    fun whenSiteHasNoTrackersThenBlockedTrackersIsEmpty() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertTrue(data.blockedTrackers.isEmpty())
    }

    @Test
    fun whenSiteHasBlockedTrackersThenBlockedTrackersExist() {
        val site = buildSite(SITE_URL)
        val event = TrackingEvent(
            documentUrl = "http://www.example.com",
            trackerUrl = "http://www.tracker.com/tracker.js",
            categories = emptyList(),
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val anotherEvent = TrackingEvent(
            documentUrl = "http://www.example.com/test",
            trackerUrl = "http://www.anothertracker.com/tracker.js",
            categories = emptyList(),
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )
        site.trackerDetected(event)
        site.trackerDetected(anotherEvent)
        assertEquals("tracker.com", BrokenSiteData.fromSite(site, reportFlow = MENU).blockedTrackers)
    }

    @Test
    fun whenSiteHasSameHostBlockedTrackersThenOnlyUniqueTrackersIncludedInData() {
        val site = buildSite(SITE_URL)
        val event = TrackingEvent(
            documentUrl = "http://www.example.com",
            trackerUrl = "http://www.tracker.com/tracker.js",
            categories = emptyList(),
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val anotherEvent = TrackingEvent(
            documentUrl = "http://www.example.com/test",
            trackerUrl = "http://www.tracker.com/tracker2.js",
            categories = emptyList(),
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        site.trackerDetected(event)
        site.trackerDetected(anotherEvent)
        assertEquals("tracker.com", BrokenSiteData.fromSite(site, reportFlow = MENU).blockedTrackers)
    }

    @Test
    fun whenSiteHasBlockedCnamedTrackersThenBlockedTrackersExist() {
        val site = buildSite(SITE_URL)
        val event = TrackingEvent(
            documentUrl = "http://www.example.com",
            trackerUrl = ".tracker.com/tracker.js",
            categories = emptyList(),
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        site.trackerDetected(event)
        assertEquals(".tracker.com", BrokenSiteData.fromSite(site, reportFlow = MENU).blockedTrackers)
    }

    @Test
    fun whenSiteHasNoSurrogatesThenSurrogatesIsEmpty() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertTrue(data.surrogates.isEmpty())
    }

    @Test
    fun whenSiteHasSurrogatesThenSurrogatesExist() {
        val surrogate = SurrogateResponse("test.js", true, "surrogate.com/test.js", "", "")
        val anotherSurrogate = SurrogateResponse("test.js", true, "anothersurrogate.com/test.js", "", "")
        val site = buildSite(SITE_URL)
        site.surrogateDetected(surrogate)
        site.surrogateDetected(anotherSurrogate)
        assertEquals("surrogate.com,anothersurrogate.com", BrokenSiteData.fromSite(site, reportFlow = MENU).surrogates)
    }

    @Test
    fun whenSiteHasSameHostSurrogatesThenOnlyUniqueSurrogateIncludedInData() {
        val surrogate = SurrogateResponse("test.js", true, "surrogate.com/test.js", "", "")
        val anotherSurrogate = SurrogateResponse("test.js", true, "surrogate.com/test2.js", "", "")
        val site = buildSite(SITE_URL)
        site.surrogateDetected(surrogate)
        site.surrogateDetected(anotherSurrogate)
        assertEquals("surrogate.com", BrokenSiteData.fromSite(site, reportFlow = MENU).surrogates)
    }

    @Test
    fun whenUserHasTriggeredRefreshThenUserRefreshCountPropertyReflectsCount() {
        val site = buildSite(SITE_URL)
        whenever(mockBrokenSiteContext.userRefreshCount).thenReturn(5)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertEquals(5, data.userRefreshCount)
    }

    @Test
    fun whenUserHasNotTriggeredRefreshThenUserRefreshCountPropertyIsZero() {
        val site = buildSite(SITE_URL)
        whenever(mockBrokenSiteContext.userRefreshCount).thenReturn(0)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertEquals(0, data.userRefreshCount)
    }

    @Test
    fun whenReferrerWasFetchedThenReferrerExists() {
        val site = buildSite(SITE_URL)
        whenever(mockBrokenSiteContext.openerContext).thenReturn(BrokenSiteOpenerContext.SERP)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertEquals(BrokenSiteOpenerContext.SERP, data.openerContext)
    }

    @Test
    fun whenNoReferrerIsRetrievedThenReferrerIsEmpty() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertNull(data.openerContext)
    }

    @Test
    fun whenFirstContentfulPaintIsRetrievedThenJsPerformanceExists() {
        val site = buildSite(SITE_URL)
        whenever(mockBrokenSiteContext.jsPerformance).thenReturn(doubleArrayOf(123.45))
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertTrue(doubleArrayOf(123.45).contentEquals(data.jsPerformance))
    }

    @Test
    fun whenFirstContentfulPaintIsNotRetrievedThenJsPerformanceIsEmpty() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site, reportFlow = MENU)
        assertNull(data.jsPerformance)
    }

    private fun buildSite(
        url: String,
        httpsUpgraded: Boolean = false,
        externalLaunch: Boolean = false,
    ): Site {
        return SiteMonitor(
            url,
            "",
            upgradedHttps = httpsUpgraded,
            externalLaunch = externalLaunch,
            mockAllowListRepository,
            mockContentBlocking,
            mockBypassedSSLCertificatesRepository,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
            mockBrokenSiteContext,
            mockDuckPlayer,
            mockStandardizedLeadingIconToggle,
        )
    }

    companion object {
        private const val SITE_URL = "foo.com"
    }
}
