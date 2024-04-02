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
import com.duckduckgo.app.bookmarks.BookmarkTestUtils
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.bookmarksRoot
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.favouritesRoot
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.sync.FakeDisplayModeSettingsRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.MissingEntitiesRelationReconciler
import com.duckduckgo.savedsites.impl.RealFavoritesDelegate
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.algorithm.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDeduplicationPersister
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.store.RealSavedSitesSyncEntitiesStore
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDatabase
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
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
    private lateinit var savedSitesDatabase: SavedSitesSyncMetadataDatabase

    private lateinit var repository: SavedSitesRepository
    private lateinit var syncSavedSitesRepository: SyncSavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var savedSitesMetadataDao: SavedSitesSyncMetadataDao
    private lateinit var duplicateFinder: SavedSitesDuplicateFinder
    private lateinit var persister: SavedSitesDeduplicationPersister
    private val store = RealSavedSitesSyncEntitiesStore(
        InstrumentationRegistry.getInstrumentation().context,
    )

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        savedSitesDatabase = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SavedSitesSyncMetadataDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        savedSitesMetadataDao = savedSitesDatabase.syncMetadataDao()

        val favoritesDelegate = RealFavoritesDelegate(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            FakeDisplayModeSettingsRepository(),
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )

        syncSavedSitesRepository = RealSyncSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, savedSitesMetadataDao, store)
        repository = RealSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDelegate,
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )
        duplicateFinder = RealSavedSitesDuplicateFinder(repository)

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
    fun whenProcessingEmptyFavouriteFoldersThenFavouritesAreAdded() {
        // given some favourites
        val firstBatch = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(firstBatch)
        savedSitesRelationsDao.insertList(BookmarkTestUtils.givenFolderWithContent(bookmarksRoot.entityId, firstBatch))

        // when processing the favourites folder
        persister.processFavouritesFolder(favouritesRoot.entityId, firstBatch.map { it.entityId })

        // then all favourites have been added
        val favourites = syncSavedSitesRepository.getFavoritesSync(favouritesRoot.entityId)
        assertTrue(favourites.map { it.id } == firstBatch.map { it.entityId })
    }

    @Test
    fun whenProcessingNotEmptyFavouriteFoldersThenFavouritesAreAdded() {
        // given some favourites
        val firstBatch = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(firstBatch)
        savedSitesRelationsDao.insertList(BookmarkTestUtils.givenFolderWithContent(bookmarksRoot.entityId, firstBatch))
        savedSitesRelationsDao.insertList(BookmarkTestUtils.givenFolderWithContent(favouritesRoot.entityId, firstBatch))

        // and new bookmarks have been added
        val secondBatch = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(secondBatch)
        savedSitesRelationsDao.insertList(BookmarkTestUtils.givenFolderWithContent(bookmarksRoot.entityId, secondBatch))

        // when processing the favourites folder
        persister.processFavouritesFolder(favouritesRoot.entityId, secondBatch.map { it.entityId })

        // then all favourites have been added
        val favourites = repository.getFavoritesSync()
        assertTrue(favourites.map { it.id } == firstBatch.map { it.entityId }.plus(secondBatch.map { it.entityId }))
    }

    @Test
    fun whenProcessingFolderNotPresentLocallyThenFolderIsInserted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        assertTrue(repository.getFolder(folder.id) == null)

        persister.processBookmarkFolder(folder, emptyList())

        assertTrue(repository.getFolder(folder.id) != null)
    }

    @Test
    fun whenProcessingDeletedFolderNotPresentLocallyThenFolderIsNotInserted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, deleted = "1")
        assertTrue(repository.getFolder(folder.id) == null)

        persister.processBookmarkFolder(folder, emptyList())

        assertTrue(repository.getFolder(folder.id) == null)
    }

    @Test
    fun whenProcessingFolderPresentLocallyThenFolderIsReplaced() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        repository.insert(folder)
        assertTrue(repository.getFolder(folder.id) != null)

        val remoteFolder = folder.copy(name = "remotefolder1")
        persister.processBookmarkFolder(remoteFolder, emptyList())

        assertTrue(repository.getFolder(folder.id) != null)
        assertTrue(repository.getFolder(folder.id)!!.name == remoteFolder.name)
    }

    @Test
    fun whenProcessingRemoteDeletedFolderPresentLocallyThenFolderIsDeleted() {
        val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)
        repository.insert(folder)
        assertTrue(repository.getFolder(folder.id) != null)

        val deletedFolder = folder.copy(deleted = "1")
        persister.processBookmarkFolder(deletedFolder, emptyList())

        assertTrue(repository.getFolder(folder.id) == null)
    }
}
