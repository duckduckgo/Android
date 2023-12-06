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
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.sync.FakeDisplayModeSettingsRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.RealFavoritesDelegate
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.algorithm.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDeduplicationPersister
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateFinder
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.savedsites.store.SavedSitesSyncMetadataDao
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SavedSitesDeduplicationPersisterTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository
    private lateinit var syncSavedSitesRepository: SyncSavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var savedSitesMetadataDao: SavedSitesSyncMetadataDao
    private lateinit var duplicateFinder: SavedSitesDuplicateFinder

    private lateinit var persister: SavedSitesDeduplicationPersister

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()
        savedSitesMetadataDao = db.savedSitesSyncMetadataDao()

        val favoritesDelegate = RealFavoritesDelegate(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            FakeDisplayModeSettingsRepository(),
            coroutinesTestRule.testDispatcherProvider,
        )

        syncSavedSitesRepository = RealSyncSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, savedSitesMetadataDao)
        repository = RealSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDelegate,
            coroutinesTestRule.testDispatcherProvider,
        )
        duplicateFinder = RealSavedSitesDuplicateFinder(repository, syncSavedSitesRepository)

        persister = SavedSitesDeduplicationPersister(repository, syncSavedSitesRepository, duplicateFinder)
    }

    @Test
    fun whenProcessingBookmarkNotPresentLocallyThenBookmarkIsInserted() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        assertTrue(repository.getBookmarkById(bookmark.id) == null)

        persister.processBookmark(bookmark, SavedSitesNames.BOOKMARKS_ROOT)

        assertTrue(repository.getBookmarkById(bookmark.id) != null)
    }

    @Test
    fun whenProcessingDeletedBookmarkNotPresentLocallyThenBookmarkIsNotInserted() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp", deleted = "1")
        assertTrue(repository.getBookmarkById(bookmark.id) == null)

        persister.processBookmark(bookmark, SavedSitesNames.BOOKMARKS_ROOT)

        assertTrue(repository.getBookmarkById(bookmark.id) == null)
    }

    @Test
    fun whenProcessingBookmarkDuplicateThenBookmarkIdIsReplaced() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        assertTrue(repository.getBookmarkById(bookmark.id) != null)

        val remoteBookmark = bookmark.copy(id = "remotebookmark1")
        persister.processBookmark(remoteBookmark, SavedSitesNames.BOOKMARKS_ROOT)

        assertTrue(repository.getBookmarkById(bookmark.id) == null)

        val replacedBookmark = repository.getBookmarkById(remoteBookmark.id)
        assertTrue(replacedBookmark != null)
        assertTrue(replacedBookmark!!.title == bookmark.title)
    }

    @Test
    fun whenProcessingFavouriteNotPresentLocallyThenFavouriteIsInserted() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        assertTrue(repository.getFavoriteById(favourite.id) == null)

        persister.processFavourite(favourite, SavedSitesNames.FAVORITES_ROOT)

        assertTrue(syncSavedSitesRepository.getFavoriteById(favourite.id, SavedSitesNames.FAVORITES_ROOT) != null)
    }

    @Test
    fun whenProcessingDeletedFavouriteNotPresentLocallyThenFavouriteIsNotInserted() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0, deleted = "1")
        assertTrue(repository.getFavoriteById(favourite.id) == null)

        persister.processFavourite(favourite, SavedSitesNames.FAVORITES_ROOT)

        assertTrue(syncSavedSitesRepository.getFavoriteById(favourite.id, SavedSitesNames.FAVORITES_ROOT) == null)
    }

    @Test
    fun whenProcessingFavouritePresentOnDifferentFolderThenFavouriteIsInserted() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        repository.insert(favourite)
        assertTrue(syncSavedSitesRepository.getFavoriteById(favourite.id, SavedSitesNames.FAVORITES_ROOT) != null)
        assertTrue(syncSavedSitesRepository.getFavoriteById(favourite.id, SavedSitesNames.FAVORITES_DESKTOP_ROOT) == null)

        persister.processFavourite(favourite, SavedSitesNames.FAVORITES_DESKTOP_ROOT)

        assertTrue(syncSavedSitesRepository.getFavoriteById(favourite.id, SavedSitesNames.FAVORITES_ROOT) != null)
        assertTrue(syncSavedSitesRepository.getFavoriteById(favourite.id, SavedSitesNames.FAVORITES_DESKTOP_ROOT) != null)
    }

    @Test
    fun whenProcessingFavouriteDuplicateThenFavouriteIdIsReplaced() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        repository.insert(favourite)
        assertTrue(repository.getFavoriteById(favourite.id) != null)

        val remoteFavourite = favourite.copy(id = "remotebookmark1")
        persister.processFavourite(remoteFavourite, SavedSitesNames.FAVORITES_ROOT)

        assertTrue(repository.getFavoriteById(favourite.id) == null)
        assertTrue(repository.getFavoriteById(remoteFavourite.id) != null)
    }

    @Test
    fun whenProcessingFolderNotPresentLocallyThenFolderIsInserted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        assertTrue(repository.getFolder(folder.id) == null)

        persister.processBookmarkFolder(folder)

        assertTrue(repository.getFolder(folder.id) != null)
    }

    @Test
    fun whenProcessingDeletedFolderNotPresentLocallyThenFolderIsNotInserted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, deleted = "1")
        assertTrue(repository.getFolder(folder.id) == null)

        persister.processBookmarkFolder(folder)

        assertTrue(repository.getFolder(folder.id) == null)
    }

    @Test
    fun whenProcessingFolderPresentLocallyThenFolderIsReplaced() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        repository.insert(folder)
        assertTrue(repository.getFolder(folder.id) != null)

        val remoteFolder = folder.copy(name = "remotefolder1")
        persister.processBookmarkFolder(remoteFolder)

        assertTrue(repository.getFolder(folder.id) != null)
        assertTrue(repository.getFolder(folder.id)!!.name == remoteFolder.name)
    }

    @Test
    fun whenProcessingRemoteDeletdFolderPresentLocallyThenFolderIsDeleted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        repository.insert(folder)
        assertTrue(repository.getFolder(folder.id) != null)

        val deletedFolder = folder.copy(deleted = "1")
        persister.processBookmarkFolder(deletedFolder)

        assertTrue(repository.getFolder(folder.id) == null)
    }
}
