/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.request.interception.impl

import android.net.Uri
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.privacy.db.UserAllowListRepository
import com.duckduckgo.privacy.config.api.ContentBlocking
import com.duckduckgo.privacy.config.api.TrackerAllowlist
import com.duckduckgo.request.interception.api.RequestBlocklist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(AndroidJUnit4::class)
class RequestExemptionsTest {

    private val mockRequestBlocklist: RequestBlocklist = mock()
    private val mockContentBlocking: ContentBlocking = mock()
    private val mockTrackerAllowlist: TrackerAllowlist = mock()
    private val mockUserAllowListRepository: UserAllowListRepository = mock()

    private val testee = RequestExemptions(
        mockRequestBlocklist,
        mockContentBlocking,
        mockTrackerAllowlist,
        mockUserAllowListRepository,
    )

    private val documentUri: Uri = "https://example.com/page".toUri()
    private val url: Uri = "https://tracker.example.net/script.js".toUri()

    @Test
    fun whenDocumentIsTrustedSiteThenIsTrustedIsTrue() {
        assertTrue(testee.isTrusted("https://duckduckgo.com/?q=test".toUri()))
    }

    @Test
    fun whenDocumentIsNotTrustedSiteThenIsTrustedIsFalse() {
        assertFalse(testee.isTrusted(documentUri))
    }

    @Test
    fun whenRequestIsForMainFrameThenVerdictIsNotListed() {
        whenever(mockRequestBlocklist.containedInBlocklist(documentUri, url)).thenReturn(true)

        assertEquals(BlocklistVerdict.NOT_LISTED, testee.blocklistVerdict(isForMainFrame = true, documentUri = documentUri, url = url))
    }

    @Test
    fun whenRequestIsNotInBlocklistThenVerdictIsNotListed() {
        whenever(mockRequestBlocklist.containedInBlocklist(documentUri, url)).thenReturn(false)

        assertEquals(BlocklistVerdict.NOT_LISTED, testee.blocklistVerdict(isForMainFrame = false, documentUri = documentUri, url = url))
    }

    @Test
    fun whenRequestIsInBlocklistAndNoExemptionThenVerdictIsBlocked() {
        whenever(mockRequestBlocklist.containedInBlocklist(documentUri, url)).thenReturn(true)

        assertEquals(BlocklistVerdict.BLOCKED, testee.blocklistVerdict(isForMainFrame = false, documentUri = documentUri, url = url))
    }

    @Test
    fun whenRequestIsInBlocklistAndContentBlockingExceptionThenVerdictIsExempted() {
        whenever(mockRequestBlocklist.containedInBlocklist(documentUri, url)).thenReturn(true)
        whenever(mockContentBlocking.isAnException(documentUri.toString())).thenReturn(true)

        assertEquals(BlocklistVerdict.EXEMPTED, testee.blocklistVerdict(isForMainFrame = false, documentUri = documentUri, url = url))
    }

    @Test
    fun whenRequestIsInBlocklistAndInTrackerAllowlistThenVerdictIsExempted() {
        whenever(mockRequestBlocklist.containedInBlocklist(documentUri, url)).thenReturn(true)
        whenever(mockTrackerAllowlist.isAnException(documentUri.toString(), url.toString())).thenReturn(true)

        assertEquals(BlocklistVerdict.EXEMPTED, testee.blocklistVerdict(isForMainFrame = false, documentUri = documentUri, url = url))
    }

    @Test
    fun whenRequestIsInBlocklistAndInUserAllowlistThenVerdictIsExempted() {
        whenever(mockRequestBlocklist.containedInBlocklist(documentUri, url)).thenReturn(true)
        whenever(mockUserAllowListRepository.isUriInUserAllowList(documentUri)).thenReturn(true)

        assertEquals(BlocklistVerdict.EXEMPTED, testee.blocklistVerdict(isForMainFrame = false, documentUri = documentUri, url = url))
    }
}
