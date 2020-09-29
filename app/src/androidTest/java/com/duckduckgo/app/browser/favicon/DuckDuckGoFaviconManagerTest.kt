/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.browser.favicon

import android.graphics.Bitmap
import android.widget.ImageView
import androidx.core.net.toUri
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_TEMP_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@ExperimentalCoroutinesApi
class DuckDuckGoFaviconManagerTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockFaviconPersister: FaviconPersister = mock()
    private val mockBookmarksDao: BookmarksDao = mock()
    private val mockFireproofWebsiteDao: FireproofWebsiteDao = mock()
    private val mockLocationPermissionsDao: LocationPermissionsDao = mock()
    private val mockFaviconDownloader: FaviconDownloader = mock()
    private val mockFile: File = File("test")

    private lateinit var testee: FaviconManager

    @Before
    fun setup() {
        testee = DuckDuckGoFaviconManager(
            mockFaviconPersister,
            mockBookmarksDao,
            FireproofWebsiteRepository(mockFireproofWebsiteDao, coroutineRule.testDispatcherProvider, mock()),
            LocationPermissionsRepository(mockLocationPermissionsDao, mock(), coroutineRule.testDispatcherProvider),
            mockFaviconDownloader,
            coroutineRule.testDispatcherProvider
        )
    }

    @Test
    fun whenLoadFromTempIfFileExistsThenGetFaviconFromDisk() = coroutineRule.runBlocking {
        givenFaviconExistsInTemp()

        testee.loadFromTemp("subfolder", "example.com")

        verify(mockFaviconDownloader).getFaviconFromDisk(any())
    }

    @Test
    fun whenLoadFromTempIfFileDoesNotExistThenDoNothing() = coroutineRule.runBlocking {
        testee.loadFromTemp("subfolder", "example.com")

        verify(mockFaviconDownloader, never()).getFaviconFromDisk(any())
    }

    @Test
    fun whenLoadToViewFromPersistedThenLoadView() = coroutineRule.runBlocking {
        givenFaviconExistsInDirectory(FAVICON_PERSISTED_DIR)
        val view: ImageView = mock()

        testee.loadToViewFromPersisted("example.com", view)

        verify(mockFaviconDownloader).loadFaviconToView(mockFile, view)
    }

    @Test
    fun whenLoadToViewFromPersistedIfCannotFindFaviconThenDownloadFromUrl() = coroutineRule.runBlocking {
        val view: ImageView = mock()
        val url = "https://example.com"

        testee.loadToViewFromPersisted(url, view)

        verify(mockFaviconDownloader).getFaviconFromUrl(url.toUri().faviconLocation()!!)
    }

    @Test
    fun whenLoadToViewFromTempThenLoadView() = coroutineRule.runBlocking {
        givenFaviconExistsInDirectory(FAVICON_TEMP_DIR)
        val view: ImageView = mock()

        testee.loadToViewFromTemp("subFolder", "example.com", view)

        verify(mockFaviconDownloader).loadFaviconToView(mockFile, view)
    }

    @Test
    fun whenLoadToViewFromTempIfCannotFindFaviconThenDownloadFromUrl() = coroutineRule.runBlocking {
        val view: ImageView = mock()
        val url = "https://example.com"

        testee.loadToViewFromTemp("subFolder", url, view)

        verify(mockFaviconDownloader).getFaviconFromUrl(url.toUri().faviconLocation()!!)
    }

    @Test
    fun whenPrefetchToTempThenGetFaviconFromUrlAndStoreFile() = coroutineRule.runBlocking {
        val bitmap = asBitmap()
        val url = "https://example.com"
        whenever(mockFaviconDownloader.getFaviconFromUrl(url.toUri().faviconLocation()!!)).thenReturn(bitmap)

        testee.prefetchToTemp("subFolder", url)

        verify(mockFaviconPersister).store(FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com")
    }

    @Test
    fun whenPrefetchToTempAndCannotDownloadThenReturnNull() = coroutineRule.runBlocking {
        val url = "https://example.com"
        whenever(mockFaviconDownloader.getFaviconFromUrl(url.toUri().faviconLocation()!!)).thenReturn(null)

        val file = testee.prefetchToTemp("subFolder", url)

        assertNull(file)
    }

    @Test
    fun whenPrefetchToTempAndDomainDoesNotExistThenReturnNull() = coroutineRule.runBlocking {
        val file = testee.prefetchToTemp("subFolder", "example.com")

        assertNull(file)
    }

    @Test
    fun whenSaveToTempIfFaviconHasBetterQualityThenReplacePersistedFavicons() = coroutineRule.runBlocking {
        val bitmap = asBitmap()
        whenever(mockFireproofWebsiteDao.fireproofWebsitesCountByDomain(any())).thenReturn(1)
        whenever(mockFaviconPersister.store(FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com")).thenReturn(File("example"))

        testee.saveToTemp("subFolder", bitmap, "example.com")

        verify(mockFaviconPersister).store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, bitmap, "example.com")
    }

    @Test
    fun whenSaveToTempIfFaviconDoesNotHaveBetterQualityThenDoNotReplacePersistedFavicons() = coroutineRule.runBlocking {
        val bitmap = asBitmap()
        whenever(mockFireproofWebsiteDao.fireproofWebsitesCountByDomain(any())).thenReturn(1)
        whenever(mockFaviconPersister.store(FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com")).thenReturn(null)

        testee.saveToTemp("subFolder", bitmap, "example.com")

        verify(mockFaviconPersister, never()).store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, bitmap, "example.com")
    }

    @Test
    fun whenSaveToTempThenStoreFile() = coroutineRule.runBlocking {
        val bitmap = asBitmap()

        testee.saveToTemp("subFolder", bitmap, "example.com")

        verify(mockFaviconPersister).store(FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com")
    }

    @Test
    fun whenPersistFaviconThenCopyToPersistedDirectory() = coroutineRule.runBlocking {
        givenFaviconExistsInTemp()

        testee.persistFavicon("subFolder", "example.com")

        verify(mockFaviconPersister).copyToDirectory(mockFile, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, "example.com")
    }

    @Test
    fun whenPersistFaviconIfFaviconDoesNotExistThenDoNotCopyToPersistedDirectory() = coroutineRule.runBlocking {
        testee.persistFavicon("subFolder", "example.com")

        verify(mockFaviconPersister, never()).copyToDirectory(any(), any(), any(), any())
    }

    @Test
    fun whenDeletePersistedFaviconIfNoRemainingFaviconsInDatabaseThenDeleteFavicon() = coroutineRule.runBlocking {
        whenever(mockFireproofWebsiteDao.fireproofWebsitesCountByDomain(any())).thenReturn(1)
        testee.deletePersistedFavicon("example.com")

        verify(mockFaviconPersister).deletePersistedFavicon("example.com")
    }

    @Test
    fun whenDeletePersistedFaviconIfRemainingFaviconsInDatabaseThenDoNotDeleteFavicon() = coroutineRule.runBlocking {
        whenever(mockFireproofWebsiteDao.fireproofWebsitesCountByDomain(any())).thenReturn(2)

        testee.deletePersistedFavicon("example.com")

        verify(mockFaviconPersister, never()).deletePersistedFavicon("example.com")
    }

    @Test
    fun whenDeleteOldFaviconThenDeleteFaviconFromTempDirectory() = coroutineRule.runBlocking {
        testee.deleteOldTempFavicon("subFolder", "example.com")

        verify(mockFaviconPersister).deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, "subFolder", "example.com")
    }

    @Test
    fun whenDeleteAllTempThenDeleteAllFaviconsFromTempDirectory() = coroutineRule.runBlocking {
        testee.deleteAllTemp()

        verify(mockFaviconPersister).deleteAll(FAVICON_TEMP_DIR)
    }

    private fun asBitmap(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)

    private fun givenFaviconExistsInTemp() {
        whenever(mockFaviconPersister.faviconFile(any(), any(), any())).thenReturn(mockFile)
    }

    private fun givenFaviconExistsInDirectory(directory: String) {
        whenever(mockFaviconPersister.faviconFile(eq(directory), any(), any())).thenReturn(mockFile)
    }

}
