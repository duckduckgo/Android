/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.sync.algorithm

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.algorithm.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDeduplicationPersister
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesSyncPersisterAlgorithm
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SavedSitesDeduplicationPersisterTest {

    // move this to unit test
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var duplicateFinder: SavedSitesDuplicateFinder

    private lateinit var persister: SavedSitesDeduplicationPersister

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)
        duplicateFinder = RealSavedSitesDuplicateFinder(repository)

        persister = SavedSitesDeduplicationPersister(repository, duplicateFinder)
    }

    @Test
    fun whenProcessingBookmarkNotPresentLocallyThenBookmarkIsInserted() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        assertTrue(repository.getBookmarkById(bookmark.id) == null)

        persister.processBookmark(bookmark, bookmark.id, SavedSitesNames.BOOKMARKS_ROOT, "timestamp")

        assertTrue(repository.getBookmarkById(bookmark.id) != null)
    }

    @Test
    fun whenProcessingBookmarkDuplicateThenBookmarkIdIsReplaced() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        assertTrue(repository.getBookmarkById(bookmark.id) != null)

        val remoteBookmark = bookmark.copy(id = "remotebookmark1")
        persister.processBookmark(remoteBookmark, remoteBookmark.id, SavedSitesNames.BOOKMARKS_ROOT, "timestamp")

        assertTrue(repository.getBookmarkById(bookmark.id) == null)

        val replacedBookmark = repository.getBookmarkById(remoteBookmark.id)
        assertTrue(replacedBookmark != null)
        assertTrue(replacedBookmark!!.title == bookmark.title)
    }

    @Test
    fun whenProcessingFavouriteNotPresentLocallyThenBookmarkIsInserted() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        assertTrue(repository.getFavoriteById(favourite.id) == null)

        persister.processFavourite(favourite, "timestamp")

        assertTrue(repository.getFavoriteById(favourite.id) != null)
    }

    @Test
    fun whenProcessingFavouriteDuplicateThenFavouriteIdIsReplaced() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        repository.insert(favourite)
        assertTrue(repository.getFavoriteById(favourite.id) != null)

        val remoteFavourite = favourite.copy(id = "remotebookmark1")
        persister.processFavourite(remoteFavourite, "timestamp")

        assertTrue(repository.getFavoriteById(favourite.id) == null)
        assertTrue(repository.getFavoriteById(remoteFavourite.id) != null)
    }

    @Test
    fun whenProcessingFolderNotPresentLocallyThenFolderIsInserted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        assertTrue(repository.getFolder(folder.id) == null)

        persister.processBookmarkFolder(folder, "timestamp")

        assertTrue(repository.getFolder(folder.id) != null)
    }
}
