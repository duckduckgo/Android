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
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import junit.framework.TestCase.assertEquals
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
    fun whenFavoriteUpdatedThenDatabaseChanged() {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        givenFavoriteStored(favorite)
        val updatedFavorite = favorite.copy(position = 3)

        repository.update(updatedFavorite)

        assertFavoriteExistsInDb(updatedFavorite)
    }

    @Test
    fun whenListReceivedThenUpdateItemsWithNewPositionInDatabase() {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", 2)

        givenFavoriteStored(favorite, favorite2)

        repository.updateWithPosition(listOf(favorite2, favorite))

        assertFavoriteExistsInDb(favorite2.copy(position = 1))
        assertFavoriteExistsInDb(favorite.copy(position = 2))
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

        val favorite = repository.insert(Favorite(id = "favorite1", title = "title", url = "www.website.com", position = 1))
        val otherFavorite = repository.insert(Favorite(id = "favorite2", title = "other title", url = "www.other-website.com", position = 2))

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

    private fun givenBookmarkStored(vararg bookmark: Bookmark) {
        bookmark.forEach {
            val entity = Entity(it.id, it.title, it.url, type = BOOKMARK)
            syncEntitiesDao.insert(entity)
            syncRelationsDao.insert(Relation(relationId = Relation.BOOMARKS_ROOT, entityId = entity.entityId))
        }
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

    private fun assertFavoriteExistsInDb(favorite: Favorite) {
        val storedFavorite = syncEntitiesDao.favorite(url = favorite.url) ?: error("Favorite not found in database")
        Assert.assertEquals(storedFavorite.title, favorite.title)
        Assert.assertEquals(storedFavorite.url, favorite.url)
    }

    private fun givenEmptyDBState() {
        syncRelationsDao.insertList(givenFolderWithContent(Relation.BOOMARKS_ROOT, emptyList()))
        syncRelationsDao.insertList(givenFolderWithContent(Relation.FAVORITES_ROOT, emptyList()))
    }
}
