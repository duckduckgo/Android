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
import com.duckduckgo.app.bookmarks.db.FavoritesDao
import com.duckduckgo.app.bookmarks.migration.AppDatabaseBookmarksMigrationCallback
import com.duckduckgo.app.bookmarks.model.FavoritesDataRepository
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import dagger.Lazy
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

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

        appDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        favoritesDao = appDatabase.favoritesDao()
        favoritesRepository = FavoritesDataRepository(favoritesDao, lazyFaviconManager)

        syncEntitiesDao = appDatabase.syncEntitiesDao()
        syncRelationsDao = appDatabase.syncRelationsDao()
    }

    @Test
    fun whenFavouritesExistThenMigrateBookmarksAndRelations() {
        favoritesRepository.insert("title", "http://example.com")

        appDatabase.apply {
            AppDatabaseBookmarksMigrationCallback({ this }, coroutineRule.testDispatcherProvider).migrateBookmarks()
        }

        assertTrue(syncEntitiesDao.hasEntities())
        assertTrue(syncRelationsDao.hasRelations())
    }
}
