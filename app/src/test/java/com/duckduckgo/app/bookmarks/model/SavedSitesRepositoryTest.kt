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

package com.duckduckgo.app.bookmarks.model

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.savedsites.BookmarkFolder
import com.duckduckgo.savedsites.BookmarkFolderItem
import com.duckduckgo.savedsites.FolderBranch
import com.duckduckgo.savedsites.SavedSite.Bookmark
import com.duckduckgo.savedsites.SavedSite.Favorite
import com.duckduckgo.savedsites.SavedSitesRepository
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SyncEntitiesDao
import com.duckduckgo.savedsites.store.SyncRelationsDao
import com.duckduckgo.sync.impl.RealSavedSitesRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SavedSitesRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var syncEntitiesDao: SyncEntitiesDao
    private lateinit var syncRelationsDao: SyncRelationsDao

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncEntitiesDao = db.syncEntitiesDao()
        syncRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(syncEntitiesDao, syncRelationsDao)
    }

    @Test
    fun whenNoDataThenFolderContentisEmpty() = runTest {
        repository.getFolderContent(Relation.BOOMARKS_ROOT).test {
            val result = awaitItem()
            assert(result.first.isEmpty())
            assert(result.second.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRootFolderHasOnlyBookmarksThenDataIsRetrieved() = runTest {
        val totalBookmarks = 10
        val entities = givenSomeBookmarks(totalBookmarks)
        syncEntitiesDao.insertList(entities)

        val relation = givenFolderWithContent(Relation.BOOMARKS_ROOT, entities)
        syncRelationsDao.insertList(relation)

        repository.getFolderContent(Relation.BOOMARKS_ROOT).test {
            val result = awaitItem()
            assert(result.first.size == totalBookmarks)
            assert(result.second.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRootFolderHasBookmarksAndFoldersThenDataIsRetrieved() = runTest {
        val totalBookmarks = 10
        val totalFolders = 3

        val entities = givenSomeBookmarks(totalBookmarks)
        syncEntitiesDao.insertList(entities)

        val folders = givenSomeFolders(totalFolders)
        syncEntitiesDao.insertList(folders)

        val relation = givenFolderWithContent(Relation.BOOMARKS_ROOT, entities.plus(folders))
        syncRelationsDao.insertList(relation)

        repository.getFolderContent(Relation.BOOMARKS_ROOT).test {
            val result = awaitItem()
            assert(result.first.size == totalBookmarks)
            assert(result.second.size == totalFolders)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenRequestingDataFromEmptyFolderThenNothingIsRetrieved() = runTest {
        val totalBookmarks = 10
        val totalFolders = 3

        val entities = givenSomeBookmarks(totalBookmarks)
        syncEntitiesDao.insertList(entities)

        val folders = givenSomeFolders(totalFolders)
        syncEntitiesDao.insertList(folders)

        val relation = givenFolderWithContent(Relation.BOOMARKS_ROOT, entities.plus(folders))
        syncRelationsDao.insertList(relation)

        repository.getFolderContent("12").test {
            val result = awaitItem()
            assert(result.first.isEmpty())
            assert(result.second.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenFavoriteIsAddedThenBookmarkIsAlsoAdded() {
        givenEmptyDBState()

        repository.insertFavorite("https://favorite.com", "favorite")

        assert(repository.getBookmark("https://favorite.com") != null)
        assert(repository.getFavorite("https://favorite.com") != null)
    }

    @Test
    fun whenBookmarkIsAddedThenFavoriteIsNotAdded() {
        givenEmptyDBState()

        repository.insertBookmark("https://favorite.com", "favorite")

        assert(repository.getBookmark("https://favorite.com") != null)
        assert(repository.getFavorite("https://favorite.com") == null)
    }

    @Test
    fun whenFavoriteIsAddedAndThenRemovedThenNothingIsRetrieved() {
        givenEmptyDBState()

        repository.insertFavorite("https://favorite.com", "favorite")

        val favorite = repository.getFavorite("https://favorite.com")
        assert(favorite != null)

        repository.delete(favorite!!)
        assert(repository.getFavorite("https://favorite.com") == null)
    }

    @Test
    fun whenBookmarkIsAddedAndThenRemovedThenNothingIsRetrieved() {
        givenEmptyDBState()

        repository.insertBookmark("https://favorite.com", "favorite")

        val bookmark = repository.getBookmark("https://favorite.com")
        assert(bookmark != null)

        repository.delete(bookmark!!)
        assert(repository.getBookmark("https://favorite.com") == null)
    }

    @Test
    fun whenInsertFavoriteThenReturnSavedSite() {
        givenNoFavoritesStored()

        val savedSite = repository.insertFavorite(title = "title", url = "http://example.com")

        assertEquals("title", savedSite.title)
        assertEquals("http://example.com", savedSite.url)
        assertEquals(0, savedSite.position)
    }

    @Test
    fun whenInsertFavoriteWithoutTitleThenSavedSiteUsesUrlAsTitle() {
        givenNoFavoritesStored()

        val savedSite = repository.insertFavorite(title = "", url = "http://example.com")

        assertEquals("http://example.com", savedSite.title)
        assertEquals("http://example.com", savedSite.url)
        assertEquals(0, savedSite.position)
    }

    @Test
    fun whenUserHasFavoritesAndInsertFavoriteThenSavedSiteUsesNextPosition() {
        givenSomeFavoritesStored()

        val savedSite = repository.insertFavorite(title = "Favorite", url = "http://favexample.com")

        Assert.assertEquals("Favorite", savedSite.title)
        Assert.assertEquals("http://favexample.com", savedSite.url)
        Assert.assertEquals(2, savedSite.position)
    }

    @Test
    fun whenDataSourceChangesThenNewListReceived() {
        givenNoFavoritesStored()

        repository.insertFavorite(title = "Favorite", url = "http://favexample.com")

        val testObserver = repository.getFavoritesObservable().test()
        val lastState = testObserver.assertNoErrors().values().last()

        Assert.assertEquals(1, lastState.size)
        Assert.assertEquals("Favorite", lastState.first().title)
        Assert.assertEquals("http://favexample.com", lastState.first().url)
        Assert.assertEquals(0, lastState.first().position)
    }

    @Test
    fun whenFavoriteNameUpdatedThenDataIsUpdated() {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", 0)
        val favoritetwo = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        val favoritethree = Favorite("favorite1", "Favorite", "http://favexample.com", 2)

        givenFavoriteStored(favoriteone)
        givenFavoriteStored(favoritetwo)
        givenFavoriteStored(favoritethree)

        val updatedFavorite = favoriteone.copy(title = "favorite updated")

        repository.update(updatedFavorite)

        assertEquals(repository.getFavorite(favoriteone.url), updatedFavorite)
    }

    @Test
    fun whenFavoritePositionUpdatedThenDataIsUpdated() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", 0)
        val favoritetwo = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        val favoritethree = Favorite("favorite1", "Favorite", "http://favexample.com", 2)

        givenFavoriteStored(favoriteone)
        givenFavoriteStored(favoritetwo)
        givenFavoriteStored(favoritethree)

        val updatedFavoriteOne = favoriteone.copy(position = 2)
        val updatedFavoriteTwo = favoriteone.copy(position = 0)
        val updatedFavoriteThree = favoriteone.copy(position = 1)

        repository.update(updatedFavoriteOne)

        repository.getFavorites().test {
            val favorites = awaitItem()
            assertEquals(favorites, listOf(updatedFavoriteTwo, updatedFavoriteThree, updatedFavoriteOne))
        }
    }

    @Test
    fun whenListReceivedThenUpdateItemsWithNewPositionInDatabase() {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 0)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", 1)

        givenFavoriteStored(favorite, favorite2)

        repository.updateWithPosition(listOf(favorite2, favorite))

        assertEquals(repository.getFavorite(favorite.url), favorite.copy(position = 1))
        assertEquals(repository.getFavorite(favorite2.url), favorite2.copy(position = 0))
    }

    @Test
    fun whenFavoriteDeletedThenDatabaseUpdated() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        givenFavoriteStored(favorite)

        repository.delete(favorite)

        Assert.assertNull(repository.getFavorite(favorite.url))
    }

    @Test
    fun whenUserHasFavoritesThenReturnTrue() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 1)

        givenFavoriteStored(favorite)

        Assert.assertTrue(repository.hasFavorites())
    }

    @Test
    fun whenFavoriteByUrlRequestedAndAvailableThenReturnFavorite() = runTest {
        givenNoFavoritesStored()

        val favorite = repository.insert(Favorite(id = "favorite1", title = "title", url = "www.website.com", position = 0))
        val otherFavorite = repository.insert(Favorite(id = "favorite2", title = "other title", url = "www.other-website.com", position = 1))

        val result = repository.getFavorite("www.website.com")

        Assert.assertEquals(favorite, result)
    }

    @Test
    fun whenFavoriteByUrlRequestedAndNotAvailableThenReturnNull() = runTest {
        givenNoFavoritesStored()

        repository.insert(Favorite(id = "favorite1", title = "title", url = "www.website.com", position = 1))
        repository.insert(Favorite(id = "favorite2", title = "other title", url = "www.other-website.com", position = 2))

        val result = repository.getFavorite("www.test.com")

        Assert.assertNull(result)
    }

    @Test
    fun whenFavoriteByUrlRequestedAndNoFavoritesAvailableThenReturnNull() = runTest {
        val result = repository.getFavorite("www.test.com")

        Assert.assertNull(result)
    }

    @Test
    fun whenAllFavoritesDeletedThenDeleteAllFavorites() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", 2)

        givenFavoriteStored(favorite)
        givenFavoriteStored(favorite2)

        repository.deleteAll()

        givenNoFavoritesStored()
    }

    @Test
    fun whenInsertBookmarkThenPopulateDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(Bookmark(id = "bookmark1", title = "title", url = "foo.com", parentId = Relation.BOOMARKS_ROOT))

        Assert.assertEquals(listOf(bookmark), repository.getBookmarks().first())
    }

    @Test
    fun whenInsertBookmarkByTitleAndUrlThenPopulateDB() = runTest {
        val bookmark = repository.insert(Bookmark(id = "bookmark1", title = "title", url = "foo.com", parentId = Relation.BOOMARKS_ROOT))
        val bookmarkInserted = repository.getBookmark(bookmark.url)

        Assert.assertEquals(bookmark, bookmarkInserted)
    }

    @Test
    fun whenUpdateBookmarkUrlThenUpdateBookmarkInDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(Bookmark(id = "bookmark1", title = "title", url = "foo.com", parentId = Relation.BOOMARKS_ROOT))
        val updatedBookmark = Bookmark(id = bookmark.id, title = "new title", url = "example.com", parentId = Relation.BOOMARKS_ROOT)

        repository.update(updatedBookmark)
        val bookmarkUpdated = repository.getBookmark(updatedBookmark.url)!!

        Assert.assertEquals(updatedBookmark.id, bookmarkUpdated.id)
    }

    @Test
    fun whenUpdateBookmarkFolderThenUpdateBookmarkInDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(Bookmark(id = "bookmark1", title = "title", url = "foo.com", parentId = Relation.BOOMARKS_ROOT))
        val updatedBookmark = Bookmark(id = bookmark.id, title = "title", url = "foo.com", parentId = "folder2")

        repository.update(updatedBookmark)
        val bookmarkUpdated = repository.getBookmark(bookmark.url)!!

        Assert.assertEquals(updatedBookmark.id, bookmarkUpdated.id)
    }

    @Test
    fun whenDeleteBookmarkThenRemoveBookmarkFromDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(Bookmark(id = "bookmark1", title = "title", url = "foo.com", parentId = Relation.BOOMARKS_ROOT))
        repository.delete(bookmark)

        Assert.assertFalse(repository.hasBookmarks())
    }

    @Test
    fun whenBookmarkAddedToRootThenGetFolderReturnsRootFolder() = runTest {
        val bookmark = repository.insertBookmark(title = "name", url = "foo.com")
        repository.getFolderContent(bookmark.parentId).test {
            val result = awaitItem()
            Assert.assertTrue(result.first.size == 1)
            Assert.assertTrue(result.first.first().id == bookmark.id)
            Assert.assertTrue(result.second.isEmpty())
        }
    }

    @Test
    fun whenGetBookmarkFoldersByParentIdThenReturnBookmarkFoldersForParentId() = runTest {
        val folder = repository.insert(BookmarkFolder(id = "folder1", name = "name", parentId = "folder2"))
        val folderInserted = repository.getFolder(folder.id)

        Assert.assertEquals(folder, folderInserted)
    }

    @Test
    fun whenBookmarkFolderInsertedButWrongIdThenNothingIsRetrieved() = runTest {
        repository.insert(BookmarkFolder(id = "folder1", name = "name", parentId = "folder2"))
        val folderInserted = repository.getFolder("folder2")

        Assert.assertNull(folderInserted)
    }

    @Test
    fun whenBookmarkAddedToFolderThenGetFolderReturnsFolder() = runTest {
        givenNoBookmarksStored()

        repository.insertBookmark(title = "root", url = "foo.com")
        val folder = repository.insert(BookmarkFolder(id = "folder2", name = "folder2", parentId = Relation.BOOMARKS_ROOT))

        val bookmarkOne = repository.insertBookmark(title = "one", url = "fooone.com")
        repository.update(bookmarkOne.copy(parentId = folder.id))

        val bookmarkTwo = repository.insertBookmark(title = "two", url = "footwo.com")
        repository.update(bookmarkTwo.copy(parentId = folder.id))

        repository.getFolderContent(folder.id).test {
            val result = awaitItem()
            Assert.assertTrue(result.first.size == 2)
            Assert.assertTrue(result.second.isEmpty())
        }
    }

    @Test
    fun whenBookmarkAndFolderAddedToFolderThenGetFolderReturnsFolder() = runTest {
        givenNoBookmarksStored()

        repository.insertBookmark(title = "root", url = "foo.com")
        val folder = repository.insert(BookmarkFolder(id = "folder2", name = "folder2", parentId = Relation.BOOMARKS_ROOT))

        val bookmarkOne = repository.insertBookmark(title = "one", url = "fooone.com")
        repository.update(bookmarkOne.copy(parentId = folder.id))

        val bookmarkTwo = repository.insertBookmark(title = "two", url = "footwo.com")
        repository.update(bookmarkTwo.copy(parentId = folder.id))

        repository.insert(BookmarkFolder(id = "folder3", name = "folder2", parentId = "folder2"))

        repository.getFolderContent(folder.id).test {
            val result = awaitItem()
            Assert.assertTrue(result.first.size == 2)
            Assert.assertTrue(result.second.size == 1)
            Assert.assertEquals(result.second.first().id, "folder3")
        }
    }

    @Test
    fun whenBookmarkIsMovedToAnotherFolderThenRootBookmarksReturnsEmpty() = runTest {
        givenNoBookmarksStored()

        val folderOne = repository.insert(BookmarkFolder(id = "folder1", name = "folder1", parentId = Relation.BOOMARKS_ROOT))
        val bookmark = repository.insertBookmark(title = "bookmark1", url = "foo.com")
        val folderTwo = repository.insert(BookmarkFolder(id = "folder2", name = "folder2", parentId = Relation.BOOMARKS_ROOT))

        repository.getFolderContent(Relation.BOOMARKS_ROOT).test {
            val result = awaitItem()
            assertEquals(listOf(bookmark), result.first)

            val updatedBookmark = bookmark.copy(parentId = folderTwo.id)
            repository.update(updatedBookmark)
            val updatedResult = awaitItem()

            assertTrue(updatedResult.first.isEmpty())
        }
    }

    @Test
    fun whenBookmarkIsMovedToAnotherFolderThenFolderReturnsBookmark() = runTest {
        givenNoBookmarksStored()

        val folderOne = repository.insert(BookmarkFolder(id = "folder1", name = "folder1", parentId = Relation.BOOMARKS_ROOT))
        val bookmark = repository.insertBookmark(title = "bookmark1", url = "foo.com")
        val folderTwo = repository.insert(BookmarkFolder(id = "folder2", name = "folder2", parentId = Relation.BOOMARKS_ROOT))

        repository.getFolderContent(folderTwo.id).test {
            val result = awaitItem()
            assertTrue(result.first.isEmpty())

            val updatedBookmark = bookmark.copy(parentId = folderTwo.id)
            repository.update(updatedBookmark)
            val updatedResult = awaitItem()

            assertEquals(listOf(updatedBookmark), updatedResult.first)
        }
    }

    @Test
    fun whenFolderIsMovedToAnotherFolderThenParentFolderIsUpdated() = runTest {
        givenNoBookmarksStored()

        val folderOne = repository.insert(BookmarkFolder(id = "folder1", name = "folder1", parentId = Relation.BOOMARKS_ROOT))
        val bookmarkOne = repository.insertBookmark(title = "bookmark1", url = "foo1.com")
        val updatedBookmarkOne = bookmarkOne.copy(parentId = folderOne.id)
        repository.update(updatedBookmarkOne)

        val updatedFolderOne = folderOne.copy(numBookmarks = 1)
        assertEquals(repository.getFolder(folderOne.id), updatedFolderOne)

        val folderTwo = repository.insert(BookmarkFolder(id = "folder2", name = "folder2", parentId = Relation.BOOMARKS_ROOT))
        val bookmarkTwo = repository.insertBookmark(title = "bookmark2", url = "foo2.com")
        val updatedBookmarkTwo = bookmarkTwo.copy(parentId = folderTwo.id)
        repository.update(updatedBookmarkTwo)

        val updatedFolderTwo = folderTwo.copy(numBookmarks = 1)
        assertEquals(repository.getFolder(folderTwo.id), updatedFolderTwo)

        val bookmarkThree = repository.insertBookmark(title = "bookmark3", url = "foo3.com")

        repository.getFolderContent(Relation.BOOMARKS_ROOT).test {
            val result = awaitItem()
            assertEquals(listOf(bookmarkThree), result.first)
            assertEquals(listOf(updatedFolderOne, updatedFolderTwo), result.second)

            repository.update(folderTwo.copy(parentId = folderOne.id))

            val updatedFolderOne = folderOne.copy(numBookmarks = 1, numFolders = 1)
            val updatedResult = awaitItem()

            assertEquals(listOf(bookmarkThree), updatedResult.first)
            assertEquals(listOf(updatedFolderOne), updatedResult.second)
        }
    }

    @Test
    fun whenFolderNameUpdatedThenRepositoryReturnsUpdatedFolder() = runTest {
        val folder = repository.insert(BookmarkFolder(id = "folder", name = "folder", parentId = Relation.BOOMARKS_ROOT))
        assertEquals(repository.getFolder(folder.id), folder)

        val folderUpdated = folder.copy(name = "folder updated")
        repository.update(folderUpdated)
        assertEquals(repository.getFolder(folderUpdated.id), folderUpdated)
    }

    @Test
    fun whenFolderParentUpdatedThenRepositoryReturnsUpdatedFolder() = runTest {
        val folderRoot = repository.insert(BookmarkFolder(id = Relation.BOOMARKS_ROOT, name = "folder", parentId = ""))
        val folderTwo = repository.insert(BookmarkFolder(id = "folder2", name = "folder two", parentId = folderRoot.id))
        val folder = repository.insert(BookmarkFolder(id = "folder", name = "folder", parentId = folderRoot.id))

        assertEquals(repository.getFolder(folder.id), folder)

        val folderUpdated = folder.copy(parentId = folderTwo.id)
        repository.update(folderUpdated)

        assertEquals(repository.getFolder(folder.id), folderUpdated)
    }

    @Test
    fun whenInsertBranchFolderThenAllEntitiesAreInsertedCorrectly() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        repository.insertFolderBranch(folderBranch)

        assertEquals(repository.getFolder(parentFolder.id), parentFolder)
        assertEquals(repository.getFolder(childFolder.id), childFolder)
        assertEquals(repository.getBookmark(childBookmark.url), childBookmark)
    }

    @Test
    fun whenChildFolderWithBookmarkThenGetFolderBranchReturnsFolderBranch() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        repository.insertFolderBranch(folderBranch)

        val branch = repository.getFolderBranch(parentFolder)

        assertEquals(listOf(childBookmark), branch.bookmarks)
        assertEquals(listOf(parentFolder, childFolder), branch.folders)
    }

    @Test
    fun whenChildFoldersWithBookmarksThenGetFolderBranchReturnsFolderBranch() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", Relation.BOOMARKS_ROOT)
        val parentBookmark = Bookmark("bookmark1", "title1", "www.example1.com", "folder1")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1")
        val childBookmark = Bookmark("bookmark2", "title2", "www.example2.com", "folder2")
        val childSecondFolder = BookmarkFolder("folder3", "Parent Folder", "folder2")
        val childThirdBookmark = Bookmark("bookmark3", "title3", "www.example3.com", "folder3")
        val childFourthBookmark = Bookmark("bookmark4", "title4", "www.example4.com", "folder3")
        val childThirdFolder = BookmarkFolder("folder4", "Parent Folder", "folder3")
        val childFourthFolder = BookmarkFolder("folder5", "Parent Folder", "folder3")
        val folderBranch = FolderBranch(
            listOf(parentBookmark, childBookmark, childThirdBookmark, childFourthBookmark),
            listOf(parentFolder, childFolder, childSecondFolder, childThirdFolder, childFourthFolder),
        )

        repository.insertFolderBranch(folderBranch)

        val branch = repository.getFolderBranch(parentFolder)

        assertEquals(listOf(parentBookmark, childBookmark, childThirdBookmark, childFourthBookmark), branch.bookmarks)
        assertEquals(listOf(parentFolder, childFolder, childSecondFolder, childThirdFolder, childFourthFolder), branch.folders)
    }

    @Test
    fun whenChildFoldersWithBookmarksInRootThenGetFolderBranchReturnsFolderBranch() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", Relation.BOOMARKS_ROOT)
        val parentBookmark = Bookmark("bookmark1", "title1", "www.example1.com", Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1")
        val childBookmark = Bookmark("bookmark2", "title2", "www.example2.com", "folder2")
        val childSecondFolder = BookmarkFolder("folder3", "Parent Folder", "folder2")
        val childThirdBookmark = Bookmark("bookmark3", "title3", "www.example3.com", "folder3")
        val childFourthBookmark = Bookmark("bookmark4", "title4", "www.example4.com", "folder3")
        val childThirdFolder = BookmarkFolder("folder4", "Parent Folder", "folder3")
        val childFourthFolder = BookmarkFolder("folder5", "Parent Folder", "folder3")
        val folderBranch = FolderBranch(
            listOf(parentBookmark, childBookmark, childThirdBookmark, childFourthBookmark),
            listOf(parentFolder, childFolder, childSecondFolder, childThirdFolder, childFourthFolder),
        )

        repository.insertFolderBranch(folderBranch)

        val branch = repository.getFolderBranch(parentFolder)

        assertEquals(listOf(childBookmark, childThirdBookmark, childFourthBookmark), branch.bookmarks)
        assertEquals(listOf(parentFolder, childFolder, childSecondFolder, childThirdFolder, childFourthFolder), branch.folders)
    }

    @Test
    fun whenBranchFolderDeletedThenNothingIsRetrieved() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        repository.insertFolderBranch(folderBranch)

        assertEquals(repository.getFolder(parentFolder.id), parentFolder)
        assertEquals(repository.getFolder(childFolder.id), childFolder)
        assertEquals(repository.getBookmark(childBookmark.url), childBookmark)

        repository.deleteFolderBranch(parentFolder)

        assertNull(repository.getFolder(parentFolder.id))
        assertNull(repository.getFolder(childFolder.id))
        assertNull(repository.getBookmark(childBookmark.url))
    }

    @Test
    fun whenBuildFlatStructureThenReturnFolderListWithDepth() = runTest {
        val rootFolder = BookmarkFolder(id = Relation.BOOMARKS_ROOT, name = "root", parentId = "")
        val parentFolder = BookmarkFolder(id = "folder1", name = "name", parentId = Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder(id = "folder2", name = "another name", parentId = "folder1")
        val folder = BookmarkFolder(id = "folder3", name = "folder name", parentId = Relation.BOOMARKS_ROOT)

        repository.insert(rootFolder)
        repository.insert(parentFolder)
        repository.insert(childFolder)
        repository.insert(folder)

        val flatStructure = repository.getFolderTree(folder.id, null)

        val items = listOf(
            BookmarkFolderItem(0, rootFolder.copy(numFolders = 2), false),
            BookmarkFolderItem(1, parentFolder, false),
            BookmarkFolderItem(2, childFolder, false),
            BookmarkFolderItem(1, folder, true),
        )

        assertEquals(items, flatStructure)
    }

    @Test
    fun whenBuildFlatStructureThenReturnFolderListWithDepthWithoutCurrentFolderBranch() = runTest {
        val rootFolder = BookmarkFolder(id = Relation.BOOMARKS_ROOT, name = "root", parentId = "")
        val parentFolder = BookmarkFolder(id = "folder1", name = "name", parentId = Relation.BOOMARKS_ROOT)
        val childFolder = BookmarkFolder(id = "folder2", name = "another name", parentId = parentFolder.id)
        val folder = BookmarkFolder(id = "folder3", name = "folder name", parentId = Relation.BOOMARKS_ROOT)

        repository.insert(rootFolder)
        repository.insert(parentFolder)
        repository.insert(childFolder)
        repository.insert(folder)

        val flatStructure = repository.getFolderTree(folder.id, parentFolder)

        val items = listOf(
            BookmarkFolderItem(0, rootFolder.copy(numFolders = 2), false),
            BookmarkFolderItem(1, folder, true),
        )

        assertEquals(items, flatStructure)
    }

    @Test
    fun whenFolderIsDeletedThenRemovedFromDb() {
        val rootFolder = BookmarkFolder(id = Relation.BOOMARKS_ROOT, name = "root", parentId = "")
        repository.insert(rootFolder)

        assertEquals(repository.getFolder(rootFolder.id), rootFolder)

        repository.delete(rootFolder)
        assertNull(repository.getFolder(rootFolder.id))
    }

    @Test
    fun whenRootFolderHasBookmarksAndFoldersThenDataWithNumbersIsRetrieved() = runTest {
        val totalBookmarks = 10
        val totalFolders = 3

        val rootFolder = BookmarkFolder(id = Relation.BOOMARKS_ROOT, name = "root", parentId = "")
        repository.insert(rootFolder)

        val parentFolder = BookmarkFolder(id = "folder1", name = "name", parentId = Relation.BOOMARKS_ROOT)
        repository.insert(parentFolder)

        givenFolderWithEntities(parentFolder.id, totalBookmarks, totalFolders)

        repository.getFolderContent(parentFolder.id).test {
            val result = awaitItem()

            val parentFolder = result.second.first()
            assert(parentFolder.numBookmarks == 10)
            assert(parentFolder.numFolders == 3)

            cancelAndConsumeRemainingEvents()
        }
    }

    @After
    fun after() {
        db.close()
    }

    private fun givenNoFavoritesStored() {
        Assert.assertFalse(repository.hasFavorites())
    }

    private fun givenFavoriteStored(vararg favorite: Favorite) {
        favorite.forEach {
            val entity = Entity(it.id, it.title, it.url, type = BOOKMARK)
            syncEntitiesDao.insert(entity)
            syncRelationsDao.insert(Relation(relationId = Relation.FAVORITES_ROOT, entityId = entity.entityId))
        }
    }

    private fun givenSomeFavoritesStored() {
        val entity1 = Entity(title = "title", url = "http://example.com", type = BOOKMARK)
        val entity2 = Entity(title = "title2", url = "http://examples.com", type = BOOKMARK)

        syncEntitiesDao.insert(entity1)
        syncRelationsDao.insert(Relation(relationId = Relation.FAVORITES_ROOT, entityId = entity1.entityId))

        syncEntitiesDao.insert(entity2)
        syncRelationsDao.insert(Relation(relationId = Relation.FAVORITES_ROOT, entityId = entity2.entityId))
    }

    private fun givenNoBookmarksStored() {
        Assert.assertFalse(repository.hasBookmarks())
    }

    private fun givenSomeBookmarks(
        total: Int,
    ): List<Entity> {
        val entities = mutableListOf<Entity>()
        for (index in 1..total) {
            entities.add(Entity(Entity.generateBookmarkId(index.toLong()), "entity$index", "https://testUrl$index", BOOKMARK))
        }
        return entities
    }

    private fun givenSomeFolders(
        total: Int,
    ): List<Entity> {
        val entities = mutableListOf<Entity>()
        for (index in 1..total) {
            entities.add(Entity(Entity.generateFolderId(index.toLong()), "entity$index", "https://testUrl$index", FOLDER))
        }
        return entities
    }

    private fun givenFolderWithEntities(folderId: String, bookmarks: Int, folders: Int) {
        val bookmarks = givenSomeBookmarks(bookmarks)
        val folders = givenSomeFolders(folders)
        val folderContent = givenFolderWithContent(folderId, bookmarks.plus(folders))
        syncEntitiesDao.insertList(bookmarks)
        syncEntitiesDao.insertList(folders)
        syncRelationsDao.insertList(folderContent)
    }

    private fun givenFolderWithContent(
        folderId: String,
        entities: List<Entity>,
    ): List<Relation> {
        val relations = mutableListOf<Relation>()
        entities.forEach {
            relations.add(Relation(relationId = folderId, entityId = it.entityId))
        }
        return relations
    }

    private fun givenEmptyDBState() {
        syncRelationsDao.insertList(givenFolderWithContent(Relation.BOOMARKS_ROOT, emptyList()))
        syncRelationsDao.insertList(givenFolderWithContent(Relation.FAVORITES_ROOT, emptyList()))
    }
}
