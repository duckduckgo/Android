/*
 * Copyright (c) 2021 DuckDuckGo
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

package com.duckduckgo.app.bookmarks.service

import android.net.Uri
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import com.nhaarman.mockitokotlin2.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class RealBookmarksManagerTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private var importer: BookmarksImporter = mock()
    private var exporter: BookmarksExporter = mock()
    private var pixel: Pixel = mock()
    private lateinit var testee: RealBookmarksManager

    @Before
    fun before() {
        testee = RealBookmarksManager(importer, exporter, pixel)
    }

    @Test
    fun whenBookmarksImportSucceedsThenPixelIsSent() = runBlocking {
        val someUri = Uri.parse("")
        val importedBookmarks = listOf(aBookmark())
        whenever(importer.import(someUri)).thenReturn(ImportBookmarksResult.Success(importedBookmarks))

        testee.import(someUri)

        val pixelSent = AppPixelName.BOOKMARK_IMPORT_SUCCESS.pixelName.replace("%d", importedBookmarks.size.toString())

        verify(pixel).fire(pixelSent)
    }

    @Test
    fun whenBookmarksImportFailsThenPixelIsSent() = runBlocking {
        val someUri = Uri.parse("")
        whenever(importer.import(someUri)).thenReturn(ImportBookmarksResult.Error(Exception()))

        testee.import(someUri)

        verify(pixel).fire(AppPixelName.BOOKMARK_IMPORT_ERROR)
    }

    @Test
    fun whenBookmarksExportSucceedsThenPixelIsSent() = runBlocking {
        val someUri = Uri.parse("")
        whenever(exporter.export(someUri)).thenReturn(ExportBookmarksResult.Success)

        testee.export(someUri)

        verify(pixel).fire(AppPixelName.BOOKMARK_EXPORT_SUCCESS)
    }

    @Test
    fun whenBookmarksExportFailsThenPixelIsSent() = runBlocking {
        val someUri = Uri.parse("")
        whenever(exporter.export(someUri)).thenReturn(ExportBookmarksResult.Error(Exception()))

        testee.export(someUri)

        verify(pixel).fire(AppPixelName.BOOKMARK_EXPORT_ERROR)
    }

    private fun aBookmark(): Bookmark {
        return Bookmark("title", "url")
    }

}
