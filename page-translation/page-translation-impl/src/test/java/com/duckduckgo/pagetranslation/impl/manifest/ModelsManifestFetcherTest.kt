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

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ModelsManifestFetcherTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val server = MockWebServer()
    private val directory get() = temporaryFolder.root.resolve(MANIFEST_DIRECTORY_NAME)
    private val preferences = PreferenceDataStoreFactory.create(scope = coroutineRule.testScope) {
        temporaryFolder.root.resolve(MANIFEST_PREFERENCES_FILE_NAME)
    }
    private val store = ModelsManifestStore({ directory }, preferences, coroutineRule.testDispatcherProvider)
    private val fetcher = ModelsManifestFetcher(store, coroutineRule.testDispatcherProvider)

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun storeOriginalBodyAndETagOnFirstFetch() = runTest {
        val request = seedManifest()

        assertNull(request.getHeader(IF_NONE_MATCH_HEADER))
        assertEquals(MANIFEST, getStoredFile().readText())

        val model = store.readManifest()!!.models.getValue(MODEL_PAIR)
        assertEquals(25020240L, model.totalBytes)
        assertEquals(31561787L, model.files.getValue(MODEL_FILE_KEY).decompressedSize)

        verifyStoredETag()
    }

    @Test
    fun keepBodyAndETagWhenNotModified() = runTest {
        seedManifest()
        val file = getStoredFile()
        val lastModified = file.lastModified()

        val request = fetchSuccessfully(MockResponse().setResponseCode(304).setHeader(ETAG_HEADER, NEW_TAG))

        assertEquals(OLD_TAG, request.getHeader(IF_NONE_MATCH_HEADER))
        assertEquals(MANIFEST, file.readText())
        assertEquals(lastModified, file.lastModified())
        verifyStoredETag()
    }

    @Test
    fun keepBodyAndETagWhenResponseIsMalformed() = runTest {
        seedManifest()

        val invalidManifests = listOf(
            INCOMPLETE_JSON_OBJECT,
            NULL_JSON,
            MANIFEST + EMPTY_JSON_OBJECT,
            EMPTY_JSON_OBJECT,
            MISSING_MODELS_MANIFEST,
        )

        for (invalid in invalidManifests) {
            fetchSuccessfully(MockResponse().setBody(invalid).setHeader(ETAG_HEADER, NEW_TAG))

            assertEquals(MANIFEST, getStoredFile().readText())
            verifyStoredETag()
        }
    }

    @Test
    fun keepBodyAndETagWhenSchemaIsUnsupported() = runTest {
        seedManifest()

        fetchSuccessfully(MockResponse().setBody(FUTURE_SCHEMA_MANIFEST).setHeader(ETAG_HEADER, NEW_TAG))

        assertEquals(MANIFEST, getStoredFile().readText())
        verifyStoredETag()
    }

    @Test
    fun replaceBodyAndDropMissingETagWhenResponseIsAccepted() = runTest {
        seedManifest()

        fetchSuccessfully(MockResponse().setBody(UPDATED_MANIFEST))

        assertEquals(UPDATED_BASE_URL, store.readManifest()!!.baseUrl)
        verifyStoredETag(null)
    }

    @Test
    fun clearETagBeforeFetchWhenFileIsMissing() = runTest {
        seedManifest()
        getStoredFile().delete()

        val request = fetchSuccessfully(MockResponse().setBody(EMPTY_JSON_OBJECT))

        assertNull(request.getHeader(IF_NONE_MATCH_HEADER))
        assertNull(store.readManifest())
        verifyStoredETag(null)
    }

    @Test
    fun deleteCorruptFileAndClearETagBeforeFetch() = runTest {
        seedManifest()
        val file = getStoredFile()
        file.writeText(CORRUPT_CONTENT)
        assertNull(store.readManifest())

        val request = fetchSuccessfully(MockResponse().setBody(EMPTY_JSON_OBJECT))

        assertFalse(file.exists())
        assertNull(request.getHeader(IF_NONE_MATCH_HEADER))
        verifyStoredETag(null)
    }

    @Test
    fun sendGenericUserAgentWithoutCookiesOrLanguage() = runTest {
        fetchSuccessfully(MockResponse().setBody(MANIFEST).setHeader(SET_COOKIE_HEADER, TEST_COOKIE))

        val request = fetchSuccessfully(MockResponse().setResponseCode(304))

        assertEquals(USER_AGENT_VALUE, request.getHeader(USER_AGENT_HEADER))
        assertNull(request.getHeader(COOKIE_HEADER))
        assertNull(request.getHeader(ACCEPT_LANGUAGE_HEADER))
    }

    @Test
    fun retryAndKeepBodyAndETagWhenServerFails() = runTest {
        seedManifest()
        server.enqueue(MockResponse().setResponseCode(503).setBody(SERVER_ERROR_BODY))

        assertEquals(Result.retry(), fetch())
        takeRequest()

        assertEquals(MANIFEST, getStoredFile().readText())
        verifyStoredETag()
    }

    @Test
    fun retryAndKeepBodyAndETagWhenResponseIsInterrupted() = runTest {
        seedManifest()
        server.enqueue(MockResponse().setBody(MANIFEST).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        assertEquals(Result.retry(), fetch())
        takeRequest()

        assertEquals(MANIFEST, getStoredFile().readText())
        verifyStoredETag()
    }

    private suspend fun seedManifest() = fetchSuccessfully(MockResponse().setBody(MANIFEST).setHeader(ETAG_HEADER, OLD_TAG))

    private suspend fun verifyStoredETag(expected: String? = OLD_TAG) {
        assertEquals(expected, fetchSuccessfully(MockResponse().setResponseCode(304)).getHeader(IF_NONE_MATCH_HEADER))
    }

    private suspend fun fetchSuccessfully(response: MockResponse): RecordedRequest {
        server.enqueue(response)

        assertEquals(Result.success(), fetch())

        return takeRequest()
    }

    private suspend fun fetch() = fetcher.fetch(server.url(MANIFEST_PATH).toString())

    private fun takeRequest() = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun getStoredFile(): File = directory.listFiles()!!.single()

    private companion object {
        const val MANIFEST_DIRECTORY_NAME = "page-translation"
        const val MANIFEST_PREFERENCES_FILE_NAME = "manifest.preferences_pb"
        const val MANIFEST_PATH = "/translations/models/models.json"

        const val MODEL_PAIR = "fr-en"
        const val MODEL_FILE_KEY = "model"

        const val ETAG_HEADER = "ETag"
        const val IF_NONE_MATCH_HEADER = "If-None-Match"
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT_VALUE = "DuckDuckGo"
        const val SET_COOKIE_HEADER = "Set-Cookie"
        const val COOKIE_HEADER = "Cookie"
        const val ACCEPT_LANGUAGE_HEADER = "Accept-Language"
        const val TEST_COOKIE = "example=value; Path=/"

        const val EMPTY_JSON_OBJECT = "{}"
        const val INCOMPLETE_JSON_OBJECT = "{"
        const val NULL_JSON = "null"
        const val MISSING_MODELS_MANIFEST = """{"schemaVersion":1,"baseUrl":"/"}"""
        const val FUTURE_SCHEMA_MANIFEST = """{"schemaVersion":2,"baseUrl":"/","models":{}}"""
        const val UPDATED_MANIFEST = """{"schemaVersion":1,"baseUrl":"new","models":{}}"""
        const val UPDATED_BASE_URL = "new"
        const val CORRUPT_CONTENT = "broken"
        const val SERVER_ERROR_BODY = "unavailable"

        const val OLD_TAG = "\"first\""
        const val NEW_TAG = "W/\"second\""

        val MANIFEST = """
            {
              "schemaVersion": 1,
              "generated": "2026-11-02T10:14:30Z",
              "baseUrl": "https://example.test/",
              "extra": true,
              "models": {
                "fr-en": {
                  "sourceLanguage": "fr",
                  "targetLanguage": "en",
                  "version": "3.0",
                  "architecture": "base-memory",
                  "totalBytes": 25020240,
                  "files": {
                    "model": {
                      "path": "model.zst",
                      "bytes": 22712124,
                      "sha256": "a",
                      "decompressedSize": 31561787,
                      "decompressedHash": "b"
                    },
                    "lexicalShortlist": {
                      "path": "short.zst",
                      "bytes": 2,
                      "sha256": "c",
                      "decompressedSize": 5,
                      "decompressedHash": "d"
                    },
                    "vocab": {
                      "path": "vocab.zst",
                      "bytes": 3,
                      "sha256": "e",
                      "decompressedSize": 6,
                      "decompressedHash": "f"
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
