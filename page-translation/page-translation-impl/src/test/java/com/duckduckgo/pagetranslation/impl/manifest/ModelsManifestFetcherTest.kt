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

package com.duckduckgo.pagetranslation.impl.manifest

import androidx.work.ListenableWorker.Result
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifestFetcher.Companion.MANIFEST_URL
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.IOException

class ModelsManifestFetcherTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val store: ModelsManifestStore = mock()
    private val call: Call = mock()
    private val client: OkHttpClient = mock { on { newCall(any()) } doReturn call }
    private val fetcher = ModelsManifestFetcher(store, lazyOf(client), coroutineRule.testDispatcherProvider)

    @Test
    fun requestManifestWithStoredETag() = runTest {
        whenever(store.getEtagForRequest()).thenReturn(OLD_TAG)
        respondWith(NOT_MODIFIED)

        fetcher.fetch()

        val request = takeRequest()
        assertEquals(MANIFEST_URL, request.url.toString())
        assertEquals(OLD_TAG, request.header(IF_NONE_MATCH_HEADER))
    }

    @Test
    fun requestWithoutIfNoneMatchWhenNoETagIsStored() = runTest {
        respondWith(NOT_MODIFIED)

        fetcher.fetch()

        assertNull(takeRequest().header(IF_NONE_MATCH_HEADER))
    }

    @Test
    fun passBodyAndETagToStoreWhenManifestChanged() = runTest {
        respondWith(OK, body = MANIFEST_BODY, etag = NEW_TAG)

        assertEquals(Result.success(), fetcher.fetch())

        verify(store).replaceIfSupported(MANIFEST_BODY, NEW_TAG)
    }

    @Test
    fun keepStoredManifestWhenNotModified() = runTest {
        respondWith(NOT_MODIFIED)

        assertEquals(Result.success(), fetcher.fetch())

        verify(store, never()).replaceIfSupported(any(), any())
    }

    @Test
    fun retryAndKeepStoredManifestWhenServerFails() = runTest {
        respondWith(SERVICE_UNAVAILABLE, body = SERVER_ERROR_BODY)

        assertEquals(Result.retry(), fetcher.fetch())

        verify(store, never()).replaceIfSupported(any(), any())
    }

    @Test
    fun retryAndKeepStoredManifestWhenRequestFails() = runTest {
        whenever(call.execute()).thenThrow(IOException())

        assertEquals(Result.retry(), fetcher.fetch())

        verify(store, never()).replaceIfSupported(any(), any())
    }

    private fun respondWith(code: Int, body: String = "", etag: String? = null) {
        whenever(call.execute()).thenAnswer {
            Response.Builder()
                .request(takeRequest())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("")
                .body(body.toResponseBody())
                .apply { etag?.let { header(ETAG_HEADER, it) } }
                .build()
        }
    }

    private fun takeRequest(): Request = argumentCaptor<Request>().apply { verify(client).newCall(capture()) }.lastValue

    private companion object {
        const val OK = 200
        const val NOT_MODIFIED = 304
        const val SERVICE_UNAVAILABLE = 503

        const val ETAG_HEADER = "ETag"
        const val IF_NONE_MATCH_HEADER = "If-None-Match"

        const val OLD_TAG = "\"first\""
        const val NEW_TAG = "W/\"second\""
        const val MANIFEST_BODY = """{"schemaVersion":1,"baseUrl":"/","models":{}}"""
        const val SERVER_ERROR_BODY = "unavailable"
    }
}
