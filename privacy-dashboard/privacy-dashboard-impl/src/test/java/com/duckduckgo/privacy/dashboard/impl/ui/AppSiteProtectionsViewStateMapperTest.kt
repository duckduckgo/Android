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
import android.os.Build.VERSION_CODES
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.global.model.Site
import com.duckduckgo.app.global.model.domain
import com.duckduckgo.app.trackerdetection.model.Entity
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.privacy.dashboard.impl.ui.AppSiteProtectionsViewStateMapperTest.EntityMO.MAJOR_ENTITY_A
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSiteProtectionsViewStateMapperTest {

    private val androidQAppBuildConfig = mock<AppBuildConfig>().apply {
        whenever(this.sdkInt).thenReturn(VERSION_CODES.Q)
    }

    val testee = AppSiteProtectionsViewStateMapper(PublicKeyInfoMapper(androidQAppBuildConfig))

    @Test
    fun whenSiteHasEntityThenViewStateHasParentEntity() {
        val site = site(entity = MAJOR_ENTITY_A)

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertNotNull(siteProtectionsViewState.parentEntity)
        assertEquals(site.entity!!.displayName, siteProtectionsViewState.parentEntity!!.displayName)
        assertTrue(site.entity!!.prevalence == siteProtectionsViewState.parentEntity!!.prevalence)
    }

    @Test
    fun whenSiteDoesNotHaveEntityThenViewStateParentEntityNull() {
        val site = site()

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertNull(siteProtectionsViewState.parentEntity)
    }

    @Test
    fun whenMappingSiteThenViewStateHasSiteInfo() {
        val site = site()

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertEquals(site.url, siteProtectionsViewState.url)
        assertEquals(site.upgradedHttps, siteProtectionsViewState.upgradedHttps)
        assertEquals(site.url, siteProtectionsViewState.site.url)
        assertEquals(site.domain, siteProtectionsViewState.site.domain)
        assertEquals(site.userAllowList, siteProtectionsViewState.site.allowlisted)
    }

    @Test
    fun whenSiteHasCertificateThenViewStateContainsCertificates() {
        val site = site(certificate = TestCertificateInfo("cname"))

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertEquals("cname", siteProtectionsViewState.secCertificateViewModels.first()!!.commonName)
        assertEquals("cname", siteProtectionsViewState.secCertificateViewModels.first()!!.summary)
    }

    @Test
    fun whenSiteDoesNotHaveCertificateThenViewStateCertificatesEmpty() {
        val site = site()

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertTrue(siteProtectionsViewState.secCertificateViewModels.isEmpty())
    }

    @Test
    fun whenSiteContainsTrackersEventsThenViewStateHasTrackerData() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", null, MAJOR_ENTITY_A, true, null),
                TrackingEvent("test2.com", "test2.com", null, MAJOR_ENTITY_A, true, null)
            )
        )

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertTrue(siteProtectionsViewState.trackers.containsKey(MAJOR_ENTITY_A.displayName))
        assertTrue(siteProtectionsViewState.trackers[MAJOR_ENTITY_A.displayName]!!.count == 2)
        val eventsEntityA = siteProtectionsViewState.trackers[MAJOR_ENTITY_A.displayName]
        assertTrue(eventsEntityA!!.urls["test.com"]!!.isBlocked)
        assertTrue(eventsEntityA.urls["test2.com"]!!.isBlocked)
    }

    @Test
    fun whenSiteContainsTrackersEventsThenSiteViewStateHasUrls() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", null, MAJOR_ENTITY_A, true, null),
                TrackingEvent("test2.com", "test2.com", null, MAJOR_ENTITY_A, false, null)
            )
        )

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertTrue(siteProtectionsViewState.site.trackersUrls.count() == 2)
        assertTrue(siteProtectionsViewState.site.trackersUrls.containsAll(listOf("test.com", "test2.com")))
    }

    @Test
    fun whenAllTrackersEventsNotBlockedThenViewStateBlockedTrackersEmpty() {
        val site = site(
            events = listOf(
                TrackingEvent("test.com", "test.com", null, MAJOR_ENTITY_A, false, null),
                TrackingEvent("test2.com", "test2.com", null, MAJOR_ENTITY_A, false, null)
            )
        )

        val siteProtectionsViewState = testee.mapFromSite(site)

        assertTrue(siteProtectionsViewState.trackerBlocked.isEmpty())
    }

    private fun site(
        url: String = "http://www.test.com",
        uri: Uri? = Uri.parse(url),
        events: List<TrackingEvent> = emptyList(),
        entity: Entity? = null,
        certificate: TestCertificateInfo? = null
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

    data class TestCertificateInfo(
        val cName: String
    )

    data class TestEntity(
        override val name: String,
        override val displayName: String,
        override val prevalence: Double
    ) : Entity
}
