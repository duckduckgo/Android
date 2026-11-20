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
package com.duckduckgo.adblocking.impl.segmentskip

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.duckduckgo.adblocking.impl.AdBlockingPixelNames
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.BufferedSource
import okio.buffer
import okio.source
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SegmentSkipDatasetSyncTest {
    @get:Rule val coroutineRule = CoroutineTestRule()
    private val feature = FakeFeatureToggleFactory.create(SegmentSkipFeature::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "sponsor-prefixes.bin")
    private lateinit var store: SegmentSkipBinaryStore
    private val service = FakeService()
    private val segmentsProvider: SegmentSkipSegmentsProvider = mock()
    private val pixel: Pixel = mock()
    private val freshness: SegmentSkipDatasetFreshness = mock()
    private val preferences: SegmentSkipUserPreferences = mock()
    private lateinit var sync: SegmentSkipDatasetSync

    @Before fun setup() {
        file.delete()
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        store = SegmentSkipBinaryStore(context, DefaultDispatcherProvider())
        whenever(preferences.modeFlow()).thenReturn(flowOf(SegmentSkipMode.ALWAYS_ASK))
        runBlocking { whenever(freshness.isStale(any())).thenReturn(true) }
        sync = SegmentSkipDatasetSync(service, store, segmentsProvider, pixel, freshness, preferences, feature)
    }

    @After fun clean() { file.delete() }

    @Test fun binaryRequestUsesHashPrefixEndpoint() {
        val service = Retrofit.Builder().baseUrl("https://duckduckgo.com/").build().create(SegmentSkipDatasetService::class.java)
        val url = service.binaryHashPrefixes(42).request().url
        assertEquals("protection.duckduckgo.com", url.host)
        assertEquals("/api/protection/segments/v1/android/hashPrefix", url.encodedPath)
        assertEquals(setOf("revision"), url.queryParameterNames)
        assertEquals("42", url.queryParameter("revision"))
    }

    @Test fun invalidLatestRevisionIsRejectedWithoutRequestingDataset() = runTest {
        for (revision in listOf(-1L, 0x100000000L)) {
            service.latest = revision
            try {
                sync.sync()
                fail("Expected invalid revision")
            } catch (_: IllegalArgumentException) { }
            assertTrue(service.requests.isEmpty())
        }
        service.latest = 0
        sync.sync()
        assertTrue(service.requests.isEmpty())
    }

    @Test fun initialAndMultiStepSync() = runTest {
        service.latest = 1
        service.responses[0L] = full(1, listOf(1))
        sync.sync()
        service.latest = 3
        service.responses[1L] = diff(2, listOf(2), listOf(1), listOf(2))
        service.responses[2L] = diff(3, listOf(3), emptyList(), listOf(2, 3))
        sync.sync()
        assertEquals(listOf(0L, 1L, 2L), service.requests)
        verify(freshness, times(2)).recordSuccess()
        verify(segmentsProvider, times(3)).clearCache()
        verify(pixel, times(3)).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_COUNT, parameters = emptyMap())
        verify(pixel, times(3)).enqueueFire(
            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_DAILY,
            parameters = emptyMap(),
            type = Pixel.PixelType.Daily(),
        )
        sync.sync()
        verify(freshness, times(3)).recordSuccess()
        verify(pixel, times(3)).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_COUNT, parameters = emptyMap())
        assertEquals(3L, store.revision())
        assertFalse(store.contains("00000001"))
        assertTrue(store.contains("00000002"))
        assertTrue(store.contains("00000003"))
    }

    @Test fun syncsUnsignedRevisionAboveSignedIntRange() = runTest {
        service.latest = 0xffffffffL
        service.responses[0L] = full(0xffffffffL, listOf(1))
        sync.sync()
        assertEquals(0xffffffffL, store.revision())
        assertEquals(listOf(0L), service.requests)
    }

    @Test fun invalidDiffFallsBackToFull() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.responses[1L] = byteArrayOf(1)
        service.responses[0L] = full(2, listOf(2))
        sync.sync()
        assertEquals(listOf(1L, 0L), service.requests)
        verify(pixel).enqueueFire(
            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_VALIDATION_ERROR_COUNT,
            parameters = mapOf("reason" to "decode_error"),
        )
        verify(pixel).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_COUNT, parameters = emptyMap())
        assertEquals(2L, store.revision())
        assertTrue(store.contains("00000002"))
    }

    @Test fun invalidFallbackPreservesOldFileAndCanRetry() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.responses[1L] = byteArrayOf(1)
        service.responses[0L] = byteArrayOf(1)
        try {
            sync.sync()
            fail("Expected invalid file")
        } catch (_: InvalidSegmentSkipFile) { }
        assertEquals(1L, store.revision())
        assertTrue(store.contains("00000001"))
        assertEquals(1L, SegmentSkipBinaryStore(context, DefaultDispatcherProvider()).revision())
        verify(segmentsProvider, never()).clearCache()
        verify(freshness, never()).recordSuccess()
        service.responses[0L] = full(2, listOf(2))
        sync.sync()
        assertEquals(2L, store.revision())
    }

    @Test fun fallbackRejectsDiffAndKeepsOldFile() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.responses[1L] = byteArrayOf(1)
        service.responses[0L] = diff(2, listOf(2), emptyList(), listOf(1, 2))
        try {
            sync.sync()
            fail("Expected full response")
        } catch (_: InvalidSegmentSkipFile) { }
        assertEquals(listOf(1L, 0L), service.requests)
        assertEquals(1L, store.revision())
        assertTrue(store.contains("00000001"))
    }

    @Test fun failedDownloadPreservesOldFileAndCanRetry() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.failure = IOException("network")
        try {
            sync.sync()
            fail("Expected download failure")
        } catch (_: IOException) { }
        assertEquals(1L, store.revision())
        assertTrue(SegmentSkipBinaryStore(context, DefaultDispatcherProvider()).contains("00000001"))
        verify(freshness, never()).recordSuccess()
        verify(pixel).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT, parameters = emptyMap())
        service.failure = null
        service.responses[1L] = diff(2, listOf(2), emptyList(), listOf(1, 2))
        sync.sync()
        assertEquals(2L, store.revision())
    }

    @Test fun unsupportedVersionDoesNotFallback() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.responses[1L] = full(2, listOf(2)).apply { this[4] = 2 }
        try {
            sync.sync()
            fail("Expected unsupported version")
        } catch (_: UnsupportedSegmentSkipFormat) { }
        assertEquals(listOf(1L), service.requests)
        verify(pixel).enqueueFire(
            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_VALIDATION_ERROR_COUNT,
            parameters = mapOf("reason" to "decode_error"),
        )
        assertEquals(1L, store.revision())
        assertTrue(store.contains("00000001"))
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun workerDelaysBothEnableAndRegularChecksAndCanBeCancelled() = runTest {
        whenever(freshness.generation()).thenReturn(0L)
        for (force in listOf(false, true)) {
            val worker = TestListenableWorkerBuilder<SegmentSkipDatasetWorker>(context)
                .setInputData(workDataOf(SegmentSkipDatasetWorker.FORCE_CHECK to force))
                .build().also {
                    it.sync = sync
                    it.feature = feature
                    it.dispatchers = coroutineRule.testDispatcherProvider
                    it.freshness = freshness
                }
            val scheduler = coroutineRule.testScope.testScheduler
            val before = service.revisionRequests
            val cancelled = coroutineRule.testScope.launch { worker.doWork() }
            scheduler.runCurrent()
            assertEquals(before, service.revisionRequests)
            cancelled.cancelAndJoin()
            scheduler.advanceTimeBy(TimeUnit.MINUTES.toMillis(5) + 1)
            scheduler.runCurrent()
            assertEquals(before, service.revisionRequests)

            val check = coroutineRule.testScope.launch { worker.doWork() }
            scheduler.runCurrent()
            assertEquals(before, service.revisionRequests)
            scheduler.advanceTimeBy(TimeUnit.MINUTES.toMillis(5) + 1)
            scheduler.runCurrent()
            check.join()
            assertEquals(before + 1, service.revisionRequests)
        }
    }

    @Test fun consecutivePeriodicChecksRunEvenWhenPreviousCheckIsStillFresh() = runTest {
        whenever(freshness.isStale(any())).thenReturn(false)
        val worker = TestListenableWorkerBuilder<SegmentSkipDatasetWorker>(context)
            .setInputData(workDataOf(SegmentSkipDatasetWorker.PERIODIC_CHECK to true))
            .build().also {
                it.sync = sync
                it.feature = feature
                it.dispatchers = coroutineRule.testDispatcherProvider
                it.freshness = freshness
            }
        assertEquals(Result.success(), worker.doWork())
        assertEquals(Result.success(), worker.doWork())
        assertEquals(2, service.revisionRequests)
    }

    @Test fun disabledWorkerDoesNotRequestRevision() = runTest {
        whenever(preferences.modeFlow()).thenReturn(flowOf(SegmentSkipMode.OFF))
        val worker = TestListenableWorkerBuilder<SegmentSkipDatasetWorker>(context).build().also {
            it.sync = sync
            it.feature = feature
            it.dispatchers = coroutineRule.testDispatcherProvider
            it.freshness = freshness
        }
        assertEquals(Result.success(), worker.doWork())
        assertEquals(0, service.revisionRequests)
        verify(freshness, never()).recordSuccess()
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = false))
        whenever(preferences.modeFlow()).thenReturn(flowOf(SegmentSkipMode.ALWAYS_ASK))
        assertEquals(Result.success(), worker.doWork())
        assertEquals(0, service.revisionRequests)
    }

    @Test fun enableCheckSkipsWhenAnotherCheckSucceededAfterTrigger() = runTest {
        whenever(freshness.generation()).thenReturn(4L)
        sync.sync(enableGeneration = 3L)
        assertEquals(0, service.revisionRequests)
        sync.sync(enableGeneration = 4L)
        assertEquals(1, service.revisionRequests)
    }

    @Test fun workerRetriesFailuresAndDefersUnsupportedVersions() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.failure = IOException("network")
        val worker = TestListenableWorkerBuilder<SegmentSkipDatasetWorker>(context).build().also {
            it.sync = sync
            it.feature = feature
            it.dispatchers = coroutineRule.testDispatcherProvider
            it.freshness = freshness
        }
        assertEquals(Result.retry(), worker.doWork())
        service.failure = null
        service.responses[1L] = full(2, listOf(2)).apply { this[4] = 2 }
        assertEquals(Result.success(), worker.doWork())
        assertEquals(1L, store.revision())
    }

    @Test fun enabledDatasetRequiresMatchingPrefix() = runTest {
        val gate = RealSegmentSkipHashPrefixGate(store, coroutineRule.testDispatcherProvider)
        assertFalse(gate.hasSegments("00000001"))
        store.apply(0, full(1, listOf(1)).inputStream())
        assertTrue(gate.hasSegments("00000001"))
        assertFalse(gate.hasSegments("00000002"))
    }

    @Test fun overlappingSyncsDoNotRetryAnAlreadyAppliedRevision() = runBlocking {
        service.latest = 1
        service.responses[0L] = full(1, listOf(1))
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        service.onRevision = {
            if (service.revisionRequests == 1) {
                firstStarted.complete(Unit)
                releaseFirst.await()
            }
        }
        val first = async(DefaultDispatcherProvider().io()) { sync.sync() }
        firstStarted.await()
        val second = async(DefaultDispatcherProvider().io(), start = CoroutineStart.UNDISPATCHED) { sync.sync() }
        releaseFirst.complete(Unit)
        withTimeout(5000) {
            first.await()
            second.await()
        }
        assertEquals(2, service.revisionRequests)
        assertEquals(listOf(0L), service.requests)
        assertEquals(1L, store.revision())
    }

    @Test fun waitingSyncCanBeCancelledWithoutStartingARequest() = runBlocking {
        service.latest = 1
        service.responses[0L] = full(1, listOf(1))
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        service.onRevision = {
            firstStarted.complete(Unit)
            releaseFirst.await()
        }
        val first = async(DefaultDispatcherProvider().io()) { sync.sync() }
        firstStarted.await()
        val waiting = launch(DefaultDispatcherProvider().io(), start = CoroutineStart.UNDISPATCHED) { sync.sync() }
        withTimeout(5000) { waiting.cancelAndJoin() }
        assertEquals(1, service.revisionRequests)
        releaseFirst.complete(Unit)
        withTimeout(5000) { first.await() }
        assertEquals(listOf(0L), service.requests)
    }

    @Test fun cancellationAbortsBlockedResponseAndKeepsPreviousFile() = runBlocking {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        val readStarted = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val input = object : InputStream() {
            override fun read(): Int {
                readStarted.countDown()
                check(cancelled.await(5, TimeUnit.SECONDS))
                throw IOException("call cancelled")
            }
        }
        val body = object : ResponseBody() {
            private val buffered = input.source().buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1
            override fun source(): BufferedSource = buffered
        }
        val call: Call<ResponseBody> = mock()
        doAnswer {
            it.getArgument<Callback<ResponseBody>>(0).onResponse(call, Response.success(body))
            null
        }.whenever(call).enqueue(any())
        doAnswer {
            cancelled.countDown()
            null
        }.whenever(call).cancel()
        service.overrideCall = call
        val job = launch(DefaultDispatcherProvider().io(), start = CoroutineStart.DEFAULT) { sync.sync() }
        assertTrue(readStarted.await(5, TimeUnit.SECONDS))
        withTimeout(5000) { job.cancelAndJoin() }
        verify(call).cancel()
        verify(freshness, never()).recordSuccess()
        verifyNoInteractions(pixel)
        assertEquals(1L, store.revision())
        assertTrue(SegmentSkipBinaryStore(context, DefaultDispatcherProvider()).contains("00000001"))
        assertFalse(File(context.filesDir, "sponsor-download.tmp").exists())
    }

    @Test fun checksumFailureReportsBoundedReason() = runTest {
        service.latest = 1
        service.responses[0L] = full(1, listOf(1)).apply { this[12] = (this[12].toInt() xor 1).toByte() }

        try {
            sync.sync()
            fail("Expected invalid checksum")
        } catch (_: InvalidSegmentSkipFile) { }

        assertEquals(0L, store.revision())
        verify(pixel).enqueueFire(
            AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_VALIDATION_ERROR_COUNT,
            parameters = mapOf("reason" to "checksum_mismatch"),
        )
    }

    @Test fun localStorageFailureReportsUpdateErrorAndPreservesSnapshot() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.responses[1L] = full(2, listOf(2))
        val blockedDownload = File(context.filesDir, "sponsor-download.tmp")
        assertTrue(blockedDownload.mkdir())

        try {
            sync.sync()
            fail("Expected local failure")
        } catch (_: IOException) {
        } finally {
            blockedDownload.delete()
        }

        assertEquals(1L, store.revision())
        assertTrue(store.contains("00000001"))
        verify(pixel).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATE_ERROR_COUNT, parameters = emptyMap())
        verify(pixel, never()).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT, parameters = emptyMap())
    }

    @Test fun responseReadFailureReportsFetchErrorAndPreservesSnapshot() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("network failure")
        }
        val body = object : ResponseBody() {
            private val buffered = input.source().buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1
            override fun source(): BufferedSource = buffered
        }
        val call: Call<ResponseBody> = mock()
        doAnswer {
            it.getArgument<Callback<ResponseBody>>(0).onResponse(call, Response.success(body))
            null
        }.whenever(call).enqueue(any())
        service.overrideCall = call

        try {
            sync.sync()
            fail("Expected network failure")
        } catch (_: IOException) { }

        assertEquals(1L, store.revision())
        assertTrue(store.contains("00000001"))
        verify(pixel).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_FETCH_ERROR_COUNT, parameters = emptyMap())
        verify(pixel, never()).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATE_ERROR_COUNT, parameters = emptyMap())
    }

    @Test fun responseCleanupAfterPublicationDoesNotReportUpdateError() = runTest {
        service.latest = 1
        val input = object : java.io.ByteArrayInputStream(full(1, listOf(1))) {
            override fun close() { throw IOException("close failed") }
        }
        val body = object : ResponseBody() {
            private val buffered = input.source().buffer()
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = -1
            override fun source(): BufferedSource = buffered
        }
        val call: Call<ResponseBody> = mock()
        doAnswer {
            it.getArgument<Callback<ResponseBody>>(0).onResponse(call, Response.success(body))
            null
        }.whenever(call).enqueue(any())
        service.overrideCall = call

        runCatching { sync.sync() }

        assertEquals(1L, store.revision())
        verify(pixel).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATED_COUNT, parameters = emptyMap())
        verify(pixel, never()).enqueueFire(AdBlockingPixelNames.SEGMENT_SKIP_PREFIXES_UPDATE_ERROR_COUNT, parameters = emptyMap())
    }

    private class FakeService : SegmentSkipDatasetService {
        var revisionRequests = 0
        var onRevision: suspend () -> Unit = {}
        var overrideCall: Call<ResponseBody>? = null
        var latest = 0L
        var failure: IOException? = null
        val responses = mutableMapOf<Long, ByteArray>()
        val requests = mutableListOf<Long>()
        override suspend fun revision(): SegmentSkipRevisionResponse {
            revisionRequests++
            onRevision()
            return SegmentSkipRevisionResponse(latest)
        }
        override fun binaryHashPrefixes(revision: Long): Call<ResponseBody> {
            requests += revision
            failure?.let { throw it }
            overrideCall?.let { return it }
            val body = responses.getValue(revision).toResponseBody()
            val call: Call<ResponseBody> = mock()
            doAnswer {
                it.getArgument<Callback<ResponseBody>>(0).onResponse(call, Response.success(body))
                null
            }.whenever(call).enqueue(any())
            return call
        }
    }
}
