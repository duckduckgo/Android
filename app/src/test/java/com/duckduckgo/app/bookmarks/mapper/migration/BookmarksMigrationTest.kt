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

package com.duckduckgo.app.bookmarks.mapper.migration

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.turbine.test
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFolderEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.bookmarks.migration.AppDatabaseBookmarksMigrationCallback
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import dagger.Lazy
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class BookmarksMigrationTest {

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private lateinit var appDatabase: AppDatabase

    private lateinit var syncEntitiesDao: SyncEntitiesDao
    private lateinit var syncRelationsDao: SyncRelationsDao

    private val mockFaviconManager: FaviconManager = mock()
    private val lazyFaviconManager = Lazy { mockFaviconManager }

    private lateinit var favoritesDao: FavoritesDao
    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var bookmarkFoldersDao: BookmarkFoldersDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        favoritesDao = appDatabase.favoritesDao()
        bookmarksDao = appDatabase.bookmarksDao()
        bookmarkFoldersDao = appDatabase.bookmarkFoldersDao()

        syncEntitiesDao = appDatabase.syncEntitiesDao()
        syncRelationsDao = appDatabase.syncRelationsDao()
    }

    @Test
    fun whenFavoritesExistThenMigrationIsSuccessful() = runTest {
        val totalFavorites = 10
        givenSomeFavorites(totalFavorites)
        whenMigrationApplied()

        val relations = syncRelationsDao.relations()
        assertTrue(relations.size == totalFavorites)

        val entities = syncEntitiesDao.entities()
        assertTrue(entities.size == totalFavorites + 2)
    }

    @Test
    fun whenBookmarksWithoutFoldersExistThenMigrationIsSuccessful() = runTest {
        val totalBookmarks = 10

        givenSomeBookmarks(totalBookmarks, Relation.BOOMARKS_ROOT_ID)

        whenMigrationApplied()

        assertTrue(syncEntitiesDao.hasEntities())
        assertTrue(syncRelationsDao.hasRelations())

        syncEntitiesDao.entitiesByType(BOOKMARK).test {
            val entities = awaitItem()
            assertTrue(entities.size == totalBookmarks)
        }

        val relations = (syncRelationsDao.relations())
        assertTrue(relations.size == totalBookmarks)
    }

    @Test
    fun whenBookmarksWithFoldersExistThenMigrationIsSuccessful() {
        val totalFolder = 10
        val bookmarksPerFolder = 5
        createFoldersTree(totalFolder, bookmarksPerFolder)

        val totalFavorites = 15
        givenSomeFavorites(totalFavorites)

        whenMigrationApplied()

        assertTrue(syncEntitiesDao.hasEntities())
        assertTrue(syncRelationsDao.hasRelations())

        val entities = syncEntitiesDao.entities()
        assertTrue(entities.size == totalFolder + bookmarksPerFolder + totalFavorites + ROOT_FOLDERS)

        val relations = (syncRelationsDao.relations())
        assertTrue(relations.size == totalFolder + bookmarksPerFolder + totalFavorites)
    }

    @Test
    fun whenBookmarksWithFoldersAndFavoritesExistThenMigrationIsSuccessful() {
        val totalFolder = 10
        val bookmarksPerFolder = 5
        createFoldersTree(totalFolder, bookmarksPerFolder)

        whenMigrationApplied()

        assertTrue(syncEntitiesDao.hasEntities())
        assertTrue(syncRelationsDao.hasRelations())

        val entities = syncEntitiesDao.entities()
        assertTrue(entities.size == totalFolder + bookmarksPerFolder + ROOT_FOLDERS)

        val relations = (syncRelationsDao.relations())
        assertTrue(relations.size == totalFolder + bookmarksPerFolder) // total folder + root folder
    }

    @Test
    fun whenBookmarkAndFavoriteHaveSameUrlThenBookmarkAlsoMigratedAsFavorite() {
        bookmarksDao.insert(BookmarkEntity(1, "bookmark1", "http://test.com", 0))
        favoritesDao.insert(FavoriteEntity(2, "favorite1", "http://test.com", 0))

        whenMigrationApplied()

        // only one entity migrated
        assertTrue(syncEntitiesDao.entities().size == 1 + ROOT_FOLDERS)

        // two relations migrated, one for bookmarks and another for favorites
        assertTrue(syncRelationsDao.relations().size == 2)

        val bookmarks = syncEntitiesDao.entitiesInFolderSync(Relation.BOOMARKS_ROOT)
        val favorites = syncEntitiesDao.entitiesInFolderSync(Relation.FAVORITES_ROOT)

        assertTrue(bookmarks.size == 1)
        assertTrue(bookmarks.first().entityId == "bookmark1")

        assertTrue(favorites.size == 1)
        assertTrue(favorites.first().entityId == "bookmark1")
    }

    @Test
    fun whenBookmarkAndFavoriteHaveDifferentUrlThenBothAreMigrated() {
        bookmarksDao.insert(BookmarkEntity(1, "Bookmark1", "http://test.com", 0))
        favoritesDao.insert(FavoriteEntity(2, "Favorite1", "http://testee.com", 0))

        whenMigrationApplied()

        assertTrue(syncEntitiesDao.entities().size == 2 + ROOT_FOLDERS)
        assertTrue(syncRelationsDao.relations().size == 2)

        val bookmarks = syncEntitiesDao.entitiesInFolderSync(Relation.BOOMARKS_ROOT)
        val favorites = syncEntitiesDao.entitiesInFolderSync(Relation.FAVORITES_ROOT)

        assertTrue(bookmarks.size == 1)
        assertTrue(bookmarks.first().entityId == "bookmark1")

        assertTrue(favorites.size == 1)
        assertTrue(favorites.first().entityId == "favorite2")
    }

    @Ignore @Test
    fun whenDataIsMigratedThenOldTablesAreDeleted() {
        givenSomeFavorites(10)
        givenSomeBookmarks(5, Relation.BOOMARKS_ROOT_ID)
        whenMigrationApplied()

        assertFalse(favoritesDao.userHasFavorites())
        assertFalse(bookmarksDao.bookmarksCount() > 0)
        assertTrue(bookmarkFoldersDao.getBookmarkFoldersSync().isEmpty())
    }

    private fun whenMigrationApplied() {
        appDatabase.apply {
            AppDatabaseBookmarksMigrationCallback({ this }, coroutineRule.testDispatcherProvider).runMigration()
        }
    }

    private fun givenSomeFavorites(total: Int) {
        val favorites = mutableListOf<FavoriteEntity>()
        for (index in 1..total) {
            favorites.add(FavoriteEntity(index.toLong(), "Favorite$index", "http://favexample$index.com", index))
        }
        favoritesDao.insertList(favorites)
    }

    private fun givenSomeBookmarks(
        total: Int,
        bookmarkFolderId: Long,
    ) {
        val bookmarks = mutableListOf<BookmarkEntity>()
        for (index in 1..total) {
            bookmarks.add(BookmarkEntity(index.toLong(), "Bookmark$index", "http://bookmark$index.com", bookmarkFolderId))
        }
        bookmarksDao.insertList(bookmarks)
    }

    private fun givenAFolder(index: Int) {
        val folderEntity = if (index == Relation.BOOMARKS_ROOT_ID.toInt()) {
            BookmarkFolderEntity(Relation.BOOMARKS_ROOT_ID, "folder$index", Relation.BOOMARKS_ROOT_ID)
        } else {
            BookmarkFolderEntity(index.toLong(), "folder$index", (index - 1).toLong())
        }

        bookmarkFoldersDao.insert(folderEntity)
    }

    private fun createFoldersTree(
        totalFolders: Int,
        bookmarksPerFolder: Int,
    ) {
        for (index in 1..totalFolders) {
            givenAFolder(index)
            givenSomeBookmarks(bookmarksPerFolder, index.toLong())
        }
    }

    companion object {
        const val ROOT_FOLDERS = 2
    }
}
