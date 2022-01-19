/*
 * Copyright (c) 2021 DuckDuckGo
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

import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.privacy.db.UserWhitelistDao
import com.duckduckgo.app.trackerdetection.db.WebTrackersBlockedDao
import com.duckduckgo.app.trackerdetection.model.TrackingEvent
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.jakewharton.threetenabp.AndroidThreeTen
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class TrackerDetectorClientTypeTest {

    private var mockEntityLookup: EntityLookup = mock()
    private var mockBlockingClient: Client = mock()
    private var mockUserWhitelistDao: UserWhitelistDao = mock()
    private var mockWebTrackersBlockedDao: WebTrackersBlockedDao = mock()
    private var mockContentBlocking: ContentBlocking = mock()
    private var mockTrackerAllowlist: TrackerAllowlist = mock()

    private var testee =
        TrackerDetectorImpl(mockEntityLookup, mockUserWhitelistDao, mockContentBlocking, mockTrackerAllowlist, mockWebTrackersBlockedDao)

    @Before
    fun before() {
        AndroidThreeTen.init(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext)

        whenever(mockUserWhitelistDao.contains(any())).thenReturn(false)

        whenever(mockBlockingClient.matches(eq(Url.BLOCKED), any())).thenReturn(Client.Result(true))
        whenever(mockBlockingClient.matches(eq(Url.UNLISTED), any())).thenReturn(Client.Result(false))
        whenever(mockBlockingClient.name).thenReturn(Client.ClientName.TDS)
        testee.addClient(mockBlockingClient)
    }

    @Test
    fun whenUrlMatchesOnlyInBlockingClientThenEvaluateReturnsTrackingEvent() {
        val url = Url.BLOCKED
        val expected = TrackingEvent(documentUrl, url, null, null, true, null)
        assertEquals(expected, testee.evaluate(url, documentUrl))
    }

    @Test
    fun whenUrlDoesNotMatchInAnyClientsThenEvaluateReturnsNull() {
        val url = Url.UNLISTED
        assertNull(testee.evaluate(url, documentUrl))
    }

    companion object {
        private const val documentUrl = "http://example.com"
    }

    object Url {
        const val BLOCKED = "blocked.com"
        const val UNLISTED = "unlisted.com"
    }
}
