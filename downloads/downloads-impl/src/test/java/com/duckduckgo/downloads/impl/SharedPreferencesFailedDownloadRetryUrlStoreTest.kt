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

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedPreferencesFailedDownloadRetryUrlStoreTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDataStore = PreferenceDataStoreFactory.create(
        scope = coroutineRule.testScope,
        produceFile = { context.preferencesDataStoreFile("downloads_retry_urls_test") },
    )

    private val store = SharedPreferencesFailedDownloadRetryUrlStore(testDataStore)

    @Test
    fun whenUrlSavedThenItCanBeRetrieved() = runTest {
        store.saveRetryUrl(1L, "https://example.com/file.txt")

        assertEquals("https://example.com/file.txt", store.getRetryUrl(1L))
    }

    @Test
    fun whenNoUrlSavedThenGetReturnsNull() = runTest {
        assertNull(store.getRetryUrl(1L))
    }

    @Test
    fun whenUrlRemovedThenGetReturnsNull() = runTest {
        store.saveRetryUrl(1L, "https://example.com/file.txt")
        store.removeRetryUrl(1L)

        assertNull(store.getRetryUrl(1L))
    }

    @Test
    fun whenMultipleUrlsSavedThenEachCanBeRetrievedIndependently() = runTest {
        store.saveRetryUrl(1L, "https://example.com/file1.txt")
        store.saveRetryUrl(2L, "https://example.com/file2.txt")

        assertEquals("https://example.com/file1.txt", store.getRetryUrl(1L))
        assertEquals("https://example.com/file2.txt", store.getRetryUrl(2L))
    }

    @Test
    fun whenRemovingOneUrlThenOtherUrlsAreUnaffected() = runTest {
        store.saveRetryUrl(1L, "https://example.com/file1.txt")
        store.saveRetryUrl(2L, "https://example.com/file2.txt")

        store.removeRetryUrl(1L)

        assertNull(store.getRetryUrl(1L))
        assertEquals("https://example.com/file2.txt", store.getRetryUrl(2L))
    }

    @Test
    fun whenUrlSavedForSameDownloadIdThenItIsOverwritten() = runTest {
        store.saveRetryUrl(1L, "https://example.com/old.txt")
        store.saveRetryUrl(1L, "https://example.com/new.txt")

        assertEquals("https://example.com/new.txt", store.getRetryUrl(1L))
    }

    @Test
    fun whenDataUriSavedThenItCanBeRetrieved() = runTest {
        val largeDataUri = "data:image/png;base64," + "A".repeat(10_000)
        store.saveRetryUrl(1L, largeDataUri)

        assertEquals(largeDataUri, store.getRetryUrl(1L))
    }

    @Test
    fun whenRemovingNonExistentUrlThenNoError() = runTest {
        store.removeRetryUrl(999L)

        assertNull(store.getRetryUrl(999L))
    }

    @Test
    fun whenClearCalledThenAllUrlsAreRemoved() = runTest {
        store.saveRetryUrl(1L, "https://example.com/file1.txt")
        store.saveRetryUrl(2L, "https://example.com/file2.txt")

        store.clear()

        assertNull(store.getRetryUrl(1L))
        assertNull(store.getRetryUrl(2L))
    }
}
