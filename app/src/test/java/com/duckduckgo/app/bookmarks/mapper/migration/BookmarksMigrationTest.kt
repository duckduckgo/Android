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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFoldersDao
import com.duckduckgo.app.bookmarks.db.BookmarksDao
import com.duckduckgo.app.bookmarks.db.FavoriteEntity
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.bookmarks.migration.AppDatabaseBookmarksMigrationCallback
import com.duckduckgo.app.bookmarks.model.FavoritesDataRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import dagger.Lazy
import junit.framework.Assert.assertFalse
import junit.framework.Assert.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
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
    private lateinit var favoritesRepository: FavoritesRepository

    private lateinit var bookmarksDao: BookmarksDao
    private lateinit var bookmarkFoldersDao: BookmarkFoldersDao

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        favoritesDao = appDatabase.favoritesDao()
        favoritesRepository = FavoritesDataRepository(favoritesDao, lazyFaviconManager)

        bookmarksDao = appDatabase.bookmarksDao()
        bookmarkFoldersDao = appDatabase.bookmarkFoldersDao()

        syncEntitiesDao = appDatabase.syncEntitiesDao()
        syncRelationsDao = appDatabase.syncRelationsDao()
    }

    @Test
    fun whenFavoritesExistThenMigrationIsSuccessful() {
        val totalFavorites = 10
        givenSomeFavorites(totalFavorites)
        whenMigrationApplied()

        assertTrue(syncEntitiesDao.hasEntities())
        assertTrue(syncRelationsDao.hasRelations())

        val entities = syncEntitiesDao.entities()
        assertTrue(entities.size == totalFavorites)

        val relations = (syncRelationsDao.relations())
        assertTrue(relations.size == 1)

        val relation = relations.first()
        assertTrue(relation.children.size == totalFavorites )
        assertTrue(relation.id == Relation.FAVORITES_ROOT)
    }

    @Test
    fun whenBookmarksWithoutFoldersExistThenMigrationIsSuccessful(){
        val totalBookmarks = 10
        givenSomeBookmarks(totalBookmarks, Relation.BOOMARKS_ROOT_ID)

        whenMigrationApplied()

        assertTrue(syncEntitiesDao.hasEntities())
        assertTrue(syncRelationsDao.hasRelations())

        val entities = syncEntitiesDao.entities()
        assertTrue(entities.size == totalBookmarks)

        val relations = (syncRelationsDao.relations())
        assertTrue(relations.size == 1)

        val relation = relations.first()
        assertTrue(relation.children.size == totalBookmarks )
        assertTrue(relation.id == Relation.BOOMARKS_ROOT)
    }

    @Test
    fun whenDataIsMigratedThenOldTablesAreDeleted() {
        givenSomeFavorites(10)
        givenSomeBookmarks(5, Relation.BOOMARKS_ROOT_ID)
        whenMigrationApplied()

        assertFalse(favoritesDao.userHasFavorites())
        assertFalse(bookmarksDao.bookmarksCount() > 0)
        assertTrue(bookmarkFoldersDao.getBookmarkFoldersSync().isEmpty())
    }

    private fun whenMigrationApplied(){
        appDatabase.apply {
            AppDatabaseBookmarksMigrationCallback({ this }, coroutineRule.testDispatcherProvider).runMigration()
        }
    }

    private fun givenSomeFavorites(total: Int){
        for (index in 1..total) {
            val favorite = FavoriteEntity(index.toLong(), "Favorite$index", "http://favexample.com", index)
            favoritesDao.insert(favorite)
        }
    }

    private fun givenSomeBookmarks(total: Int, bookmarkFolderId: Long){
        for (index in 1..total) {
            val bookmark = BookmarkEntity(index.toLong(), "Bookmark$index", "http://bookmark.com", bookmarkFolderId)
            bookmarksDao.insert(bookmark)
        }
    }
}
