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

package com.duckduckgo.app.trackerdetection.api

import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Test

class TrackerDataDownloaderKtTest {

    @Test
    fun whenExtractETagAndContainsPrefixAndQuotesThenReturnETag() {
        val headers = Headers.headersOf("eTag", "W/\"123456789\"")
        assertEquals(ETAG, headers.extractETag())
    }

    @Test
    fun whenExtractETagAndContainsQuotesThenReturnETag() {
        val headers = Headers.headersOf("eTag", "\"123456789\"")
        assertEquals(ETAG, headers.extractETag())
    }

    @Test
    fun whenExtractETagAndDoesNotContainsQuotesAndPrefixThenReturnETag() {
        val headers = Headers.headersOf("eTag", "123456789")
        assertEquals(ETAG, headers.extractETag())
    }

    companion object {
        const val ETAG = "123456789"
    }
}
