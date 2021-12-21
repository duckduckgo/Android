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

import android.content.Context
import android.graphics.Bitmap
import android.widget.ImageView
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.core.net.toUri
import androidx.test.annotation.UiThreadTest
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_PERSISTED_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.FAVICON_TEMP_DIR
import com.duckduckgo.app.browser.favicon.FileBasedFaviconPersister.Companion.NO_SUBFOLDER
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteDao
import com.duckduckgo.app.fire.fireproofwebsite.data.FireproofWebsiteRepository
import com.duckduckgo.app.global.faviconLocation
import com.duckduckgo.app.location.data.LocationPermissionsDao
import com.duckduckgo.app.location.data.LocationPermissionsRepository
import com.duckduckgo.app.runBlocking
import com.nhaarman.mockitokotlin2.*
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class DuckDuckGoFaviconManagerTest {

    @get:Rule var coroutineRule = CoroutineTestRule()

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    private val mockFaviconPersister: FaviconPersister = mock()
    private val mockBookmarksDao: BookmarksDao = mock()
    private val mockFavoriteRepository: FavoritesRepository = mock()
    private val mockFireproofWebsiteDao: FireproofWebsiteDao = mock()
    private val mockLocationPermissionsDao: LocationPermissionsDao = mock()
    private val mockFaviconDownloader: FaviconDownloader = mock()
    private val mockFile: File = File("test")
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var testee: FaviconManager

    @Before
    fun setup() {
        whenever(mockFavoriteRepository.favoritesCountByDomain(any())).thenReturn(0)

        testee =
            DuckDuckGoFaviconManager(
                faviconPersister = mockFaviconPersister,
                bookmarksDao = mockBookmarksDao,
                fireproofWebsiteRepository =
                    FireproofWebsiteRepository(
                        mockFireproofWebsiteDao, coroutineRule.testDispatcherProvider, mock()),
                locationPermissionsRepository =
                    LocationPermissionsRepository(
                        mockLocationPermissionsDao, mock(), coroutineRule.testDispatcherProvider),
                favoritesRepository = mockFavoriteRepository,
                faviconDownloader = mockFaviconDownloader,
                dispatcherProvider = coroutineRule.testDispatcherProvider)
    }

    @Test
    fun whenLoadFromDiskIfFileExistsInTempThenGetFaviconFromDisk() =
        coroutineRule.runBlocking {
            givenFaviconExistsInTemp()

            testee.loadFromDisk("subfolder", "example.com")

            verify(mockFaviconDownloader).getFaviconFromDisk(any())
        }

    @Test
    fun whenLoadFromDiskIfFileExistsInPersistedThenGetFaviconFromDisk() =
        coroutineRule.runBlocking {
            givenFaviconExistsInDirectory(FAVICON_PERSISTED_DIR)

            testee.loadFromDisk("subfolder", "example.com")

            verify(mockFaviconDownloader).getFaviconFromDisk(any())
        }

    @Test
    fun whenLoadFromDiskIfFileDoesNotExistThenDoNothing() =
        coroutineRule.runBlocking {
            testee.loadFromDisk("subfolder", "example.com")

            verify(mockFaviconDownloader, never()).getFaviconFromDisk(any())
        }

    @Test
    @UiThreadTest
    fun whenLoadToViewFromLocalOrFallbackIfCannotFindFaviconThenDownloadFromUrl() =
        coroutineRule.runBlocking {
            val view = ImageView(context)
            val url = "https://example.com"

            testee.loadToViewFromLocalOrFallback(url = url, view = view)

            verify(mockFaviconDownloader).getFaviconFromUrl(url.toUri().faviconLocation()!!)
        }

    @Test
    @UiThreadTest
    fun whenLoadToViewFromLocalOrFallbackWithTabIdIfCannotFindFaviconThenDownloadFromUrl() =
        coroutineRule.runBlocking {
            val view = ImageView(context)
            val url = "https://example.com"

            testee.loadToViewFromLocalOrFallback("subFolder", "example.com", view)

            verify(mockFaviconDownloader).getFaviconFromUrl(url.toUri().faviconLocation()!!)
        }

    @Test
    fun whenTryFetchFaviconForUrlThenGetFaviconFromUrlAndStoreFile() =
        coroutineRule.runBlocking {
            val bitmap = asBitmap()
            val url = "https://example.com"
            whenever(mockFaviconDownloader.getFaviconFromUrl(url.toUri().faviconLocation()!!))
                .thenReturn(bitmap)

            testee.tryFetchFaviconForUrl("subFolder", url)

            verify(mockFaviconPersister).store(FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com")
        }

    @Test
    fun whenTryFetchFaviconForUrlAndCannotDownloadThenReturnNull() =
        coroutineRule.runBlocking {
            val url = "https://example.com"
            whenever(mockFaviconDownloader.getFaviconFromUrl(url.toUri().faviconLocation()!!))
                .thenReturn(null)

            val file = testee.tryFetchFaviconForUrl("subFolder", url)

            assertNull(file)
        }

    @Test
    fun whenStoreFaviconIfFaviconHasBetterQualityThenReplacePersistedFavicons() =
        coroutineRule.runBlocking {
            val bitmap = asBitmap()
            givenFaviconShouldBePersisted()
            whenever(
                    mockFaviconPersister.store(
                        FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com"))
                .thenReturn(File("example"))

            testee.storeFavicon("subFolder", FaviconSource.ImageFavicon(bitmap, "example.com"))

            verify(mockFaviconPersister)
                .store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, bitmap, "example.com")
        }

    @Test
    fun whenStoreFaviconIfFaviconDoesNotHaveBetterQualityThenDoNotReplacePersistedFavicons() =
        coroutineRule.runBlocking {
            val bitmap = asBitmap()
            givenFaviconShouldBePersisted()
            whenever(
                    mockFaviconPersister.store(
                        FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com"))
                .thenReturn(null)

            testee.storeFavicon("subFolder", FaviconSource.ImageFavicon(bitmap, "example.com"))

            verify(mockFaviconPersister, never())
                .store(FAVICON_PERSISTED_DIR, NO_SUBFOLDER, bitmap, "example.com")
        }

    @Test
    fun whenStoreFaviconThenStoreFile() =
        coroutineRule.runBlocking {
            val bitmap = asBitmap()

            testee.storeFavicon("subFolder", FaviconSource.ImageFavicon(bitmap, "example.com"))

            verify(mockFaviconPersister).store(FAVICON_TEMP_DIR, "subFolder", bitmap, "example.com")
        }

    @Test
    fun whenPersistFaviconThenCopyToPersistedDirectory() =
        coroutineRule.runBlocking {
            givenFaviconExistsInTemp()

            testee.persistCachedFavicon("subFolder", "example.com")

            verify(mockFaviconPersister)
                .copyToDirectory(mockFile, FAVICON_PERSISTED_DIR, NO_SUBFOLDER, "example.com")
        }

    @Test
    fun whenPersistFaviconIfFaviconDoesNotExistThenDoNotCopyToPersistedDirectory() =
        coroutineRule.runBlocking {
            testee.persistCachedFavicon("subFolder", "example.com")

            verify(mockFaviconPersister, never()).copyToDirectory(any(), any(), any(), any())
        }

    @Test
    fun whenDeletePersistedFaviconIfNoRemainingFaviconsInDatabaseThenDeleteFavicon() =
        coroutineRule.runBlocking {
            givenFaviconShouldBePersisted()
            testee.deletePersistedFavicon("example.com")

            verify(mockFaviconPersister).deletePersistedFavicon("example.com")
        }

    @Test
    fun whenDeletePersistedFaviconIfRemainingFaviconsInDatabaseThenDoNotDeleteFavicon() =
        coroutineRule.runBlocking {
            whenever(mockFireproofWebsiteDao.fireproofWebsitesCountByDomain(any())).thenReturn(2)

            testee.deletePersistedFavicon("example.com")

            verify(mockFaviconPersister, never()).deletePersistedFavicon("example.com")
        }

    @Test
    fun whenDeleteOldFaviconThenDeleteFaviconFromTempDirectory() =
        coroutineRule.runBlocking {
            testee.deleteOldTempFavicon("subFolder", "example.com")

            verify(mockFaviconPersister)
                .deleteFaviconsForSubfolder(FAVICON_TEMP_DIR, "subFolder", "example.com")
        }

    @Test
    fun whenDeleteAllTempThenDeleteAllFaviconsFromTempDirectory() =
        coroutineRule.runBlocking {
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

    private fun givenFaviconShouldBePersisted() {
        whenever(mockFireproofWebsiteDao.fireproofWebsitesCountByDomain(any())).thenReturn(1)
    }
}
