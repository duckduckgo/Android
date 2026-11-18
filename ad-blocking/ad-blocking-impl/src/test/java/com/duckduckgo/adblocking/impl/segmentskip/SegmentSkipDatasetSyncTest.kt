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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@SuppressLint("DenyListedApi")
@RunWith(AndroidJUnit4::class)
class SegmentSkipDatasetSyncTest {
    @get:Rule val coroutineRule = CoroutineTestRule()
    private val feature = FakeFeatureToggleFactory.create(SegmentSkipFeature::class.java)
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val file = File(context.filesDir, "sponsor-prefixes.bin")
    private lateinit var store: SegmentSkipBinaryStore
    private val service = FakeService()
    private lateinit var sync: SegmentSkipDatasetSync

    @Before fun setup() {
        file.delete()
        feature.mvpEnabled().setRawStoredState(Toggle.State(remoteEnableState = true))
        store = SegmentSkipBinaryStore(context)
        sync = SegmentSkipDatasetSync(service, store)
    }

    @After fun clean() { file.delete() }

    @Test fun initialAndMultiStepSync() = runTest {
        service.latest = 1
        service.responses[0L] = full(1, listOf(1))
        sync.sync()
        service.latest = 3
        service.responses[1L] = diff(2, listOf(2), listOf(1), listOf(2))
        service.responses[2L] = diff(3, listOf(3), emptyList(), listOf(2, 3))
        sync.sync()
        assertEquals(listOf(0L, 1L, 2L), service.requests)
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
        assertEquals(1L, SegmentSkipBinaryStore(context).revision())
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
        assertTrue(SegmentSkipBinaryStore(context).contains("00000001"))
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
        assertEquals(1L, store.revision())
        assertTrue(store.contains("00000001"))
    }

    @Test fun workerRetriesFailuresAndDefersUnsupportedVersions() = runTest {
        store.apply(0, full(1, listOf(1)).inputStream())
        service.latest = 2
        service.failure = IOException("network")
        val worker = TestListenableWorkerBuilder<SegmentSkipDatasetWorker>(context).build().also {
            it.sync = sync
            it.feature = feature
            it.dispatchers = coroutineRule.testDispatcherProvider
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

    private class FakeService : SegmentSkipDatasetService {
        var revisionRequests = 0
        var latest = 0L
        var failure: IOException? = null
        val responses = mutableMapOf<Long, ByteArray>()
        val requests = mutableListOf<Long>()
        override suspend fun revision(): SegmentSkipRevisionResponse {
            revisionRequests++
            return SegmentSkipRevisionResponse(latest)
        }
        override suspend fun binaryHashPrefixes(revision: Long): ResponseBody {
            requests += revision
            failure?.let { throw it }
            return responses.getValue(revision).toResponseBody()
        }
    }
}
