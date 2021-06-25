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
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.global.db.AppDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class SavedSitesExporterTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var favoritesDao: FavoritesDao
    private lateinit var exporter: RealSavedSitesExporter

    private lateinit var filesDir: File

    @Before
    fun before() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        bookmarksDao = db.bookmarksDao()
        favoritesDao = db.favoritesDao()
        filesDir = context.filesDir
        exporter = RealSavedSitesExporter(context.contentResolver, bookmarksDao, favoritesDao, RealSavedSitesParser())
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenSomeBookmarksExistThenExportingSucceeds() = runBlocking {
        val bookmark = BookmarkEntity(id = 1, title = "example", url = "www.example.com")
        bookmarksDao.insert(bookmark)

        val testFile = File(filesDir, "test_bookmarks.html")
        val localUri = Uri.fromFile(testFile)

        val result = exporter.export(localUri)
        testFile.delete()

        assertTrue(result is ExportSavedSitesResult.Success)
    }

    @Test
    fun whenFileDoesNotExistThenExportingFails() = runBlocking {
        val bookmark = BookmarkEntity(id = 1, title = "example", url = "www.example.com")
        bookmarksDao.insert(bookmark)

        val localUri = Uri.parse("uridoesnotexist")

        val result = exporter.export(localUri)

        assertTrue(result is ExportSavedSitesResult.Error)
    }

    @Test
    fun whenNoSavedSitesExistThenNothingIsExported() = runBlocking {
        val localUri = Uri.parse("whatever")
        val result = exporter.export(localUri)
        assertTrue(result is ExportSavedSitesResult.NoSavedSitesExported)
    }

    @Test
    fun whenSomeFavoritesExistThenExportingSucceeds() = runBlocking {
        val favorite = FavoriteEntity(id = 1, title = "example", url = "www.example.com", position = 0)
        favoritesDao.insert(favorite)

        val testFile = File(filesDir, "test_favorites.html")
        val localUri = Uri.fromFile(testFile)

        val result = exporter.export(localUri)
        testFile.delete()

        assertTrue(result is ExportSavedSitesResult.Success)
    }

}
