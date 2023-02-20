/*
 * Copyright (c) 2021 DuckDuckGo
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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@ExperimentalCoroutinesApi
class FavoritesDataRepositoryTest {
    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    @Suppress("unused")
    val coroutineRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var syncEntitiesDao: SyncEntitiesDao
    private lateinit var syncRelationsDao: SyncRelationsDao
    private lateinit var repository: SavedSitesRepository

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        syncEntitiesDao = db.syncEntitiesDao()
        syncRelationsDao = db.syncRelationsDao()
        repository = RealSavedSitesRepository(syncEntitiesDao, syncRelationsDao)
    }

    @Test
    fun whenFavoriteByUrlRequestedAndNoFavoritesAvailableThenReturnNull() = runTest {
        val result = repository.getFavorite("www.test.com")

        assertNull(result)
    }

    private fun givenFavorite(vararg favorite: Favorite) {
        favorite.forEach {
            val entity = Entity(it.id, it.title, it.url, type = BOOKMARK)
            syncEntitiesDao.insert(entity)
            syncRelationsDao.insert(Relation(Relation.FAVORITES_ROOT, entity.entityId))
        }
    }

    private fun givenMoreFavoritesStored() {
        val entity1 = Entity(title = "title", url = "http://example.com", type = BOOKMARK)
        val entity2 = Entity(title = "title2", url = "http://examples.com", type = BOOKMARK)

        syncEntitiesDao.insert(entity1)
        syncRelationsDao.insert(Relation(Relation.FAVORITES_ROOT, entity1.entityId))

        syncEntitiesDao.insert(entity2)
        syncRelationsDao.insert(Relation(Relation.FAVORITES_ROOT, entity2.entityId))
    }

    private fun givenNoFavoritesStored() {
        assertFalse(repository.hasFavorites())
    }

    private fun assertFavoriteExistsInDb(favorite: Favorite) {
        val storedFavorite = syncEntitiesDao.favorite(url = favorite.url) ?: error("Favorite not found in database")
        assertEquals(storedFavorite.title, favorite.title)
        assertEquals(storedFavorite.url, favorite.url)
    }

    @Test
    fun whenAllFavoritesDeletedThenDeleteAllFavorites() = runTest {
        val favorite = Favorite("favorite1", "Favorite", "http://favexample.com", 1)
        val favorite2 = Favorite("favorite2", "Favorite2", "http://favexample2.com", 2)
        givenFavorite(favorite, favorite2)

        repository.deleteAll()

        givenNoFavoritesStored()
    }
}
