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
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.MissingEntitiesRelationReconciler
import com.duckduckgo.savedsites.impl.RealFavoritesDelegate
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.algorithm.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesDuplicateResult
import com.duckduckgo.savedsites.impl.sync.store.RealSavedSitesSyncEntitiesStore
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDatabase
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TODO: Move this test out of app module and into saved-sites module once AppDatabase is in its own module
 */
@RunWith(AndroidJUnit4::class)
class SavedSitesDuplicateFinderTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var savedSitesDatabase: SavedSitesSyncMetadataDatabase

    private lateinit var repository: SavedSitesRepository
    private lateinit var syncRepository: SyncSavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var savedSitesMetadataDao: SavedSitesSyncMetadataDao
    private lateinit var duplicateFinder: SavedSitesDuplicateFinder
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

        syncRepository = RealSyncSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, savedSitesMetadataDao, store)
        repository = RealSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDelegate,
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )

        duplicateFinder = RealSavedSitesDuplicateFinder(repository)
    }

    @Test
    fun whenBookmarkAlreadyExistsThenDuplicateIsFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(bookmark)

        Assert.assertTrue(result == SavedSitesDuplicateResult.Duplicate(bookmark.id))
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentIdsAndUrlsThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title", "www.examples.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentIdsAndSameUrlsThenDuplicateIsFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result == SavedSitesDuplicateResult.Duplicate(bookmark.id))
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentUrlThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark1", "title", "www.examples.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentTitleThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark1", "title1", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)
        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentTitleAndIdsThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title1", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentParentIdThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder3", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentParentIdAndIdsThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title", "www.example.com", "folder3", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.findBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenBookmarkNotPresentThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")

        val result = duplicateFinder.findBookmarkDuplicate(bookmark)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenFolderNotPresentThenDuplicateIsNotFound() {
        val folder = BookmarkFolder("folder", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")

        val result = duplicateFinder.findFolderDuplicate(folder)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenFolderPresentWithSameParentIdThenDuplicateIsFound() {
        val folder = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val folder1 = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        repository.insert(folder)

        val result = duplicateFinder.findFolderDuplicate(folder1)

        Assert.assertTrue(result == SavedSitesDuplicateResult.Duplicate(folder.id))
    }

    @Test
    fun whenFolderPresentWithDifferentParentIdThenDuplicateIsNotFound() {
        val folder = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val folder1 = BookmarkFolder("folder2", "Folder", folder.id, 2, 1, "timestamp")
        repository.insert(folder)

        val result = duplicateFinder.findFolderDuplicate(folder1)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenFolderPresentWithSameTitleDifferentParentIdThenDuplicateIsNotFound() {
        val folder = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val folder1 = BookmarkFolder("folder2", "Folder", folder.id, 2, 1, "timestamp")
        repository.insert(folder)

        val result = duplicateFinder.findFolderDuplicate(folder1)

        Assert.assertTrue(result is SavedSitesDuplicateResult.NotDuplicate)
    }

    @Test
    fun whenFolderPresentWithSameTitleAndParentIdThenDuplicateIsNotFound() {
        val folder = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val folder1 = BookmarkFolder("folder2", "Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        repository.insert(folder)

        val result = duplicateFinder.findFolderDuplicate(folder1)

        Assert.assertTrue(result == SavedSitesDuplicateResult.Duplicate(folder.id))
    }
}
