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

package com.duckduckgo.app.trackerdetection.api

import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.trackerdetection.TrackerDataLoader
import com.duckduckgo.app.trackerdetection.model.Action.BLOCK
import com.duckduckgo.common.test.FileUtilities.loadText
import okhttp3.Headers
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Response
import java.io.IOException

class RealTrackerDataDownloaderTest {

    private val mockTrackerListService: TrackerListService = mock()
    private val mockTrackerDataLoader: TrackerDataLoader = mock()
    private val mockAppDatabase: AppDatabase = mock()

    private val testee = RealTrackerDataDownloader(
        trackerListService = mockTrackerListService,
        binaryDataStore = mock(),
        trackerDataLoader = mockTrackerDataLoader,
        appDatabase = mockAppDatabase,
        metadataDao = mock(),
        moshi = TrackerDetectionJsonModule.moshi,
    )

    @Before
    fun setup() {
        doAnswer { it.getArgument<Runnable>(0).run() }.whenever(mockAppDatabase).runInTransaction(any<Runnable>())
    }

    @Test
    fun whenTdsDownloadedThenBodyIsParsedWithTrackerDetectionAdapters() {
        givenTdsBody(loadText(javaClass.classLoader!!, "json/tds_trackers.json"))

        testee.downloadTds().blockingAwait()

        val tdsJson = argumentCaptor<TdsJson>().apply { verify(mockTrackerDataLoader).persistTds(eq(ETAG), capture()) }.firstValue
        assertEquals(BLOCK, tdsJson.jsonToTrackers().getValue("1dmp.io").defaultAction)
    }

    @Test
    fun whenTdsBodyStartsWithUtf8BomThenBodyIsParsed() {
        givenTdsBody("﻿$MINIMAL_TDS")

        testee.downloadTds().blockingAwait()

        verify(mockTrackerDataLoader).persistTds(eq(ETAG), any())
    }

    @Test
    fun whenTdsBodyHasContentAfterTheJsonThenDownloadFails() {
        givenTdsBody("$MINIMAL_TDS garbage")

        testee.downloadTds().test().assertError(IOException::class.java)

        verifyNoInteractions(mockTrackerDataLoader)
    }

    @Test
    fun whenTdsBodyIsTruncatedThenDownloadFails() {
        givenTdsBody(MINIMAL_TDS.dropLast(1))

        testee.downloadTds().test().assertError(IOException::class.java)

        verifyNoInteractions(mockTrackerDataLoader)
    }

    @Test
    fun whenTdsBodyIsJsonNullThenDownloadFails() {
        givenTdsBody("null")

        testee.downloadTds().test().assertError(IOException::class.java)

        verifyNoInteractions(mockTrackerDataLoader)
    }

    private fun givenTdsBody(json: String) {
        val call: Call<ResponseBody> = mock()
        whenever(call.execute()).thenReturn(Response.success(json.toResponseBody(), Headers.headersOf("eTag", ETAG)))
        whenever(mockTrackerListService.tds()).thenReturn(call)
    }

    companion object {
        private const val ETAG = "etag"
        private const val MINIMAL_TDS = """{"entities":{},"domains":{},"trackers":{},"cnames":{}}"""
    }
}
