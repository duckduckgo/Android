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
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDatabase
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataEntity
import com.duckduckgo.savedsites.store.Entity
import com.duckduckgo.savedsites.store.EntityType.BOOKMARK
import com.duckduckgo.savedsites.store.EntityType.FOLDER
import com.duckduckgo.savedsites.store.Relation
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

    val bookmarksRoot = Entity(SavedSitesNames.BOOKMARKS_ROOT, "DuckDuckGo Bookmarks", "", FOLDER, "", false)
    val favouritesRoot = Entity(entityId = SavedSitesNames.FAVORITES_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER)
    val favouritesDesktopRoot =
        Entity(entityId = SavedSitesNames.FAVORITES_DESKTOP_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER)
    val favouritesMobileRoot =
        Entity(entityId = SavedSitesNames.FAVORITES_MOBILE_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER)

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

        givenInitialFolderState()
    }

    @Test
    fun whenFolderMetadataNotPresentThenAllChildrenInCurrentAndInsertField() = runTest {
        givenSomeContentIn(folderId = folder.id, children = 5, saveMetadata = false)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(folderChildren.current.size, 5)
        Assert.assertEquals(folderChildren.insert.size, 5)
    }

    @Test
    fun whenFolderMetadataNotPresentThenDeletedChildrenNotPresentInDeletedField() = runTest {
        val entities = BookmarkTestUtils.givenSomeBookmarks(5)
        savedSitesEntitiesDao.insertList(entities)

        val relation = BookmarkTestUtils.givenFolderWithContent(folder.id, entities)
        savedSitesRelationsDao.insertList(relation)

        val deletedBookmark = entities.first()
        savedSitesRelationsDao.deleteRelationByEntity(deletedBookmark.entityId)
        savedSitesEntitiesDao.delete(deletedBookmark.entityId)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(folderChildren.current, entities.minus(deletedBookmark).map { it.entityId })
        Assert.assertEquals(folderChildren.current.size, 4)
        Assert.assertEquals(folderChildren.insert, entities.minus(deletedBookmark).map { it.entityId })
        Assert.assertEquals(folderChildren.insert.size, 4)
        Assert.assertEquals(folderChildren.remove.size, 0)
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

    @Test
    fun whenFolderIsReplacedThenChildrenAreUpdated() {
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val folderRelation = BookmarkTestUtils.givenFolderWithContent(folder.id, bookmarks)
        savedSitesRelationsDao.insertList(folderRelation)

        val storedChildren = savedSitesRelationsDao.relationsByFolderId(folder.id)

        Assert.assertEquals(storedChildren.map { it.entityId }, bookmarks.map { it.entityId })

        val remoteChildren = BookmarkTestUtils.givenSomeBookmarks(5)
        repository.replaceFolder(folder, remoteChildren.map { it.entityId })

        val replacedChildren = savedSitesRelationsDao.relationsByFolderId(folder.id)
        Assert.assertEquals(replacedChildren.map { it.entityId }, remoteChildren.map { it.entityId })
    }

    @Test
    fun whenFolderIsReplacedThenOrphanedChildrenAreRemoved() {
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val folderRelation = BookmarkTestUtils.givenFolderWithContent(folder.id, bookmarks)
        savedSitesRelationsDao.insertList(folderRelation)

        val remoteChildren = BookmarkTestUtils.givenSomeBookmarks(5)
        repository.replaceFolder(folder, remoteChildren.map { it.entityId })

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertEquals(orphans.map { it.entityId }, bookmarks.map { it.entityId })
    }

    @Test
    fun whenRemoteFolderDoesNotContainLocallyStoredChildThenOrphanIsCreated() {
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val folderRelation = BookmarkTestUtils.givenFolderWithContent(folder.id, bookmarks)
        savedSitesRelationsDao.insertList(folderRelation)

        val updatedChildren = bookmarks.toMutableList()
        val removedChildren = updatedChildren.removeFirst()

        repository.replaceFolder(folder, updatedChildren.map { it.entityId })

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertFalse(orphans.isEmpty())
        Assert.assertEquals(orphans[0].entityId, removedChildren.entityId)
    }

    @Test
    fun whenRemoteFolderContainsAddedChildrenThenOrphansAreNotCreated() {
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)

        val folderRelation = BookmarkTestUtils.givenFolderWithContent(folder.id, bookmarks)
        savedSitesRelationsDao.insertList(folderRelation)

        val addedChildren = BookmarkTestUtils.givenSomeBookmarks(5)
        val updatedFolderContent = bookmarks.plus(addedChildren)
        repository.replaceFolder(folder, updatedFolderContent.map { it.entityId })

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertTrue(orphans.isEmpty())
    }

    @Test
    fun whenOrphansPresentThenOrphansAttachedToBookmarksRoot() {
        // given a root folder that contains a subfolder
        val subfolder = Entity(folder.id, "Folder", "", FOLDER, "", false)
        savedSitesEntitiesDao.insert(subfolder)
        savedSitesRelationsDao.insert(Relation(folderId = bookmarksRoot.entityId, entityId = folder.id))

        // and the subfolder contains 10 bookmarks
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)
        val folderRelation = BookmarkTestUtils.givenFolderWithContent(folder.id, bookmarks)
        savedSitesRelationsDao.insertList(folderRelation)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(folder.id).size)

        // when we create orphans
        val remoteChildren = BookmarkTestUtils.givenSomeBookmarks(5)
        repository.replaceFolder(folder, remoteChildren.map { it.entityId })
        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(folder.id).size)

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertEquals(10, orphans.size)

        // then fixing orphans will attach those to the root
        val orphansPresent = repository.fixOrphans()
        Assert.assertTrue(orphansPresent)

        // root should now contain the original folder plus the original children of the folder
        val attachedChildren = savedSitesRelationsDao.relationsByFolderId(bookmarksRoot.entityId)
        Assert.assertEquals(attachedChildren.size, 11)
    }

    @Test
    fun whenFolderContentIsReplacedThenRelationsAreNotDuplicated() {
        val remoteChildren = BookmarkTestUtils.givenSomeBookmarks(5)
        savedSitesEntitiesDao.insertList(remoteChildren)
        val folderRelation = BookmarkTestUtils.givenFolderWithContent(bookmarksRoot.entityId, remoteChildren)
        savedSitesRelationsDao.insertList(folderRelation)

        repository.replaceFolder(folder, remoteChildren.map { it.entityId })

        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(bookmarksRoot.entityId).size)
    }

    @Test
    fun fixingOrphansDoesNotAffectFavourites() {
        // given a root folder that contains a subfolder
        savedSitesRelationsDao.insert(Relation(folderId = bookmarksRoot.entityId, entityId = folder.id))

        // and the subfolder contains 10 bookmarks
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)
        val folderRelation = BookmarkTestUtils.givenFolderWithContent(folder.id, bookmarks)
        savedSitesRelationsDao.insertList(folderRelation)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(folder.id).size)

        // and those bookmarks are also favourites
        val favouritesRootRelation = BookmarkTestUtils.givenFolderWithContent(favouritesRoot.entityId, bookmarks)
        savedSitesRelationsDao.insertList(favouritesRootRelation)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).size)

        val favouritesMobileRootRelation = BookmarkTestUtils.givenFolderWithContent(favouritesMobileRoot.entityId, bookmarks)
        savedSitesRelationsDao.insertList(favouritesMobileRootRelation)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesMobileRoot.entityId).size)

        // when we create orphans
        val remoteChildren = BookmarkTestUtils.givenSomeBookmarks(5)
        repository.replaceFolder(folder, remoteChildren.map { it.entityId })

        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(folder.id).size)
        Assert.assertEquals(1, savedSitesRelationsDao.relationsByFolderId(bookmarksRoot.entityId).size)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).size)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesMobileRoot.entityId).size)

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertEquals(10, orphans.size)

        // then fixing orphans will attach those to the root
        val orphansPresent = repository.fixOrphans()
        Assert.assertTrue(orphansPresent)
        Assert.assertEquals(11, savedSitesRelationsDao.relationsByFolderId(bookmarksRoot.entityId).size)

        // and favourites won't be affected
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).size)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesMobileRoot.entityId).size)
    }

    @Test
    fun whenPruningDeletedEntityThenAllRelatedDataIsDeleted() {
        // given a root folder that contains a subfolder
        val subfolder = Entity(folder.id, "Folder", "", FOLDER, "", false)
        savedSitesEntitiesDao.insert(subfolder)
        savedSitesRelationsDao.insert(Relation(folderId = bookmarksRoot.entityId, entityId = folder.id))

        // given some content in the subfolder
        givenSomeContentIn(folderId = folder.id, children = 5, saveMetadata = true)
        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(folder.id).size)
        Assert.assertTrue(savedSitesMetadataDao.get(folder.id) != null)

        // when we soft delete it
        savedSitesEntitiesDao.delete(folder.id)
        Assert.assertTrue(savedSitesMetadataDao.get(folder.id) != null)

        // and permanently delete it
        repository.pruneDeleted()

        // then metadata is also deleted
        Assert.assertTrue(savedSitesMetadataDao.get(folder.id) == null)
    }

    private fun givenInitialFolderState() {
        // when bookmarks root and all favourites folders present
        savedSitesEntitiesDao.insert(bookmarksRoot)
        // and favourites folders present
        savedSitesEntitiesDao.insert(favouritesRoot)
        savedSitesEntitiesDao.insert(favouritesDesktopRoot)
        savedSitesEntitiesDao.insert(favouritesMobileRoot)
    }

    private fun givenSomeContentIn(
        folderId: String = SavedSitesNames.BOOKMARKS_ROOT,
        children: Int = 5,
        saveMetadata: Boolean = true,
    ) {
        val entities = BookmarkTestUtils.givenSomeBookmarks(children)
        savedSitesEntitiesDao.insertList(entities)

        val relation = BookmarkTestUtils.givenFolderWithContent(folderId, entities)
        savedSitesRelationsDao.insertList(relation)

        if (saveMetadata) {
            val childrenJSON = stringListAdapter.toJson(entities.map { it.entityId })
            val metadata = SavedSitesSyncMetadataEntity(folderId, childrenJSON, null)
            savedSitesMetadataDao.addOrUpdate(metadata)
        }
    }
}
