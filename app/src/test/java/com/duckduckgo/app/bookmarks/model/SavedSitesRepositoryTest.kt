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
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.givenFolderWithContent
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.givenSomeBookmarks
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.givenSomeFolders
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.BookmarkFolderItem
import com.duckduckgo.savedsites.api.models.FolderBranch
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
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
import org.threeten.bp.LocalDateTime

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SavedSitesRepositoryTest {

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenNoDataThenFolderContentisEmpty() = runTest {
        repository.getFolderContent(SavedSitesNames.BOOKMARKS_ROOT).test {
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
        savedSitesEntitiesDao.insertList(entities)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, entities)
        savedSitesRelationsDao.insertList(relation)

        repository.getFolderContent(SavedSitesNames.BOOKMARKS_ROOT).test {
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
        savedSitesEntitiesDao.insertList(entities)

        val folders = givenSomeFolders(totalFolders)
        savedSitesEntitiesDao.insertList(folders)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, entities.plus(folders))
        savedSitesRelationsDao.insertList(relation)

        repository.getFolderContent(SavedSitesNames.BOOKMARKS_ROOT).test {
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
        savedSitesEntitiesDao.insertList(entities)

        val folders = givenSomeFolders(totalFolders)
        savedSitesEntitiesDao.insertList(folders)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, entities.plus(folders))
        savedSitesRelationsDao.insertList(relation)

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

        repository.insertFavorite("favourite1", "https://favorite.com", "favorite")

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
    fun whenFavoriteIsAddedAndThenRemovedThenBookmarkStillExists() {
        givenEmptyDBState()

        val favorite = repository.insertFavorite("favourite1", "https://favorite.com", "favorite")

        assert(repository.getFavorite("https://favorite.com") != null)

        repository.delete(favorite)

        assert(repository.getFavorite("https://favorite.com") == null)
        assert(repository.getBookmark("https://favorite.com") != null)
    }

    @Test
    fun whenBookmarkIsAddedAndThenRemovedThenNothingIsRetrieved() {
        givenEmptyDBState()

        val bookmark = repository.insertBookmark("https://favorite.com", "favorite")

        assert(repository.getBookmark("https://favorite.com") != null)

        repository.delete(bookmark)
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
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 2)

        givenFavoriteStored(favoriteone)
        givenFavoriteStored(favoritetwo)
        givenFavoriteStored(favoritethree)

        val updatedFavorite = favoriteone.copy(title = "favorite updated")

        repository.updateFavourite(updatedFavorite)

        val storedUpdatedFavourite = repository.getFavorite(favoriteone.url)!!
        assertEquals(storedUpdatedFavourite.id, updatedFavorite.id)
        assertEquals(storedUpdatedFavourite.title, updatedFavorite.title)
        assertEquals(storedUpdatedFavourite.url, updatedFavorite.url)
    }

    @Test
    fun whenBookmarkFavoriteMovedToAnotherFolderThenBookmarkIsStillFavorite() {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favoriteone)

        val folder =
            repository.insert(BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))

        val bookmark = repository.getBookmark(favoriteone.url)
        assertNotNull(bookmark)

        val updatedBookmark = bookmark!!.copy(parentId = folder.id)
        repository.updateBookmark(updatedBookmark, SavedSitesNames.BOOKMARKS_ROOT)

        assertNotNull(repository.getFavorite(favoriteone.url))
    }

    @Test
    fun whenFavoritePositionUpdatedThenDataIsUpdated() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 2)

        givenFavoriteStored(favoriteone)
        givenFavoriteStored(favoritetwo)
        givenFavoriteStored(favoritethree)

        val updatedFavoriteOne = favoriteone.copy(position = 2)
        val updatedFavoriteTwo = favoriteone.copy(position = 0)
        val updatedFavoriteThree = favoriteone.copy(position = 1)

        repository.updateFavourite(updatedFavoriteOne)

        repository.getFavorites().test {
            val favorites = awaitItem()
            assertEquals(favorites.map { it.id }, listOf(updatedFavoriteTwo, updatedFavoriteThree, updatedFavoriteOne).map { it.id })
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenListReceivedThenUpdateItemsWithNewPositionInDatabase() {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", "timestamp", 1)

        givenFavoriteStored(favorite, favorite2)

        repository.updateWithPosition(listOf(favorite2, favorite))

        assertEquals(repository.getFavorite(favorite.url), favorite.copy(position = 1))
        assertEquals(repository.getFavorite(favorite2.url), favorite2.copy(position = 0))
    }

    @Test
    fun whenFavoriteDeletedThenDatabaseUpdated() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 1)
        givenFavoriteStored(favorite)

        repository.delete(favorite)

        Assert.assertNull(repository.getFavorite(favorite.url))
    }

    @Test
    fun whenUserHasFavoritesThenReturnTrue() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 1)

        givenFavoriteStored(favorite)

        Assert.assertTrue(repository.hasFavorites())
    }

    @Test
    fun whenFavoriteByUrlRequestedAndAvailableThenReturnFavorite() = runTest {
        givenNoFavoritesStored()

        val favorite =
            repository.insert(Favorite(id = "favorite1", title = "title", url = "www.website.com", lastModified = "timestamp", position = 0))
        val otherFavorite = repository.insert(
            Favorite(
                id = "favorite2",
                title = "other title",
                url = "www.other-website.com",
                lastModified = "timestamp",
                position = 1,
            ),
        )

        val result = repository.getFavorite("www.website.com")

        Assert.assertEquals(favorite, result)
    }

    @Test
    fun whenFavoriteByUrlRequestedAndNotAvailableThenReturnNull() = runTest {
        givenNoFavoritesStored()

        repository.insert(Favorite(id = "favorite1", title = "title", url = "www.website.com", lastModified = "timestamp", position = 1))
        repository.insert(Favorite(id = "favorite2", title = "other title", url = "www.other-website.com", lastModified = "timestamp", position = 2))

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
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 1)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", "timestamp", 2)

        givenFavoriteStored(favorite)
        givenFavoriteStored(favorite2)

        repository.deleteAll()

        givenNoFavoritesStored()
    }

    @Test
    fun whenInsertBookmarkThenPopulateDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            ),
        )

        Assert.assertEquals(listOf(bookmark), repository.getBookmarks().first())
    }

    @Test
    fun whenInsertBookmarkByTitleAndUrlThenPopulateDB() = runTest {
        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            ),
        )
        val bookmarkInserted = repository.getBookmark(bookmark.url)

        Assert.assertEquals(bookmark, bookmarkInserted)
    }

    @Test
    fun whenUpdateBookmarkUrlThenUpdateBookmarkInDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            ),
        )
        val updatedBookmark =
            Bookmark(
                id = bookmark.id,
                title = "new title",
                url = "example.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            )

        repository.updateBookmark(updatedBookmark, "folder1")
        val bookmarkUpdated = repository.getBookmark(updatedBookmark.url)!!

        Assert.assertEquals(updatedBookmark.id, bookmarkUpdated.id)
    }

    @Test
    fun whenUpdateBookmarkFolderThenUpdateBookmarkInDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            ),
        )
        val updatedBookmark = Bookmark(id = bookmark.id, title = "title", url = "foo.com", lastModified = "timestamp", parentId = "folder2")

        repository.updateBookmark(updatedBookmark, "folder1")
        val bookmarkUpdated = repository.getBookmark(bookmark.url)!!

        Assert.assertEquals(updatedBookmark.id, bookmarkUpdated.id)
    }

    @Test
    fun whenDeleteBookmarkThenRemoveBookmarkFromDB() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            ),
        )
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
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenGetBookmarkFoldersByParentIdThenReturnBookmarkFoldersForParentId() = runTest {
        val folder = repository.insert(BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = "folder2"))
        val folderInserted = repository.getFolder(folder.id)

        Assert.assertEquals(folder, folderInserted)
    }

    @Test
    fun whenBookmarkFolderInsertedButWrongIdThenNothingIsRetrieved() = runTest {
        repository.insert(BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = "folder2"))
        val folderInserted = repository.getFolder("folder2")

        Assert.assertNull(folderInserted)
    }

    @Test
    fun whenGetBookmarkFoldersByNameThenReturnBookmarkFolder() = runTest {
        val folder = repository.insert(BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = "folder2"))
        val folderInserted = repository.getFolderByName(folder.name)

        Assert.assertEquals(folder, folderInserted)
    }

    @Test
    fun whenBookmarkAddedToFolderThenGetFolderReturnsFolder() = runTest {
        givenNoBookmarksStored()

        repository.insertBookmark(title = "root", url = "foo.com")
        val folder =
            repository.insert(BookmarkFolder(id = "folder2", name = "folder2", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))

        val bookmarkOne = repository.insertBookmark(title = "one", url = "fooone.com")
        repository.updateBookmark(bookmarkOne.copy(parentId = folder.id), bookmarkOne.parentId)

        val bookmarkTwo = repository.insertBookmark(title = "two", url = "footwo.com")
        repository.updateBookmark(bookmarkTwo.copy(parentId = folder.id), bookmarkTwo.parentId)

        repository.getFolderContent(folder.id).test {
            val result = awaitItem()
            Assert.assertTrue(result.first.size == 2)
            Assert.assertTrue(result.second.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBookmarkAndFolderAddedToFolderThenGetFolderReturnsFolder() = runTest {
        givenNoBookmarksStored()

        repository.insertBookmark(title = "root", url = "foo.com")
        val folder =
            repository.insert(BookmarkFolder(id = "folder2", name = "folder2", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))

        val bookmarkOne = repository.insertBookmark(title = "one", url = "fooone.com")
        repository.updateBookmark(bookmarkOne.copy(parentId = folder.id), bookmarkOne.parentId)

        val bookmarkTwo = repository.insertBookmark(title = "two", url = "footwo.com")
        repository.updateBookmark(bookmarkTwo.copy(parentId = folder.id), bookmarkTwo.parentId)

        repository.insert(BookmarkFolder(id = "folder3", name = "folder2", lastModified = "timestamp", parentId = "folder2"))

        repository.getFolderContent(folder.id).test {
            val result = awaitItem()
            Assert.assertTrue(result.first.size == 2)
            Assert.assertTrue(result.second.size == 1)
            Assert.assertEquals(result.second.first().id, "folder3")
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBookmarkIsMovedToAnotherFolderThenRootBookmarksReturnsEmpty() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insertBookmark(title = "bookmark1", url = "foo.com")
        val folderTwo =
            repository.insert(BookmarkFolder(id = "folder2", name = "folder2", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))

        repository.getFolderContent(SavedSitesNames.BOOKMARKS_ROOT).test {
            val result = awaitItem()
            assertEquals(listOf(bookmark), result.first)

            val updatedBookmark = bookmark.copy(parentId = folderTwo.id)
            repository.updateBookmark(updatedBookmark, bookmark.parentId)

            val updatedResult = awaitItem()
            assertTrue(updatedResult.first.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBookmarkIsMovedToAnotherFolderThenFolderReturnsBookmark() = runTest {
        givenNoBookmarksStored()

        val bookmark = repository.insertBookmark(title = "bookmark1", url = "foo.com")
        val root =
            repository.insert(BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "Bookmarks", lastModified = "timestamp", parentId = ""))
        val folderTwo =
            repository.insert(BookmarkFolder(id = "folder2", name = "folder2", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))

        assertTrue(repository.getFolder(SavedSitesNames.BOOKMARKS_ROOT)!!.numBookmarks == 1)

        val updatedBookmark = bookmark.copy(parentId = folderTwo.id)
        repository.updateBookmark(updatedBookmark, bookmark.parentId)

        assertTrue(repository.getFolder(SavedSitesNames.BOOKMARKS_ROOT)!!.numBookmarks == 0)
        assertTrue(repository.getFolder(folderTwo.id)!!.numBookmarks == 1)
    }

    @Test
    fun whenFolderIsMovedToAnotherFolderThenParentFolderIsUpdated() = runTest {
        givenNoBookmarksStored()

        repository.insert(BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "Bookmarks", lastModified = "timestamp", parentId = ""))
        val folderOne =
            repository.insert(BookmarkFolder(id = "folder1", name = "folder1", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))
        val bookmarkOne = repository.insertBookmark(title = "bookmark1", url = "foo1.com")
        val updatedBookmarkOne = bookmarkOne.copy(parentId = folderOne.id)
        repository.updateBookmark(updatedBookmarkOne, SavedSitesNames.BOOKMARKS_ROOT)

        val updatedFolderOne = folderOne.copy(numBookmarks = 1)
        val storedUpdatedFolderOne = repository.getFolder(folderOne.id)!!
        assertEquals(storedUpdatedFolderOne.id, updatedFolderOne.id)
        assertEquals(storedUpdatedFolderOne.name, updatedFolderOne.name)

        val folderTwo =
            repository.insert(BookmarkFolder(id = "folder2", name = "folder2", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))
        val bookmarkTwo = repository.insertBookmark(title = "bookmark2", url = "foo2.com")
        val updatedBookmarkTwo = bookmarkTwo.copy(parentId = folderTwo.id)
        repository.updateBookmark(updatedBookmarkTwo, bookmarkTwo.parentId)

        val updatedFolderTwo = folderTwo.copy(numBookmarks = 1)
        val storedUpdatedFolderTwo = repository.getFolder(folderTwo.id)!!
        assertEquals(storedUpdatedFolderTwo.id, updatedFolderTwo.id)
        assertEquals(storedUpdatedFolderTwo.name, updatedFolderTwo.name)

        repository.insertBookmark(title = "bookmark3", url = "foo3.com")

        assertTrue(repository.getFolder(SavedSitesNames.BOOKMARKS_ROOT)!!.numBookmarks == 1)
        assertTrue(repository.getFolder(SavedSitesNames.BOOKMARKS_ROOT)!!.numFolders == 2)

        repository.update(folderTwo.copy(parentId = folderOne.id))

        assertTrue(repository.getFolder(SavedSitesNames.BOOKMARKS_ROOT)!!.numBookmarks == 1)
        assertTrue(repository.getFolder(SavedSitesNames.BOOKMARKS_ROOT)!!.numFolders == 1)
    }

    @Test
    fun whenFolderNameUpdatedThenRepositoryReturnsUpdatedFolder() = runTest {
        val folder =
            repository.insert(BookmarkFolder(id = "folder", name = "folder", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT))
        assertEquals(repository.getFolder(folder.id), folder)

        val folderUpdated = folder.copy(name = "folder updated")
        repository.update(folderUpdated)
        assertEquals(repository.getFolder(folderUpdated.id), folderUpdated)
    }

    @Test
    fun whenFolderParentUpdatedThenRepositoryReturnsUpdatedFolder() = runTest {
        val folderRoot =
            repository.insert(BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "folder", lastModified = "timestamp", parentId = ""))
        val folderTwo = repository.insert(BookmarkFolder(id = "folder2", name = "folder two", lastModified = "timestamp", parentId = folderRoot.id))
        val folder = repository.insert(BookmarkFolder(id = "folder", name = "folder", lastModified = "timestamp", parentId = folderRoot.id))

        assertEquals(repository.getFolder(folder.id), folder)

        val folderUpdated = folder.copy(parentId = folderTwo.id)
        repository.update(folderUpdated)

        assertEquals(repository.getFolder(folder.id), folderUpdated)
    }

    @Test
    fun whenInsertBranchFolderThenAllEntitiesAreInsertedCorrectly() = runTest {
        val parentFolder =
            BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, numFolders = 1, numBookmarks = 1, lastModified = "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", numFolders = 0, numBookmarks = 0, lastModified = "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder1", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        repository.insertFolderBranch(folderBranch)

        assertEquals(repository.getFolder(parentFolder.id), parentFolder)
        assertEquals(repository.getFolder(childFolder.id), childFolder)
        assertEquals(repository.getBookmark(childBookmark.url), childBookmark)
    }

    @Test
    fun whenChildFolderWithBookmarkThenGetFolderBranchReturnsFolderBranch() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
        val folderBranch = FolderBranch(listOf(childBookmark), listOf(parentFolder, childFolder))

        repository.insertFolderBranch(folderBranch)

        val branch = repository.getFolderBranch(parentFolder)

        assertEquals(listOf(childBookmark), branch.bookmarks)
        assertEquals(listOf(parentFolder, childFolder), branch.folders)
    }

    @Test
    fun whenChildFoldersWithBookmarksThenGetFolderBranchReturnsFolderBranch() = runTest {
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val parentBookmark = Bookmark("bookmark1", "title1", "www.example1.com", "folder1", "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark2", "title2", "www.example2.com", "folder2", "timestamp")
        val childSecondFolder = BookmarkFolder("folder3", "Parent Folder", "folder2", 0, 0, "timestamp")
        val childThirdBookmark = Bookmark("bookmark3", "title3", "www.example3.com", "folder3", "timestamp")
        val childFourthBookmark = Bookmark("bookmark4", "title4", "www.example4.com", "folder3", "timestamp")
        val childThirdFolder = BookmarkFolder("folder4", "Parent Folder", "folder3", 0, 0, "timestamp")
        val childFourthFolder = BookmarkFolder("folder5", "Parent Folder", "folder3", 0, 0, "timestamp")
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
        val parentFolder = BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, 0, 0, "timestamp")
        val parentBookmark = Bookmark("bookmark1", "title1", "www.example1.com", SavedSitesNames.BOOKMARKS_ROOT, "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", 0, 0, "timestamp")
        val childBookmark = Bookmark("bookmark2", "title2", "www.example2.com", "folder2", "timestamp")
        val childSecondFolder = BookmarkFolder("folder3", "Parent Folder", "folder2", 0, 0, "timestamp")
        val childThirdBookmark = Bookmark("bookmark3", "title3", "www.example3.com", "folder3", "timestamp")
        val childFourthBookmark = Bookmark("bookmark4", "title4", "www.example4.com", "folder3", "timestamp")
        val childThirdFolder = BookmarkFolder("folder4", "Parent Folder", "folder3", 0, 0, "timestamp")
        val childFourthFolder = BookmarkFolder("folder5", "Parent Folder", "folder3", 0, 0, "timestamp")
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
        val parentFolder =
            BookmarkFolder("folder1", "Parent Folder", SavedSitesNames.BOOKMARKS_ROOT, numBookmarks = 0, numFolders = 1, lastModified = "timestamp")
        val childFolder = BookmarkFolder("folder2", "Parent Folder", "folder1", numBookmarks = 1, numFolders = 0, lastModified = "timestamp")
        val childBookmark = Bookmark("bookmark1", "title", "www.example.com", "folder2", "timestamp")
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
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        val parentFolder = BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val childFolder = BookmarkFolder(id = "folder2", name = "another name", lastModified = "timestamp", parentId = "folder1")
        val folder = BookmarkFolder(id = "folder3", name = "folder name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)

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
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        val parentFolder = BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        val childFolder = BookmarkFolder(id = "folder2", name = "another name", lastModified = "timestamp", parentId = parentFolder.id)
        val folder = BookmarkFolder(id = "folder3", name = "folder name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)

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
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        assertEquals(repository.getFolder(rootFolder.id), rootFolder)

        repository.delete(rootFolder)
        assertNull(repository.getFolder(rootFolder.id))
    }

    @Test
    fun whenRootFolderHasBookmarksAndFoldersThenDataWithNumbersIsRetrieved() = runTest {
        val totalBookmarks = 10
        val totalFolders = 3

        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val parentFolder = BookmarkFolder(name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        repository.insert(parentFolder)

        givenFolderWithEntities(parentFolder.id, totalBookmarks, totalFolders)

        repository.getFolderContent(parentFolder.id).test {
            val result = awaitItem()

            assert(result.first.size == 10)
            assert(result.second.size == 3)

            cancelAndConsumeRemainingEvents()
        }

        repository.getFolderContent(SavedSitesNames.BOOKMARKS_ROOT).test {
            val result = awaitItem()

            val folder = result.second.first()

            assert(folder.numBookmarks == 10)
            assert(folder.numFolders == 3)

            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserHasBookmarksAndFavoritesThenGetSavedSitesReturnsEverything() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", "timestamp", 1)
        givenFavoriteStored(favorite, favorite2)

        val bookmarks = givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, bookmarks)
        savedSitesRelationsDao.insertList(relation)

        repository.getSavedSites(SavedSitesNames.BOOKMARKS_ROOT).test {
            val savedSites = awaitItem()
            assertTrue(savedSites.bookmarks.size == 12)
            assertTrue(savedSites.favorites.size == 2)
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserHasBookmarksAndFavoritesThenGetSavedSitesFromASubFolderReturnsSubFolder() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", "timestamp", 1)
        givenFavoriteStored(favorite, favorite2)

        val folder = givenSomeFolders(1)
        val subFolderId = folder.first().entityId
        savedSitesEntitiesDao.insertList(folder)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, folder)
        savedSitesRelationsDao.insertList(relation)

        val bookmarks = givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val subFolder = givenFolderWithContent(subFolderId, bookmarks)
        savedSitesRelationsDao.insertList(subFolder)

        repository.getSavedSites(SavedSitesNames.BOOKMARKS_ROOT).test {
            val savedSites = awaitItem()
            assertTrue(savedSites.bookmarks.size == 2)
            assertTrue(savedSites.favorites.size == 2)
            assertTrue(savedSites.folders.size == 1)
            cancelAndConsumeRemainingEvents()
        }

        repository.getSavedSites(subFolderId).test {
            val savedSites = awaitItem()
            assertTrue(savedSites.bookmarks.size == 10)
            assertTrue(savedSites.favorites.isEmpty())
            assertTrue(savedSites.folders.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenUserHasBookmarksThenBookmarksTreeReturnsSavedSites() = runTest {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val parentFolder = BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        repository.insert(parentFolder)

        val totalBookmarks = 10
        val totalFolders = 3
        givenFolderWithEntities(parentFolder.id, totalBookmarks, totalFolders)

        val bookmarks = repository.getBookmarksTree()

        assertTrue(bookmarks.size == 10)
        assertTrue(bookmarks[0].parentId == parentFolder.id)
    }

    @Test
    fun whenDeleteBookmarkIsUndoneThenFolderStillHasBookmark() = runTest {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val parentFolder = BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        repository.insert(parentFolder)

        val bookmark = repository.insertBookmark("https://favorite.com", "favorite")

        assert(repository.getBookmark("https://favorite.com") != null)

        val updatedBookmark = bookmark.copy(parentId = parentFolder.id)
        repository.updateBookmark(updatedBookmark, bookmark.parentId)

        repository.delete(bookmark)
        assert(repository.getBookmark("https://favorite.com") == null)

        repository.insert(updatedBookmark)

        repository.getSavedSites(parentFolder.id).test {
            val savedSites = awaitItem()
            assertTrue(savedSites.bookmarks.size == 1)
            assertTrue(savedSites.favorites.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBookmarkIsUpdatedThenLastModifiedIsAlsoUpdated() {
        givenNoBookmarksStored()

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = SavedSitesNames.BOOKMARKS_ROOT,
            ),
        )
        val updatedBookmark = Bookmark(id = bookmark.id, title = "title", url = "foo.com", lastModified = "timestamp", parentId = "folder2")

        repository.updateBookmark(updatedBookmark, "folder1")
        val bookmarkUpdated = repository.getBookmark(bookmark.url)!!

        Assert.assertEquals(updatedBookmark.id, bookmarkUpdated.id)
    }

    @Test
    fun whenFavouritesDuplicatedThenLocalDataIsReplaced() = runTest {
        val favoriteone = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        val favoritetwo = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 1)
        val favoritethree = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 2)

        givenFavoriteStored(favoriteone)
        givenFavoriteStored(favoritetwo)
        givenFavoriteStored(favoritethree)
    }

    @Test
    fun whenSubFolderIsReplacedThenLocalDataIsUpdated() = runTest {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val parentFolder = BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        repository.insert(parentFolder)

        val bookmarks = givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val subFolder = givenFolderWithContent(parentFolder.id, bookmarks)
        savedSitesRelationsDao.insertList(subFolder)

        repository.replaceFolder("folder2", parentFolder.id)

        repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = "folder2",
            ),
        )

        repository.getSavedSites("folder2").test {
            val savedSites = awaitItem()
            assertTrue(savedSites.bookmarks.size == 11)
            assertTrue(savedSites.favorites.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenBookmarkIsReplacedWithDifferentIdThenDataIsUpdated() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = "folder2",
            ),
        ) as Bookmark

        val updatedBookmark = bookmark.copy(id = "bookmark2")

        repository.replaceBookmark(updatedBookmark, bookmark.id)

        val bookmarkUpdated = repository.getBookmark(bookmark.url)!!

        Assert.assertTrue(updatedBookmark.id == bookmarkUpdated.id)
        Assert.assertTrue(bookmark.url == bookmarkUpdated.url)
        Assert.assertTrue(bookmark.title == bookmarkUpdated.title)
    }

    @Test
    fun whenBookmarkIsReplacedWithSameIdThenDataIsUpdated() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val bookmark = repository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = "folder2",
            ),
        ) as Bookmark

        val updatedBookmark = bookmark.copy(title = "title2")

        repository.replaceBookmark(updatedBookmark, bookmark.id)

        val bookmarkUpdated = repository.getBookmark(bookmark.url)!!

        Assert.assertTrue(bookmark.id == bookmarkUpdated.id)
        Assert.assertTrue(bookmark.url == bookmarkUpdated.url)
        Assert.assertTrue("title2" == bookmarkUpdated.title)
    }

    @Test
    fun whenFavouriteIsReplacedWithSameIdThenDataIsUpdated() {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favorite)

        val favoriteUpdated = Favorite("favorite1", "Favorite New", "http://favexample.com", "timestamp", 0)
        repository.replaceFavourite(favoriteUpdated, favorite.id)

        val favoriteStored = repository.getFavoriteById(favorite.id)!!

        Assert.assertTrue(favoriteUpdated.id == favoriteStored.id)
        Assert.assertTrue(favoriteUpdated.url == favoriteStored.url)
        Assert.assertTrue(favoriteUpdated.title == favoriteStored.title)
    }

    @Test
    fun whenFavouriteIsReplacedWithDifferentIdThenDataIsUpdated() {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", "timestamp", 0)
        givenFavoriteStored(favorite)

        val favoriteUpdated = Favorite("favorite2", "Favorite New", "http://favexample.com", "timestamp", 0)
        repository.replaceFavourite(favoriteUpdated, favorite.id)

        val favoriteNulled = repository.getFavoriteById(favorite.id)
        Assert.assertTrue(favoriteNulled == null)

        val favoriteStored = repository.getFavoriteById(favoriteUpdated.id)!!
        Assert.assertTrue(favoriteUpdated.id == favoriteStored.id)
        Assert.assertTrue(favoriteUpdated.url == favoriteStored.url)
        Assert.assertTrue(favoriteUpdated.title == favoriteStored.title)
    }

    @Test
    fun whenBookmarkModifiedAfterThresholdThenGetModifiedSinceHasBookmarks() {
        val modifiedEarlierTime = LocalDateTime.now().minusHours(2)
        val since = LocalDateTime.now().minusHours(1)
        val modifiedEarlierBookmarks = givenSomeBookmarks(5, DatabaseDateFormatter.iso8601(modifiedEarlierTime))
        savedSitesEntitiesDao.insertList(modifiedEarlierBookmarks)

        val bookmarks = givenSomeBookmarks(3)
        savedSitesEntitiesDao.insertList(bookmarks)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, modifiedEarlierBookmarks.plus(bookmarks))
        savedSitesRelationsDao.insertList(relation)

        val modifiedSinceBookmarks = repository.getBookmarksModifiedSince(DatabaseDateFormatter.iso8601(since))
        assertEquals(bookmarks.size, modifiedSinceBookmarks.size)
    }

    @Test
    fun whenBookmarkModifiedBeforeThresholdThenGetModifiedSinceIsEmpty() {
        val modifiedEarlierTime = LocalDateTime.now().minusHours(2)
        val since = LocalDateTime.now().minusHours(1)
        val modifiedEarlierBookmarks = givenSomeBookmarks(5, DatabaseDateFormatter.iso8601(modifiedEarlierTime))
        savedSitesEntitiesDao.insertList(modifiedEarlierBookmarks)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, modifiedEarlierBookmarks)
        savedSitesRelationsDao.insertList(relation)

        val modifiedSinceBookmarks = repository.getBookmarksModifiedSince(DatabaseDateFormatter.iso8601(since))
        assertTrue(modifiedSinceBookmarks.isEmpty())
    }

    @Test
    fun whenFolderModifiedAfterThresholdThenGetModifiedSinceHasFolders() {
        val modifiedEarlierTime = LocalDateTime.now().minusHours(1)
        val since = LocalDateTime.now().minusHours(2)

        val rootFolder = BookmarkFolder(
            id = SavedSitesNames.BOOKMARKS_ROOT,
            name = "root",
            lastModified = DatabaseDateFormatter.iso8601(modifiedEarlierTime),
            parentId = "",
        )
        repository.insert(rootFolder)

        val modifiedEarlierBookmarks = givenSomeBookmarks(5, DatabaseDateFormatter.iso8601(modifiedEarlierTime))
        savedSitesEntitiesDao.insertList(modifiedEarlierBookmarks)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, modifiedEarlierBookmarks)
        savedSitesRelationsDao.insertList(relation)

        val modifiedSinceFolders = repository.getFoldersModifiedSince(DatabaseDateFormatter.iso8601(since))
        assertEquals(1, modifiedSinceFolders.size)
    }

    @Test
    fun whenFolderModifiedBeforeThresholdThenGetModifiedSinceIsEmpty() {
        val modifiedEarlierTime = LocalDateTime.now().minusHours(2)
        val since = LocalDateTime.now().minusHours(1)

        val rootFolder = BookmarkFolder(
            id = SavedSitesNames.BOOKMARKS_ROOT,
            name = "root",
            lastModified = DatabaseDateFormatter.iso8601(modifiedEarlierTime),
            parentId = "",
        )
        repository.insert(rootFolder)

        val modifiedEarlierBookmarks = givenSomeBookmarks(5, DatabaseDateFormatter.iso8601(modifiedEarlierTime))
        savedSitesEntitiesDao.insertList(modifiedEarlierBookmarks)

        val relation = givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, modifiedEarlierBookmarks)
        savedSitesRelationsDao.insertList(relation)

        val modifiedSinceFolders = repository.getFoldersModifiedSince(DatabaseDateFormatter.iso8601(since))
        assertTrue(modifiedSinceFolders.isEmpty())
    }

    @Test
    fun whenSubFolderContentIsReplacedThenLocalDataIsUpdated() = runTest {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        repository.insert(rootFolder)

        val parentFolder = BookmarkFolder(id = "folder1", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        repository.insert(parentFolder)

        val bookmarks = givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val subFolder = givenFolderWithContent(parentFolder.id, bookmarks)
        savedSitesRelationsDao.insertList(subFolder)

        val remoteFolder = BookmarkFolder(id = "folder2", name = "name", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
        repository.replaceFolderContent(remoteFolder, parentFolder.id)

        val storedFolder = repository.getFolder(remoteFolder.id)!!
        assertTrue(storedFolder.name == remoteFolder.name)
        assertTrue(storedFolder.parentId == remoteFolder.parentId)
        assertTrue(storedFolder.lastModified == remoteFolder.lastModified)

        repository.getSavedSites(remoteFolder.id).test {
            val savedSites = awaitItem()
            assertTrue(savedSites.bookmarks.size == 10)
            assertTrue(savedSites.favorites.isEmpty())
            cancelAndConsumeRemainingEvents()
        }
    }

    private fun givenNoFavoritesStored() {
        Assert.assertFalse(repository.hasFavorites())
    }

    private fun givenFavoriteStored(vararg favorite: Favorite) {
        favorite.forEach {
            val entity = Entity(it.id, it.title, it.url, type = BOOKMARK, lastModified = it.lastModified)
            savedSitesEntitiesDao.insert(entity)
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity.entityId))
            savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.BOOKMARKS_ROOT, entityId = entity.entityId))
        }
    }

    private fun givenSomeFavoritesStored() {
        val entity1 = Entity(title = "title", url = "http://example.com", type = BOOKMARK)
        val entity2 = Entity(title = "title2", url = "http://examples.com", type = BOOKMARK)

        savedSitesEntitiesDao.insert(entity1)
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity1.entityId))

        savedSitesEntitiesDao.insert(entity2)
        savedSitesRelationsDao.insert(Relation(folderId = SavedSitesNames.FAVORITES_ROOT, entityId = entity2.entityId))
    }

    private fun givenNoBookmarksStored() {
        Assert.assertFalse(repository.hasBookmarks())
    }

    private fun givenFolderWithEntities(
        folderId: String,
        bookmarks: Int,
        folders: Int,
    ) {
        val bookmarks = givenSomeBookmarks(bookmarks)
        val folders = givenSomeFolders(folders)
        val folderContent = givenFolderWithContent(folderId, bookmarks.plus(folders))
        savedSitesEntitiesDao.insertList(bookmarks)
        savedSitesEntitiesDao.insertList(folders)
        savedSitesRelationsDao.insertList(folderContent)
    }

    private fun givenEmptyDBState() {
        savedSitesRelationsDao.insertList(givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, emptyList()))
        savedSitesRelationsDao.insertList(givenFolderWithContent(SavedSitesNames.FAVORITES_ROOT, emptyList()))
    }
}
