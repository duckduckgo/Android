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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.SiteMonitor
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.surrogates.SurrogateResponse
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.browser.api.brokensite.BrokenSiteData
import com.duckduckgo.privacy.config.api.ContentBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class BrokenSiteDataTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockAllowlistDao: UserAllowListDao = mock()

    private val mockContentBlocking: ContentBlocking = mock()

    @Test
    fun whenSiteIsNullThenDataIsEmptyAndUpgradedIsFalse() {
        val data = BrokenSiteData.fromSite(null)
        assertTrue(data.url.isEmpty())
        assertTrue(data.blockedTrackers.isEmpty())
        assertTrue(data.surrogates.isEmpty())
        assertFalse(data.upgradedToHttps)
    }

    @Test
    fun whenSiteExistsThenDataContainsUrl() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site)
        assertEquals(SITE_URL, data.url)
    }

    @Test
    fun whenSiteUpgradedThenHttpsUpgradedIsTrue() {
        val site = buildSite(SITE_URL, httpsUpgraded = true)
        val data = BrokenSiteData.fromSite(site)
        assertTrue(data.upgradedToHttps)
    }

    @Test
    fun whenSiteNotUpgradedThenHttpsUpgradedIsFalse() {
        val site = buildSite(SITE_URL, httpsUpgraded = false)
        val data = BrokenSiteData.fromSite(site)
        assertFalse(data.upgradedToHttps)
    }

    @Test
    fun whenUrlParametersRemovedThenUrlParametersRemovedIsTrue() {
        val site = buildSite(SITE_URL)
        site.urlParametersRemoved = true
        val data = BrokenSiteData.fromSite(site)
        assertTrue(data.urlParametersRemoved)
    }

    @Test
    fun whenUrlParametersNotRemovedThenUrlParametersRemovedIsFalse() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site)
        assertFalse(data.urlParametersRemoved)
    }

    @Test
    fun whenSiteHasNoTrackersThenBlockedTrackersIsEmpty() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site)
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
        assertEquals("tracker.com", BrokenSiteData.fromSite(site).blockedTrackers)
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
        assertEquals("tracker.com", BrokenSiteData.fromSite(site).blockedTrackers)
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
        assertEquals(".tracker.com", BrokenSiteData.fromSite(site).blockedTrackers)
    }

    @Test
    fun whenSiteHasNoSurrogatesThenSurrogatesIsEmpty() {
        val site = buildSite(SITE_URL)
        val data = BrokenSiteData.fromSite(site)
        assertTrue(data.surrogates.isEmpty())
    }

    @Test
    fun whenSiteHasSurrogatesThenSurrogatesExist() {
        val surrogate = SurrogateResponse("test.js", true, "surrogate.com/test.js", "", "")
        val anotherSurrogate = SurrogateResponse("test.js", true, "anothersurrogate.com/test.js", "", "")
        val site = buildSite(SITE_URL)
        site.surrogateDetected(surrogate)
        site.surrogateDetected(anotherSurrogate)
        assertEquals("surrogate.com,anothersurrogate.com", BrokenSiteData.fromSite(site).surrogates)
    }

    @Test
    fun whenSiteHasSameHostSurrogatesThenOnlyUniqueSurrogateIncludedInData() {
        val surrogate = SurrogateResponse("test.js", true, "surrogate.com/test.js", "", "")
        val anotherSurrogate = SurrogateResponse("test.js", true, "surrogate.com/test2.js", "", "")
        val site = buildSite(SITE_URL)
        site.surrogateDetected(surrogate)
        site.surrogateDetected(anotherSurrogate)
        assertEquals("surrogate.com", BrokenSiteData.fromSite(site).surrogates)
    }

    private fun buildSite(
        url: String,
        httpsUpgraded: Boolean = false,
    ): Site {
        return SiteMonitor(
            url,
            "",
            upgradedHttps = httpsUpgraded,
            mockAllowlistDao,
            mockContentBlocking,
            coroutineRule.testScope,
            coroutineRule.testDispatcherProvider,
        )
    }

    companion object {
        private const val SITE_URL = "foo.com"
    }
}
