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

package com.duckduckgo.app.trackerdetection

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.adclick.api.AdClickManager
import com.duckduckgo.app.privacy.db.UserAllowListDao
import com.duckduckgo.app.trackerdetection.Client.ClientName
import com.duckduckgo.app.trackerdetection.Client.ClientName.EASYLIST
import com.duckduckgo.app.trackerdetection.Client.ClientName.EASYPRIVACY
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.trackerdetection.model.TdsEntity
import com.duckduckgo.app.trackerdetection.model.TrackerStatus
import com.duckduckgo.app.trackerdetection.model.TrackerType
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyMap
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class TrackerDetectorTest {

    private val mockEntityLookup: EntityLookup = mock()
    private val mockUserAllowListDao: UserAllowListDao = mock()
    private val mockContentBlocking: ContentBlocking = mock()
    private val mockTrackerAllowlist: TrackerAllowlist = mock()
    private val mockWebTrackersBlockedDao: WebTrackersBlockedDao = mock()
    private val mockAdClickManager: AdClickManager = mock()

    private val trackerDetector = TrackerDetectorImpl(
        mockEntityLookup,
        mockUserAllowListDao,
        mockContentBlocking,
        mockTrackerAllowlist,
        mockWebTrackersBlockedDao,
        mockAdClickManager,
    )

    @Test
    fun whenThereAreNoClientsThenClientCountIsZero() {
        assertEquals(0, trackerDetector.clientCount)
    }

    @Test
    fun whenClientAddedThenClientCountIsOne() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertEquals(1, trackerDetector.clientCount)
    }

    @Test
    fun whenTwoClientsWithDifferentNamesAddedThenCountIsTwo() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        assertEquals(2, trackerDetector.clientCount)
    }

    @Test
    fun whenTwoClientsWithSameNameAddedThenClientIsReplacedAndCountIsStillOne() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertEquals(1, trackerDetector.clientCount)
        assertNotNull(
            trackerDetector.evaluate(
                "http://thirdparty.com/update.js",
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )

        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertEquals(1, trackerDetector.clientCount)
        assertNotNull(
            trackerDetector.evaluate(
                "http://thirdparty.com/update.js",
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    @Test
    fun whenTwoClientsWithSameNameAddedThenClientIsReplacedAndCountIsStillOne2() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertEquals(1, trackerDetector.clientCount)
        assertNotNull(
            trackerDetector.evaluate(
                Uri.parse("http://thirdparty.com/update.js"),
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )

        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertEquals(1, trackerDetector.clientCount)
        assertNotNull(
            trackerDetector.evaluate(
                Uri.parse("http://thirdparty.com/update.js"),
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    @Test
    fun whenThereAreNoClientsAndIsThirdPartyThenEvaluateReturnsNonTrackingEvent() {
        trackerDetector.addClient(nonMatchingClientNoTracker(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )

        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenThereAreNoClientsAndIsThirdPartyThenEvaluateReturnsNonTrackingEvent2() {
        trackerDetector.addClient(nonMatchingClientNoTracker(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )

        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenThereAreNoClientsAndIsThirdPartyFromSameEntityThenEvaluateReturnsSameEntityNonTrackingEvent() {
        val entity = TdsEntity("example", "example", 0.0)
        whenever(mockEntityLookup.entityForUrl(anyString())).thenReturn(entity)
        whenever(mockEntityLookup.entityForUrl(any<Uri>())).thenReturn(entity)
        trackerDetector.addClient(nonMatchingClientNoTracker(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = entity,
            surrogateId = null,
            status = TrackerStatus.SAME_ENTITY_ALLOWED,
            type = TrackerType.OTHER,
        )

        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenThereAreNoClientsAndIsThirdPartyFromSameEntityThenEvaluateReturnsSameEntityNonTrackingEvent2() {
        val entity = TdsEntity("example", "example", 0.0)
        whenever(mockEntityLookup.entityForUrl(anyString())).thenReturn(entity)
        whenever(mockEntityLookup.entityForUrl(any<Uri>())).thenReturn(entity)
        trackerDetector.addClient(nonMatchingClientNoTracker(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = entity,
            surrogateId = null,
            status = TrackerStatus.SAME_ENTITY_ALLOWED,
            type = TrackerType.OTHER,
        )

        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenThereAreClientsAndIsThirdPartyButIgnoredThenEvaluateReturnsNonTrackingEvent() {
        val entity = TdsEntity("example", "example", 0.0)
        whenever(mockEntityLookup.entityForUrl(anyString())).thenReturn(entity)
        whenever(mockEntityLookup.entityForUrl(any<Uri>())).thenReturn(entity)
        trackerDetector.addClient(matchingClientTrackerIgnored(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = entity,
            surrogateId = null,
            status = TrackerStatus.SAME_ENTITY_ALLOWED,
            type = TrackerType.OTHER,
        )

        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenThereAreClientsAndIsThirdPartyButIgnoredThenEvaluateReturnsNonTrackingEvent2() {
        val entity = TdsEntity("example", "example", 0.0)
        whenever(mockEntityLookup.entityForUrl(anyString())).thenReturn(entity)
        whenever(mockEntityLookup.entityForUrl(any<Uri>())).thenReturn(entity)
        trackerDetector.addClient(matchingClientTrackerIgnored(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = entity,
            surrogateId = null,
            status = TrackerStatus.SAME_ENTITY_ALLOWED,
            type = TrackerType.OTHER,
        )

        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsNotUserAllowListedAndAllClientsMatchThenEvaluateReturnsBlockedTrackingEvent() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsNotUserAllowListedAndAllClientsMatchThenEvaluateReturnsBlockedTrackingEvent2() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsUserAllowListedAndAllClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.USER_ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsUserAllowListedAndAllClientsMatchThenEvaluateReturnsUnblockedTrackingEvent2() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.USER_ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsNotUserAllowListedAndSomeClientsMatchThenEvaluateReturnsBlockedTrackingEvent() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsNotUserAllowListedAndSomeClientsMatchThenEvaluateReturnsBlockedTrackingEvent2() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsUserAllowListedAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.USER_ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsUserAllowListedAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent2() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_B))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.USER_ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsInContentBlockingExceptionsListAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(mockContentBlocking.isAnException(anyString())).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsInContentBlockingExceptionsListAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent2() {
        whenever(mockContentBlocking.isAnException(anyString())).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsNotUserAllowListedAndSomeClientsMatchWithSurrogateThenEvaluateReturnsBlockedTrackingEventWithSurrogate() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClientWithSurrogate(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = "testId",
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenSiteIsNotUserAllowListedAndSomeClientsMatchWithSurrogateThenEvaluateReturnsBlockedTrackingEventWithSurrogate2() {
        whenever(mockUserAllowListDao.contains("example.com")).thenReturn(false)
        trackerDetector.addClient(alwaysMatchingClientWithSurrogate(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = "testId",
            status = TrackerStatus.BLOCKED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenRequestIsInAllowlistAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(mockTrackerAllowlist.isAnException(anyString(), anyString())).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.SITE_BREAKAGE_ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenRequestIsInAllowlistAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent2() {
        whenever(mockTrackerAllowlist.isAnException(anyString(), anyString())).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.SITE_BREAKAGE_ALLOWED,
            type = TrackerType.OTHER,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenRequestIsInAdClickAllowListAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent() {
        whenever(mockAdClickManager.isExemption(anyString(), anyString())).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.AD_ALLOWED,
            type = TrackerType.AD,
        )
        val actual = trackerDetector.evaluate(
            "http://thirdparty.com/update.js",
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenRequestIsInAdClickAllowListAndSomeClientsMatchThenEvaluateReturnsUnblockedTrackingEvent2() {
        whenever(mockAdClickManager.isExemption(anyString(), anyString())).thenReturn(true)
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        val expected = TrackingEvent(
            documentUrl = "http://example.com/index.com",
            trackerUrl = "http://thirdparty.com/update.js",
            categories = null,
            entity = null,
            surrogateId = null,
            status = TrackerStatus.AD_ALLOWED,
            type = TrackerType.AD,
        )
        val actual = trackerDetector.evaluate(
            Uri.parse("http://thirdparty.com/update.js"),
            "http://example.com/index.com".toUri(),
            requestHeaders = mapOf(),
        )
        assertEquals(expected, actual)
    }

    @Test
    fun whenUrlHasSameDomainAsDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertNull(trackerDetector.evaluate("http://example.com/update.js", "http://example.com/index.com".toUri(), requestHeaders = mapOf()))
    }

    @Test
    fun whenUrlHasSameDomainAsDocumentThenEvaluateReturnsNull2() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertNull(
            trackerDetector.evaluate(
                Uri.parse("http://example.com/update.js"),
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    @Test
    fun whenUrlIsSubdomainOfDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertNull(
            trackerDetector.evaluate(
                "http://mobile.example.com/update.js",
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    @Test
    fun whenUrlIsSubdomainOfDocumentThenEvaluateReturnsNull2() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertNull(
            trackerDetector.evaluate(
                Uri.parse("http://mobile.example.com/update.js"),
                "http://example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    @Test
    fun whenUrlIsParentOfDocumentThenEvaluateReturnsNull() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertNull(
            trackerDetector.evaluate(
                "http://example.com/update.js",
                "http://mobile.example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    @Test
    fun whenUrlIsParentOfDocumentThenEvaluateReturnsNull2() {
        trackerDetector.addClient(alwaysMatchingClient(CLIENT_A))
        assertNull(
            trackerDetector.evaluate(
                Uri.parse("http://example.com/update.js"),
                "http://mobile.example.com/index.com".toUri(),
                requestHeaders = mapOf(),
            ),
        )
    }

    private fun alwaysMatchingClient(name: ClientName): Client {
        val client: Client = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(anyString(), any<Uri>(), anyMap())).thenReturn(Client.Result(matches = true, isATracker = true))
        whenever(client.matches(any<Uri>(), any<Uri>(), anyMap())).thenReturn(Client.Result(matches = true, isATracker = true))
        return client
    }

    private fun alwaysMatchingClientWithSurrogate(name: ClientName): Client {
        val client: Client = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(anyString(), any<Uri>(), anyMap()))
            .thenReturn(Client.Result(matches = true, surrogate = "testId", isATracker = true))
        whenever(client.matches(any<Uri>(), any<Uri>(), anyMap()))
            .thenReturn(Client.Result(matches = true, surrogate = "testId", isATracker = true))
        return client
    }

    private fun nonMatchingClientNoTracker(name: ClientName): Client {
        val client: Client = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(anyString(), any<Uri>(), anyMap())).thenReturn(Client.Result(matches = false, isATracker = false))
        return client
    }

    private fun matchingClientTrackerIgnored(name: ClientName): Client {
        val client: Client = mock()
        whenever(client.name).thenReturn(name)
        whenever(client.matches(anyString(), any<Uri>(), anyMap())).thenReturn(Client.Result(matches = false, isATracker = true))
        return client
    }
    companion object {
        // It doesn't matter what the value of these is they just need to be different
        private val CLIENT_A = EASYLIST
        private val CLIENT_B = EASYPRIVACY
        private val ENTITY = TestEntity("name", "displayName", 10.0)
    }
}
