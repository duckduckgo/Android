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

package com.duckduckgo.privacy.dashboard.impl.ui

import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslCertificate.DName
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.AD_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.BLOCKED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.SAME_ENTITY_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.SITE_BREAKAGE_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerStatus.USER_ALLOWED
import com.duckduckgo.app.trackerdetection.model.TrackerType.AD
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.privacy.dashboard.impl.ui.AppSiteRequestDataViewStateMapperTest.EntityMO.MAJOR_ENTITY_A
import com.duckduckgo.privacy.dashboard.impl.ui.AppSiteRequestDataViewStateMapperTest.EntityMO.MINOR_ENTITY_A
import com.duckduckgo.privacy.dashboard.impl.ui.AppSiteViewStateMapperTest.TestCertificateInfo
import com.duckduckgo.privacy.dashboard.impl.ui.AppSiteViewStateMapperTest.TestEntity
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.AllowedReasons
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.Reason
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Allowed
import com.duckduckgo.privacy.dashboard.impl.ui.PrivacyDashboardHybridViewModel.RequestState.Blocked
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSiteRequestDataViewStateMapperTest {

    private val testee = AppSiteRequestDataViewStateMapper()

    @Test
    fun whenSiteHasTrackersThenRequestDataViewStateContainsSuchTrackers() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
                TrackingEvent("test2.com", "test2.com", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
                TrackingEvent("test3.com", "test3.com", null, null, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertTrue(viewState.requests.count() == 3)
        assertNotNull(viewState.requests.find { it.url == "http://test.com" })
        assertNotNull(viewState.requests.find { it.url == "http://test2.com" })
        assertNotNull(viewState.requests.find { it.url == "http://test3.com" })
    }

    @Test
    fun whenSiteHasTrackersBlockedAndNotBlockedThenRequestDataViewStateContainsSuchTrackers() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test1.com", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
                TrackingEvent("test.com", "test2.com", null, MAJOR_ENTITY_A, null, USER_ALLOWED, AD),
                TrackingEvent("test.com", "test3.com", null, MAJOR_ENTITY_A, null, AD_ALLOWED, AD),
                TrackingEvent("test.com", "test4.com", null, MAJOR_ENTITY_A, null, SITE_BREAKAGE_ALLOWED, AD),
                TrackingEvent("test.com", "test5.com", null, MAJOR_ENTITY_A, null, SAME_ENTITY_ALLOWED, AD),
                TrackingEvent("test.com", "test6.com", null, MAJOR_ENTITY_A, null, ALLOWED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertTrue(viewState.requests.count() == 6)
        assertTrue(viewState.requests.first { it.url == "http://test1.com" }.state is Blocked)
        assertTrue(viewState.requests.first { it.url == "http://test2.com" }.state == Allowed(Reason(AllowedReasons.PROTECTIONS_DISABLED.value)))
        assertTrue(viewState.requests.first { it.url == "http://test3.com" }.state == Allowed(Reason(AllowedReasons.AD_CLICK_ATTRIBUTION.value)))
        assertTrue(viewState.requests.first { it.url == "http://test4.com" }.state == Allowed(Reason(AllowedReasons.RULE_EXCEPTION.value)))
        assertTrue(viewState.requests.first { it.url == "http://test5.com" }.state == Allowed(Reason(AllowedReasons.OWNED_BY_FIRST_PARTY.value)))
        assertTrue(viewState.requests.first { it.url == "http://test6.com" }.state == Allowed(Reason(AllowedReasons.OTHER_THIRD_PARTY_REQUEST.value)))
    }

    @Test
    fun whenSiteDoesNotHaveCategoryThenRequestDataViewStateRequestCategoryIsNull() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertNull(viewState.requests.first().category)
    }

    @Test
    fun whenSiteDoesHaveAllowedCategoryThenRequestDataViewStateRequestHasCategory() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", listOf("Advertising", "unknown"), MAJOR_ENTITY_A, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertEquals("Advertising", viewState.requests.first().category)
    }

    @Test
    fun whenSiteHasUnknownCategoriesThenRequestDataViewStateRequestHasCategoryIsNull() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", listOf("unknown"), MAJOR_ENTITY_A, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertNull(viewState.requests.first().category)
    }

    @Test
    fun whenSiteContainsATrackerThenRequestDataContainsAllRequiredTrackerInfo() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.co.uk", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertTrue(viewState.requests.count() == 1)
        assertEquals("http://test.co.uk", viewState.requests.first().url)
        assertEquals("test.co.uk", viewState.requests.first().eTLDplus1)
        assertEquals("test.com", viewState.requests.first().pageUrl)
        assertEquals(MAJOR_ENTITY_A.displayName, viewState.requests.first().entityName)
        assertEquals(MAJOR_ENTITY_A.name, viewState.requests.first().ownerName)
        assertEquals(MAJOR_ENTITY_A.prevalence, viewState.requests.first().prevalence!!, 0.0)
        assertTrue(viewState.requests.first().state is Blocked)
    }

    @Test
    fun whenSiteHasTrackersFromSameDomainAndSameStateThenRequestDataViewStateContainsFirstEvent() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com/a.js", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
                TrackingEvent("test.com", "test.com/b.js", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertTrue(viewState.requests.count() == 1)
        assertNotNull(viewState.requests.find { it.url == "http://test.com/a.js" })
    }

    @Test
    fun whenSiteHasTrackersFromSameDomainAndSameStateButDifferentEntityThenRequestDataViewStateContainsBothEvent() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com/a.js", null, MINOR_ENTITY_A, null, BLOCKED, AD),
                TrackingEvent("test.com", "test.com/b.js", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertTrue(viewState.requests.count() == 2)
        assertNotNull(viewState.requests.find { it.url == "http://test.com/a.js" })
        assertNotNull(viewState.requests.find { it.url == "http://test.com/b.js" })
    }

    @Test
    fun whenSiteHasTrackersFromSameDomainButDifferentStateThenRequestDataViewStateContainsBothEvent() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com/a.js", null, MAJOR_ENTITY_A, null, BLOCKED, AD),
                TrackingEvent("test.com", "test.com/b.js", null, MAJOR_ENTITY_A, null, SAME_ENTITY_ALLOWED, AD),
            ),
        )

        val viewState = testee.mapFromSite(site)

        assertTrue(viewState.requests.count() == 2)
        assertTrue(viewState.requests.find { it.url == "http://test.com/a.js" }!!.state is Blocked)
        assertTrue(viewState.requests.find { it.url == "http://test.com/b.js" }!!.state is Allowed)
    }

    private fun site(
        url: String = "http://www.test.com",
        uri: Uri? = Uri.parse(url),
        events: List<TrackingEvent> = emptyList(),
        entity: Entity? = null,
        certificate: TestCertificateInfo? = null,
    ): Site {
        val site: Site = mock()
        whenever(site.url).thenReturn(url)
        whenever(site.uri).thenReturn(uri)
        whenever(site.entity).thenReturn(entity)
        whenever(site.trackingEvents).thenReturn(events)

        if (certificate != null) {
            val dName = mock<DName>().apply {
                whenever(this.cName).thenReturn(certificate.cName)
            }
            val sslCertificate = mock<SslCertificate>().apply {
                whenever(issuedTo).thenReturn(dName)
            }
            whenever(site.certificate).thenReturn(sslCertificate)
        }

        return site
    }

    object EntityMO {
        val MINOR_ENTITY_A = TestEntity("Minor A", "Minor A", 0.0)
        val MAJOR_ENTITY_A = TestEntity("Major A", "Major A", Entity.MAJOR_NETWORK_PREVALENCE)
    }
}
