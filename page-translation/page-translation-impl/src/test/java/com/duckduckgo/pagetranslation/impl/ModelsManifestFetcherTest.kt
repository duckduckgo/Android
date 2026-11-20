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

package com.duckduckgo.pagetranslation.impl

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
    private val directory get() = temporaryFolder.root.resolve("page-translation")
    private val preferences = PreferenceDataStoreFactory.create(scope = coroutineRule.testScope) {
        temporaryFolder.root.resolve("manifest.preferences_pb")
    }
    private val store = ModelsManifestStore({ directory }, preferences, coroutineRule.testDispatcherProvider)
    private val fetcher = ModelsManifestFetcher(store, coroutineRule.testDispatcherProvider)

    @Before
    fun startServer() = server.start()

    @After
    fun stopServer() = server.shutdown()

    @Test
    fun firstFetchStoresOriginalBodyAndETag() = runTest {
        val request = seedManifest()

        assertNull(request.getHeader("If-None-Match"))
        assertEquals(MANIFEST, storedFile().readText())
        val model = store.manifest()!!.models.getValue("fr-en")
        assertEquals(25020240L, model.totalBytes)
        assertEquals(31561787L, model.files.getValue("model").decompressedSize)
        verifyStoredETag()
    }

    @Test
    fun notModifiedKeepsBodyAndETag() = runTest {
        seedManifest()
        val file = storedFile()
        val lastModified = file.lastModified()

        val request = fetchSuccessfully(MockResponse().setResponseCode(304).setHeader("ETag", NEW_TAG))

        assertEquals(OLD_TAG, request.getHeader("If-None-Match"))
        assertEquals(MANIFEST, file.readText())
        assertEquals(lastModified, file.lastModified())
        verifyStoredETag()
    }

    @Test
    fun malformedResponseKeepsBodyAndETag() = runTest {
        seedManifest()
        for (invalid in listOf("{", "null", MANIFEST + "{}", "{}", """{"schemaVersion":1,"baseUrl":"/"}""")) {
            fetchSuccessfully(MockResponse().setBody(invalid).setHeader("ETag", NEW_TAG))

            assertEquals(MANIFEST, storedFile().readText())
            verifyStoredETag()
        }
    }

    @Test
    fun futureSchemaKeepsBodyAndETag() = runTest {
        seedManifest()

        fetchSuccessfully(MockResponse().setBody("""{"schemaVersion":2,"baseUrl":"/","models":{}}""").setHeader("ETag", NEW_TAG))

        assertEquals(MANIFEST, storedFile().readText())
        verifyStoredETag()
    }

    @Test
    fun acceptedResponseReplacesBodyAndDropsMissingETag() = runTest {
        seedManifest()
        val updated = """{"schemaVersion":1,"baseUrl":"new","models":{}}"""

        fetchSuccessfully(MockResponse().setBody(updated))

        assertEquals("new", store.manifest()!!.baseUrl)
        verifyStoredETag(null)
    }

    @Test
    fun missingFileClearsETagBeforeFetch() = runTest {
        seedManifest()
        storedFile().delete()

        val request = fetchSuccessfully(MockResponse().setBody("{}"))

        assertNull(request.getHeader("If-None-Match"))
        assertNull(store.manifest())
        verifyStoredETag(null)
    }

    @Test
    fun corruptFileIsDeletedAndETagClearedBeforeFetch() = runTest {
        seedManifest()
        val file = storedFile()
        file.writeText("broken")
        assertNull(store.manifest())

        val request = fetchSuccessfully(MockResponse().setBody("{}"))

        assertFalse(file.exists())
        assertNull(request.getHeader("If-None-Match"))
        verifyStoredETag(null)
    }

    @Test
    fun requestsSendOnlyGenericUserAgentAndNeverSendCookiesOrLanguage() = runTest {
        fetchSuccessfully(MockResponse().setBody(MANIFEST).setHeader("Set-Cookie", "example=value; Path=/"))

        val request = fetchSuccessfully(MockResponse().setResponseCode(304))

        assertEquals("DuckDuckGo", request.getHeader("User-Agent"))
        assertNull(request.getHeader("Cookie"))
        assertNull(request.getHeader("Accept-Language"))
    }

    @Test
    fun serverErrorRetriesAndKeepsBodyAndETag() = runTest {
        seedManifest()
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))

        assertEquals(Result.retry(), fetch())
        takeRequest()
        assertEquals(MANIFEST, storedFile().readText())
        verifyStoredETag()
    }

    @Test
    fun interruptedResponseRetriesAndKeepsBodyAndETag() = runTest {
        seedManifest()
        server.enqueue(MockResponse().setBody(MANIFEST).setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))

        assertEquals(Result.retry(), fetch())
        takeRequest()
        assertEquals(MANIFEST, storedFile().readText())
        verifyStoredETag()
    }

    private suspend fun seedManifest() = fetchSuccessfully(MockResponse().setBody(MANIFEST).setHeader("ETag", OLD_TAG))

    private suspend fun verifyStoredETag(expected: String? = OLD_TAG) {
        assertEquals(expected, fetchSuccessfully(MockResponse().setResponseCode(304)).getHeader("If-None-Match"))
    }

    private suspend fun fetchSuccessfully(response: MockResponse): RecordedRequest {
        server.enqueue(response)
        assertEquals(Result.success(), fetch())
        return takeRequest()
    }

    private suspend fun fetch() = fetcher.fetch(server.url("/translations/models/models.json").toString())

    private fun takeRequest() = checkNotNull(server.takeRequest(2, TimeUnit.SECONDS))

    private fun storedFile(): File = directory.listFiles()!!.single()

    private companion object {
        const val OLD_TAG = "\"first\""
        const val NEW_TAG = "W/\"second\""
        val MANIFEST = """
            {
              "schemaVersion": 1, "generated": "2026-11-02T10:14:30Z", "baseUrl": "https://example.test/", "extra": true,
              "models": {
                "fr-en": {
                  "sourceLanguage": "fr", "targetLanguage": "en", "version": "3.0", "architecture": "base-memory", "totalBytes": 25020240,
                  "files": {
                    "model": {"path":"model.zst","bytes":22712124,"sha256":"a","decompressedSize":31561787,"decompressedHash":"b"},
                    "lexicalShortlist": {"path":"short.zst","bytes":2,"sha256":"c","decompressedSize":5,"decompressedHash":"d"},
                    "vocab": {"path":"vocab.zst","bytes":3,"sha256":"e","decompressedSize":6,"decompressedHash":"f"}
                  }
                }
              }
            }
        """.trimIndent()
    }
}
