/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.savedsites.impl.service

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.service.ExportSavedSitesResult
import com.duckduckgo.savedsites.api.service.ImportSavedSitesResult
import com.duckduckgo.savedsites.api.service.SavedSitesExporter
import com.duckduckgo.savedsites.api.service.SavedSitesImporter
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class SavedSitesManagerTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var importer: SavedSitesImporter = mock()
    private var exporter: SavedSitesExporter = mock()
    private var pixel: Pixel = mock()
    private lateinit var testee: RealSavedSitesManager

    @Before
    fun before() {
        testee = RealSavedSitesManager(importer, exporter, pixel)
    }

    @Test
    fun whenBookmarksImportSucceedsThenPixelIsSent() = runTest {
        val someUri = Uri.parse("")
        val importedBookmarks = listOf(aBookmark())
        whenever(importer.import(someUri)).thenReturn(ImportSavedSitesResult.Success(importedBookmarks))

        testee.import(someUri)

        verify(pixel).fire(
            SavedSitesPixelName.BOOKMARK_IMPORT_SUCCESS,
            mapOf(Pixel.PixelParameter.BOOKMARK_COUNT to importedBookmarks.size.toString()),
        )
    }

    @Test
    fun whenFavoritesImportSucceedsThenPixelIsSent() = runTest {
        val someUri = Uri.parse("")
        val importedFavorites = listOf(aFavorite())
        whenever(importer.import(someUri)).thenReturn(ImportSavedSitesResult.Success(importedFavorites))

        testee.import(someUri)

        verify(pixel).fire(
            SavedSitesPixelName.BOOKMARK_IMPORT_SUCCESS,
            mapOf(Pixel.PixelParameter.BOOKMARK_COUNT to importedFavorites.size.toString()),
        )
    }

    @Test
    fun whenSavedSitesImportFailsThenPixelIsSent() = runTest {
        val someUri = Uri.parse("")
        whenever(importer.import(someUri)).thenReturn(ImportSavedSitesResult.Error(Exception()))

        testee.import(someUri)

        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_IMPORT_ERROR)
    }

    @Test
    fun whenSavedSitesExportSucceedsThenPixelIsSent() = runTest {
        val someUri = Uri.parse("")
        whenever(exporter.export(someUri)).thenReturn(ExportSavedSitesResult.Success)

        testee.export(someUri)

        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_EXPORT_SUCCESS)
    }

    @Test
    fun whenSavedSitesExportFailsThenPixelIsSent() = runTest {
        val someUri = Uri.parse("")
        whenever(exporter.export(someUri)).thenReturn(ExportSavedSitesResult.Error(Exception()))

        testee.export(someUri)

        verify(pixel).fire(SavedSitesPixelName.BOOKMARK_EXPORT_ERROR)
    }

    private fun aBookmark(): SavedSite.Bookmark {
        return SavedSite.Bookmark(UUID.randomUUID().toString(), "title", "url", UUID.randomUUID().toString(), lastModified = DatabaseDateFormatter.iso8601())
    }

    private fun aFavorite(): SavedSite.Favorite {
        return SavedSite.Favorite(UUID.randomUUID().toString(), "title", "url", lastModified = DatabaseDateFormatter.iso8601(),0)
    }
}
