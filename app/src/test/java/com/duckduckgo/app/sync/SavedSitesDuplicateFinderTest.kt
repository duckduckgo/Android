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

package com.duckduckgo.app.sync

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
import com.duckduckgo.savedsites.impl.sync.RealSavedSitesDuplicateFinder
import com.duckduckgo.savedsites.impl.sync.SavedSitesDuplicateFinder
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
    private lateinit var repository: SavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao

    private lateinit var duplicateFinder: SavedSitesDuplicateFinder

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)
        duplicateFinder = RealSavedSitesDuplicateFinder(repository)
    }

    @Test
    fun whenBookmarkAlreadyExistsThenDuplicateIsFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(bookmark)

        Assert.assertTrue(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentIdsAndUrlsThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title", "www.examples.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentIdsAndSameUrlsThenDuplicateIsFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertTrue(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentUrlThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark1", "title", "www.examples.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentTitleThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark1", "title1", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentTitleAndIdsThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title1", "www.example.com", "folder2", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentParentIdThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder3", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenBookmarkAlreadyExistsWithDifferentParentIdAndIdsThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val updatedBookmark = Bookmark("bookmark2", "title", "www.example.com", "folder3", "timestamp")
        repository.insert(bookmark)

        val result = duplicateFinder.isBookmarkDuplicate(updatedBookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenBookmarkNotPresentThenDuplicateIsNotFound() {
        val bookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")

        val result = duplicateFinder.isBookmarkDuplicate(bookmark)

        Assert.assertFalse(result)
    }

    @Test
    fun whenFavouriteAlreadyExistsThenDuplicateIsFound() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        repository.insert(favourite)

        val result = duplicateFinder.isFavouriteDuplicate(favourite)

        Assert.assertTrue(result)
    }

    @Test
    fun whenFavouriteAlreadyExistsWithDifferentIdsThenDuplicateIsFound() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        val updatedFavourite = Favorite("bookmark2", "title", "www.example.com", "timestamp", 0)
        repository.insert(favourite)

        val result = duplicateFinder.isFavouriteDuplicate(updatedFavourite)

        Assert.assertTrue(result)
    }

    @Test
    fun whenFavouriteAlreadyExistsWithDifferentUrlThenDuplicateIsFound() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        val updatedFavourite = Favorite("bookmark1", "title", "www.examples.com", "timestamp", 0)
        repository.insert(favourite)

        val result = duplicateFinder.isFavouriteDuplicate(updatedFavourite)

        Assert.assertFalse(result)
    }

    @Test
    fun whenFavouriteAlreadyExistsWithDifferentTitleThenDuplicateIsFound() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)
        val updatedFavourite = Favorite("bookmark1", "title1", "www.example.com", "timestamp", 0)
        repository.insert(favourite)

        val result = duplicateFinder.isFavouriteDuplicate(updatedFavourite)

        Assert.assertFalse(result)
    }

    @Test
    fun whenFavouriteNotPresentThenDuplicateIsFound() {
        val favourite = Favorite("bookmark1", "title", "www.example.com", "timestamp", 0)

        val result = duplicateFinder.isFavouriteDuplicate(favourite)

        Assert.assertFalse(result)
    }

    @Test
    fun whenFolderNotPresentThenDuplicateIsNotFound() {
        val folder = BookmarkFolder("folder", "Folder", SavedSitesNames.BOOMARKS_ROOT, 0, 0, "timestamp")

        val result = duplicateFinder.isFolderDuplicate(folder)

        Assert.assertFalse(result)
    }

    @Test
    fun whenFolderPresentWithSameParentIdThenDuplicateIsFound() {
        val folder = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOMARKS_ROOT, 0, 0, "timestamp")
        val folder1 = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOMARKS_ROOT, 0, 0, "timestamp")
        repository.insert(folder)

        val result = duplicateFinder.isFolderDuplicate(folder1)

        Assert.assertTrue(result)
    }

    @Test
    fun whenFolderPresentWithDifferentiParentIdThenDuplicateIsNotFound() {
        val folder = BookmarkFolder("folder1", "Folder", SavedSitesNames.BOOMARKS_ROOT, 0, 0, "timestamp")
        val folder1 = BookmarkFolder("folder2", "Folder", folder.id, 2, 1, "timestamp")
        repository.insert(folder)

        val result = duplicateFinder.isFolderDuplicate(folder1)

        Assert.assertFalse(result)
    }
}
