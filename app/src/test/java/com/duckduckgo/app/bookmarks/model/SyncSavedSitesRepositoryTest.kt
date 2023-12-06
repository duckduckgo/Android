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

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.bookmarks.BookmarkTestUtils
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.sync.FakeDisplayModeSettingsRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.RealFavoritesDelegate
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDatabase
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataEntity
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SyncSavedSitesRepositoryTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var savedSitesMetadataDao: SavedSitesSyncMetadataDao

    private lateinit var appDatabase: AppDatabase
    private lateinit var savedSitesDatabase: SavedSitesSyncMetadataDatabase
    private lateinit var repository: SyncSavedSitesRepository

    val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

    val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
    val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)

    @Before
    fun setup() {
        appDatabase = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        savedSitesEntitiesDao = appDatabase.syncEntitiesDao()
        savedSitesRelationsDao = appDatabase.syncRelationsDao()

        savedSitesDatabase =
            Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, SavedSitesSyncMetadataDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        savedSitesMetadataDao = savedSitesDatabase.syncMetadataDao()

        repository = RealSyncSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            savedSitesMetadataDao,
        )
    }

    @Test
    fun whenFolderMetadataNotPresentThenAllChildrenInCurrentAndInsertField() = runTest {
        givenSomeContentIn(folderId = folder.id, children = 5, saveMetadata = false)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(folderChildren.current.size, 5)
        Assert.assertEquals(folderChildren.insert.size, 5)
    }

    @Test
    fun whenFolderMetadataPresentAndSameContentThenAllChildrenInCurrentAndInsertEmpty() = runTest {
        givenSomeContentIn(folderId = folder.id, children = 5, saveMetadata = true)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(folderChildren.current.size, 5)
        Assert.assertEquals(folderChildren.insert.size, 0)
    }

    @Test
    fun whenFolderMetadataPresentAndLocalContentHasItemAddedThenFolderDiffContainsInsertedItem() = runTest {
        val entities = BookmarkTestUtils.givenSomeBookmarks(5)
        savedSitesEntitiesDao.insertList(entities)

        val relation = BookmarkTestUtils.givenFolderWithContent(folder.id, entities)
        savedSitesRelationsDao.insertList(relation)

        val removedEntities = entities.toMutableList()
        val removedEntity = removedEntities.removeFirst()

        val removedEntitiesIds = removedEntities.map { it.entityId }
        val childrenJSON = stringListAdapter.toJson(removedEntitiesIds)
        val metadata = SavedSitesSyncMetadataEntity(folder.id, childrenJSON, "[]")
        savedSitesMetadataDao.addOrUpdate(metadata)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(folderChildren.current, entities.map { it.entityId })
        Assert.assertEquals(folderChildren.current.size, 5)
        Assert.assertEquals(folderChildren.insert.size, 1)
        Assert.assertEquals(folderChildren.insert, listOf(removedEntity.entityId))
        Assert.assertEquals(folderChildren.remove.size, 0)
    }

    @Test
    fun whenFolderMetadataPresentAndLocalContentHasItemRemovedThenFolderDiffContainsDeletedItem() = runTest {
        val entities = BookmarkTestUtils.givenSomeBookmarks(5)
        val entityRemoved =
            Entity(title = "entity6", url = "https://testUrl6", type = BOOKMARK, lastModified = DatabaseDateFormatter.iso8601(), deleted = true)
        savedSitesEntitiesDao.insertList(entities)
        savedSitesEntitiesDao.insert(entityRemoved)

        val relation = BookmarkTestUtils.givenFolderWithContent(folder.id, entities)
        savedSitesRelationsDao.insertList(relation)

        val responseEntities = entities.toMutableList().plus(entityRemoved)

        val removedEntitiesIds = responseEntities.map { it.entityId }
        val childrenJSON = stringListAdapter.toJson(removedEntitiesIds)
        val metadata = SavedSitesSyncMetadataEntity(folder.id, childrenJSON, "[]")
        savedSitesMetadataDao.addOrUpdate(metadata)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(folderChildren.current, entities.map { it.entityId })
        Assert.assertEquals(folderChildren.current.size, 5)
        Assert.assertEquals(folderChildren.insert.size, 0)
        Assert.assertEquals(folderChildren.remove.size, 1)
        Assert.assertEquals(folderChildren.remove, listOf(entityRemoved.entityId))
    }

    private fun givenSomeContentIn(
        folderId: String = SavedSitesNames.BOOKMARKS_ROOT,
        children: Int = 5,
        saveMetadata: Boolean = true
    ) {
        val entities = BookmarkTestUtils.givenSomeBookmarks(children)
        savedSitesEntitiesDao.insertList(entities)

        val relation = BookmarkTestUtils.givenFolderWithContent(folderId, entities)
        savedSitesRelationsDao.insertList(relation)

        if (saveMetadata) {
            val childrenJSON = stringListAdapter.toJson(entities.map { it.entityId })
            val metadata = SavedSitesSyncMetadataEntity(folderId, childrenJSON, "[]")
            savedSitesMetadataDao.addOrUpdate(metadata)
        }
    }
}
