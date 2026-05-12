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

package com.duckduckgo.downloads.impl

import com.duckduckgo.downloads.api.FileDownloader.PendingFileDownload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class PendingDownloadStoreTest {

    private val trackedKeys = mutableListOf<String>()

    private fun createDownload(url: String = "https://example.com/file.txt") = PendingFileDownload(
        url = url,
        subfolder = "Downloads",
        directory = File("/tmp"),
    )

    private fun putAndTrack(download: PendingFileDownload): String {
        return PendingDownloadStore.put(download).also { trackedKeys.add(it) }
    }

    @org.junit.After
    fun tearDown() {
        trackedKeys.forEach { PendingDownloadStore.remove(it) }
        trackedKeys.clear()
    }

    @Test
    fun whenDownloadStoredThenGetReturnsIt() {
        val download = createDownload()
        val key = putAndTrack(download)

        val result = PendingDownloadStore.get(key)

        assertEquals(download, result)
    }

    @Test
    fun whenKeyDoesNotExistThenGetReturnsNull() {
        val result = PendingDownloadStore.get("nonexistent-key")

        assertNull(result)
    }

    @Test
    fun whenDownloadRemovedThenGetReturnsNull() {
        val download = createDownload()
        val key = putAndTrack(download)

        PendingDownloadStore.remove(key)

        assertNull(PendingDownloadStore.get(key))
    }

    @Test
    fun whenRemoveCalledWithUnknownKeyThenNoError() {
        PendingDownloadStore.remove("nonexistent-key")
    }

    @Test
    fun whenGetCalledMultipleTimesThenEntryPersists() {
        val download = createDownload()
        val key = putAndTrack(download)

        assertEquals(download, PendingDownloadStore.get(key))
        assertEquals(download, PendingDownloadStore.get(key))
    }

    @Test
    fun whenMaxEntriesExceededThenOldestIsEvicted() {
        val keys = (1..6).map { i ->
            putAndTrack(createDownload(url = "https://example.com/$i"))
        }

        assertNull(PendingDownloadStore.get(keys[0]))
        assertNotNull(PendingDownloadStore.get(keys[5]))
    }

    @Test
    fun whenMaxEntriesExceededThenNewestEntriesSurvive() {
        val keys = (1..7).map { i ->
            putAndTrack(createDownload(url = "https://example.com/$i"))
        }

        // First two should be evicted (6 and 7 pushed out 1 and 2)
        assertNull(PendingDownloadStore.get(keys[0]))
        assertNull(PendingDownloadStore.get(keys[1]))
        // Last five should survive
        for (i in 2..6) {
            assertNotNull(PendingDownloadStore.get(keys[i]))
        }
    }

    @Test
    fun whenEntryManuallyRemovedThenEvictionStillWorksCorrectly() {
        putAndTrack(createDownload(url = "https://example.com/1")).also { PendingDownloadStore.remove(it) }
        val key2 = putAndTrack(createDownload(url = "https://example.com/2"))

        // After removing key1, key2 is the eldest. Adding 5 more (total 6) evicts key2.
        val moreKeys = (3..7).map { i ->
            putAndTrack(createDownload(url = "https://example.com/$i"))
        }

        assertNull(PendingDownloadStore.get(key2))
        moreKeys.forEach { assertNotNull(PendingDownloadStore.get(it)) }
    }
}
