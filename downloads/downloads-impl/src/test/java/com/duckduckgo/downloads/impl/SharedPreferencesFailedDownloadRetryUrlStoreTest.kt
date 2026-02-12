/*
 * Copyright (c) 2025 DuckDuckGo
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(manifest = Config.NONE)
class SharedPreferencesFailedDownloadRetryUrlStoreTest {

    private lateinit var store: SharedPreferencesFailedDownloadRetryUrlStore

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        store = SharedPreferencesFailedDownloadRetryUrlStore(context)
    }

    @Test
    fun whenUrlSavedThenItCanBeRetrieved() {
        store.saveRetryUrl(1L, "https://example.com/file.txt")

        assertEquals("https://example.com/file.txt", store.getRetryUrl(1L))
    }

    @Test
    fun whenNoUrlSavedThenGetReturnsNull() {
        assertNull(store.getRetryUrl(1L))
    }

    @Test
    fun whenUrlRemovedThenGetReturnsNull() {
        store.saveRetryUrl(1L, "https://example.com/file.txt")
        store.removeRetryUrl(1L)

        assertNull(store.getRetryUrl(1L))
    }

    @Test
    fun whenMultipleUrlsSavedThenEachCanBeRetrievedIndependently() {
        store.saveRetryUrl(1L, "https://example.com/file1.txt")
        store.saveRetryUrl(2L, "https://example.com/file2.txt")

        assertEquals("https://example.com/file1.txt", store.getRetryUrl(1L))
        assertEquals("https://example.com/file2.txt", store.getRetryUrl(2L))
    }

    @Test
    fun whenRemovingOneUrlThenOtherUrlsAreUnaffected() {
        store.saveRetryUrl(1L, "https://example.com/file1.txt")
        store.saveRetryUrl(2L, "https://example.com/file2.txt")

        store.removeRetryUrl(1L)

        assertNull(store.getRetryUrl(1L))
        assertEquals("https://example.com/file2.txt", store.getRetryUrl(2L))
    }

    @Test
    fun whenUrlSavedForSameDownloadIdThenItIsOverwritten() {
        store.saveRetryUrl(1L, "https://example.com/old.txt")
        store.saveRetryUrl(1L, "https://example.com/new.txt")

        assertEquals("https://example.com/new.txt", store.getRetryUrl(1L))
    }

    @Test
    fun whenDataUriSavedThenItCanBeRetrieved() {
        val largeDataUri = "data:image/png;base64," + "A".repeat(10_000)
        store.saveRetryUrl(1L, largeDataUri)

        assertEquals(largeDataUri, store.getRetryUrl(1L))
    }

    @Test
    fun whenRemovingNonExistentUrlThenNoError() {
        store.removeRetryUrl(999L)

        assertNull(store.getRetryUrl(999L))
    }

    @Test
    fun whenClearCalledThenAllUrlsAreRemoved() {
        store.saveRetryUrl(1L, "https://example.com/file1.txt")
        store.saveRetryUrl(2L, "https://example.com/file2.txt")

        store.clear()

        assertNull(store.getRetryUrl(1L))
        assertNull(store.getRetryUrl(2L))
    }
}
