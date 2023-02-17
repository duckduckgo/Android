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
import com.duckduckgo.sync.store.Entity
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import com.duckduckgo.sync.store.Relation
import com.duckduckgo.sync.store.SyncEntitiesDao
import com.duckduckgo.sync.store.SyncRelationsDao
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
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

    @After
    fun after() {
        db.close()
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
            relations.add(Relation(folderId, it))
        }
        return relations
    }

    private fun givenEmptyDBState() {
        syncRelationsDao.insertList(givenFolderWithContent(Relation.BOOMARKS_ROOT, emptyList()))
        syncRelationsDao.insertList(givenFolderWithContent(Relation.FAVORITES_ROOT, emptyList()))
    }
}
