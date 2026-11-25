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

package com.duckduckgo.pagetranslation.impl.languagepack

import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.pagetranslation.impl.languagepack.LanguagePackFetcher.FetchResult
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.ModelFile
import com.duckduckgo.pagetranslation.impl.manifest.ModelsManifest.TranslationModel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS

class LanguagePackFetcherTest {
    @get:Rule
    val coroutineRule = CoroutineTestRule()

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val client: OkHttpClient = mock()
    private val stagingDirectory get() = temporaryFolder.root.resolve(PAIR)
    private val usableSpace = ArrayDeque(listOf(Long.MAX_VALUE))
    private val fetcher = RealLanguagePackFetcher(lazyOf(client), coroutineRule.testDispatcherProvider) {
        if (usableSpace.size > 1) usableSpace.removeFirst() else usableSpace.first()
    }
    private val progress = mutableListOf<Long>()

    @Test
    fun downloadEveryFileOneAfterAnotherAndReportProgress() = runTest {
        respondWith(MODEL_URL to ok(MODEL_BODY), VOCAB_URL to ok(VOCAB_BODY))

        assertEquals(FetchResult.SUCCESS, fetch())

        val requests = argumentCaptor<Request>().apply { verify(client, times(2)).newCall(capture()) }.allValues
        assertEquals(listOf(MODEL_URL, VOCAB_URL), requests.map { it.url.toString() })
        assertEquals(MODEL_BODY, stagingDirectory.resolve("model.zst").readText())
        assertEquals(VOCAB_BODY, stagingDirectory.resolve("vocab.zst").readText())
        assertEquals(listOf(MODEL_BODY.length.toLong(), (MODEL_BODY + VOCAB_BODY).length.toLong()), progress)
    }

    @Test
    fun replaceFilesLeftByAnEarlierRun() = runTest {
        stagingDirectory.mkdirs()
        stagingDirectory.resolve(LEFTOVER_FILE).writeText(LEFTOVER_FILE)
        respondWith(MODEL_URL to ok(MODEL_BODY), VOCAB_URL to ok(VOCAB_BODY))

        assertEquals(FetchResult.SUCCESS, fetch())

        assertFalse(stagingDirectory.resolve(LEFTOVER_FILE).exists())
    }

    @Test
    fun failWithoutRequestsWhenStorageCannotHoldDownloadedAndDecompressedFiles() = runTest {
        usableSpace[0] = MODEL.files.values.sumOf { it.bytes + it.decompressedSize } - 1

        assertEquals(FetchResult.NOT_ENOUGH_STORAGE, fetch())

        verifyNoInteractions(client)
    }

    @Test
    fun failWhenServerRejectsAFile() = runTest {
        respondWith(MODEL_URL to ok(MODEL_BODY), VOCAB_URL to (NOT_FOUND to "".toResponseBody()))

        assertEquals(FetchResult.FAILURE, fetch())

        assertFalse(stagingDirectory.exists())
    }

    @Test
    fun failWhenRequestFails() = runTest {
        val call: Call = mock()
        whenever(call.execute()).thenThrow(IOException())
        whenever(client.newCall(any())).thenReturn(call)

        assertEquals(FetchResult.FAILURE, fetch())
    }

    @Test
    fun failWhenDownloadIsInterrupted() = runTest {
        respondWith(MODEL_URL to (OK to InterruptedBody()))

        assertEquals(FetchResult.FAILURE, fetch())
    }

    @Test
    fun reportNotEnoughStorageWhenVolumeFillsDuringDownload() = runTest {
        usableSpace += 0L
        respondWith(MODEL_URL to (OK to InterruptedBody()))

        assertEquals(FetchResult.NOT_ENOUGH_STORAGE, fetch())

        assertFalse(stagingDirectory.exists())
    }

    @Test
    fun failWithoutRequestsWhenStagingDirectoryCannotBeCreated() = runTest {
        temporaryFolder.root.resolve(BLOCKING_FILE).writeText(BLOCKING_FILE)

        assertEquals(FetchResult.FAILURE, fetcher.fetch(BASE_URL, MODEL, temporaryFolder.root.resolve(BLOCKING_FILE).resolve(PAIR)) {})

        verifyNoInteractions(client)
    }

    @Test
    fun cancelRequestAndKeepStagingDirectoryWhenJobIsStoppedWhileWaitingForResponse() = runBlocking {
        val isStarted = blockingCall { cancelled ->
            cancelled.awaitUnlessStuck()
            null
        }

        stopWhile(isStarted)

        assertTrue(stagingDirectory.exists())
        assertEquals(emptyList<String>(), stagingDirectory.list()!!.toList())
    }

    @Test
    fun cancelRequestAndKeepStagingDirectoryWhenJobIsStoppedWhileReadingBody() = runBlocking {
        val isStarted = blockingCall { cancelled -> response(MODEL_URL, OK, BlockingBody(cancelled)) }

        stopWhile(isStarted)

        assertTrue(stagingDirectory.exists())
    }

    private suspend fun stopWhile(isStarted: CountDownLatch) = coroutineScope {
        val dispatchers = DefaultDispatcherProvider()
        val fetcher = RealLanguagePackFetcher(lazyOf(client), dispatchers) { Long.MAX_VALUE }
        val fetching = launch(dispatchers.io()) { fetcher.fetch(BASE_URL, MODEL, stagingDirectory) {} }
        assertTrue(isStarted.await(TIMEOUT_SECONDS, SECONDS))

        // ends only once the stopped job has cancelled the call
        withTimeout(SECONDS.toMillis(TIMEOUT_SECONDS)) { fetching.cancelAndJoin() }
    }

    private fun blockingCall(execute: (cancelled: CountDownLatch) -> Response?): CountDownLatch {
        val isStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val call: Call = mock()
        whenever(call.cancel()).then { cancelled.countDown() }
        whenever(call.execute()).thenAnswer {
            isStarted.countDown()
            execute(cancelled) ?: throw IOException("Canceled")
        }
        whenever(client.newCall(any())).thenReturn(call)
        return isStarted
    }

    private suspend fun fetch(model: TranslationModel = MODEL) =
        fetcher.fetch(BASE_URL, model, stagingDirectory) { progress += it }

    private fun respondWith(vararg responses: Pair<String, Pair<Int, ResponseBody>>) {
        whenever(client.newCall(any())).thenAnswer { invocation ->
            val request = invocation.getArgument<Request>(0)
            val (code, body) = responses.toMap().getValue(request.url.toString())
            mock<Call>().also { call -> whenever(call.execute()).thenReturn(response(request.url.toString(), code, body)) }
        }
    }

    private fun response(url: String, code: Int, body: ResponseBody) =
        Response.Builder().request(Request.Builder().url(url).build()).protocol(Protocol.HTTP_1_1).code(code).message("").body(body).build()

    private fun ok(body: String) = OK to body.toResponseBody()

    private class InterruptedBody : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = -1

        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long = throw IOException()

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }.buffer()
    }

    private class BlockingBody(private val cancelled: CountDownLatch) : ResponseBody() {
        override fun contentType(): MediaType? = null

        override fun contentLength(): Long = -1

        override fun source(): BufferedSource = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                cancelled.awaitUnlessStuck()
                throw IOException("Canceled")
            }

            override fun timeout(): Timeout = Timeout.NONE

            override fun close() = Unit
        }.buffer()
    }

    private companion object {
        const val TIMEOUT_SECONDS = 5L

        const val OK = 200
        const val NOT_FOUND = 404

        const val PAIR = "fr-en"
        const val BASE_URL = "https://models.test/translations/models/"
        const val MODEL_PATH = "fr-en/3.0/base-memory/model.zst"
        const val VOCAB_PATH = "fr-en/3.0/base-memory/vocab.zst"
        const val MODEL_URL = BASE_URL + MODEL_PATH
        const val VOCAB_URL = BASE_URL + VOCAB_PATH
        const val MODEL_BODY = "model-bytes"
        const val VOCAB_BODY = "vocab"
        const val LEFTOVER_FILE = "partial.zst"
        const val BLOCKING_FILE = "not-a-directory"

        val MODEL = TranslationModel(
            sourceLanguage = "fr",
            targetLanguage = "en",
            version = "3.0",
            architecture = "base-memory",
            totalBytes = 16,
            files = linkedMapOf(
                "model" to ModelFile(MODEL_PATH, bytes = 11, sha256 = "a", decompressedSize = 20, decompressedHash = "b"),
                "vocab" to ModelFile(VOCAB_PATH, bytes = 5, sha256 = "c", decompressedSize = 9, decompressedHash = "d"),
            ),
        )

        // a call that's never cancelled gives up later than the stop does, so the test fails instead of hanging
        fun CountDownLatch.awaitUnlessStuck() = await(TIMEOUT_SECONDS * 2, SECONDS)
    }
}
