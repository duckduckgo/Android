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
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.aBookmark
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.aBookmarkFolder
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.aFavorite
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.getRequestEntryFromBookmarkFolder
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.getRequestEntryFromSavedSite
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.getResponseEntryFromBookmarkFolder
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.getResponseEntryFromSavedSite
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.sync.FakeDisplayModeSettingsRepository
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.MissingEntitiesRelationReconciler
import com.duckduckgo.savedsites.impl.RealFavoritesDelegate
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.store.RealSavedSitesSyncEntitiesStore
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
import java.time.OffsetDateTime
import java.time.ZoneOffset
import junit.framework.TestCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertTrue
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
    private lateinit var savedSitesRepository: SavedSitesRepository
    private val store = RealSavedSitesSyncEntitiesStore(
        InstrumentationRegistry.getInstrumentation().context,
    )

    val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    val stringListAdapter: JsonAdapter<List<String>> = Moshi.Builder().build().adapter(stringListType)

    val bookmark = Bookmark(id = "bookmark1", title = "title", url = "foo.com", lastModified = "timestamp", parentId = SavedSitesNames.BOOKMARKS_ROOT)
    val folder = BookmarkFolder("folder1", "title", SavedSitesNames.BOOKMARKS_ROOT, 0, 0)

    val bookmarksRoot = Entity(SavedSitesNames.BOOKMARKS_ROOT, "DuckDuckGo Bookmarks", "", FOLDER, DatabaseDateFormatter.iso8601(), false)
    val favouritesRoot = Entity(entityId = SavedSitesNames.FAVORITES_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER)
    val favouritesDesktopRoot =
        Entity(entityId = SavedSitesNames.FAVORITES_DESKTOP_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER)
    val favouritesMobileRoot =
        Entity(entityId = SavedSitesNames.FAVORITES_MOBILE_ROOT, url = "", title = SavedSitesNames.FAVORITES_NAME, type = FOLDER)

    val bookmarksRootFolder = aBookmarkFolder(SavedSitesNames.BOOKMARKS_ROOT, SavedSitesNames.BOOKMARKS_NAME, "")
    val favoritesFolder = aBookmarkFolder(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "")
    val favourite1 = aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0)
    val favourite2 = aFavorite("bookmark2", "Bookmark 2", "https://bookmark2.com", 1)
    val bookmark1 = aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com")
    val bookmark2 = aBookmark("bookmark2", "Bookmark 2", "https://bookmark2.com")
    val bookmark3 = aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com")
    val bookmark4 = aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com")

    private val threeHoursAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(3))
    private val twoHoursAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(2))

    private val oneHourAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)

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
            store,
        )

        val favoritesDisplayModeSettings = FakeDisplayModeSettingsRepository()
        val favoritesDelegate = RealFavoritesDelegate(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDisplayModeSettings,
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )
        savedSitesRepository = RealSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDelegate,
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )

        givenInitialFolderState()
    }

    @After
    fun after() {
        appDatabase.close()
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
        givenSomeContentIn(folderId = folder.id, children = 5, saveMetadata = true, metadataRequest = false)

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
        val removedEntity = removedEntities.removeAt(0)

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
    fun whenFolderMetadataPresentAndLocalContentHasMissingRelationsThenFolderDiffDoesNotContainDeletedItem() = runTest {
        val entities = BookmarkTestUtils.givenSomeBookmarks(5)
        savedSitesEntitiesDao.insertList(entities.dropLast(1))

        val relation = BookmarkTestUtils.givenFolderWithContent(folder.id, entities)
        savedSitesRelationsDao.insertList(relation)

        val serverEntities = entities.map { it.entityId }
        val childrenJSON = stringListAdapter.toJson(serverEntities)
        val metadata = SavedSitesSyncMetadataEntity(folder.id, childrenJSON, "[]")
        savedSitesMetadataDao.addOrUpdate(metadata)

        val folderChildren = repository.getFolderDiff(folder.id)

        Assert.assertEquals(entities.map { it.entityId }, folderChildren.current)
        Assert.assertEquals(5, folderChildren.current.size)
        Assert.assertEquals(0, folderChildren.insert.size)
        Assert.assertEquals(0, folderChildren.remove.size)
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
        repository.replaceBookmarkFolder(folder, remoteChildren.map { it.entityId })

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
        repository.replaceBookmarkFolder(folder, remoteChildren.map { it.entityId })

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
        val removedChildren = updatedChildren.removeAt(0)

        repository.replaceBookmarkFolder(folder, updatedChildren.map { it.entityId })

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
        repository.replaceBookmarkFolder(folder, updatedFolderContent.map { it.entityId })

        val orphans = savedSitesRelationsDao.getOrphans()
        assertTrue(orphans.isEmpty())
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
        repository.replaceBookmarkFolder(folder, remoteChildren.map { it.entityId })
        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(folder.id).size)

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertEquals(10, orphans.size)

        // then fixing orphans will attach those to the root
        val orphansPresent = repository.fixOrphans()
        assertTrue(orphansPresent)

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

        repository.replaceBookmarkFolder(folder, remoteChildren.map { it.entityId })

        Assert.assertEquals(0, savedSitesRelationsDao.relationsByFolderId(bookmarksRoot.entityId).size)
        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(folder.id).size)
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
        repository.replaceBookmarkFolder(folder, remoteChildren.map { it.entityId })

        Assert.assertEquals(5, savedSitesRelationsDao.relationsByFolderId(folder.id).size)
        Assert.assertEquals(1, savedSitesRelationsDao.relationsByFolderId(bookmarksRoot.entityId).size)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).size)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesMobileRoot.entityId).size)

        val orphans = savedSitesRelationsDao.getOrphans()
        Assert.assertEquals(10, orphans.size)

        // then fixing orphans will attach those to the root
        val orphansPresent = repository.fixOrphans()
        assertTrue(orphansPresent)
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
        assertTrue(savedSitesMetadataDao.get(folder.id) != null)

        // when we soft delete it
        savedSitesEntitiesDao.delete(folder.id)
        assertTrue(savedSitesMetadataDao.get(folder.id) != null)

        // and permanently delete it
        repository.pruneDeleted()

        // then metadata is also deleted
        assertTrue(savedSitesMetadataDao.get(folder.id) == null)
    }

    @Test
    fun whenGeneratingLocalChangesThenMetadataRequestIsUpdated() {
        val expectedContent = listOf(
            getRequestEntryFromBookmarkFolder(favoritesFolder, listOf(bookmark1).map { it.id }),
            getRequestEntryFromSavedSite(bookmark1),
            getRequestEntryFromSavedSite(bookmark3),
            getRequestEntryFromSavedSite(bookmark4),
            getRequestEntryFromBookmarkFolder(bookmarksRootFolder, listOf(bookmark1, bookmark3, bookmark4).map { it.id }),
        )

        repository.addRequestMetadata(expectedContent)

        assertTrue(savedSitesMetadataDao.all().isNotEmpty())

        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)!!.childrenRequest != null)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)!!.childrenResponse == null)
    }

    @Test
    fun whenGeneratingLocalChangesAreEmptyThenMetadataRequestIsNotUpdated() {
        repository.addRequestMetadata(emptyList())
        assertTrue(savedSitesMetadataDao.all().isEmpty())
    }

    @Test
    fun whenMetadataResponseExistedAddingRequestKeepsPreviousResponse() {
        val storedMetadata = SavedSitesSyncMetadataEntity(favoritesFolder.id, "previousResponse", null)
        savedSitesMetadataDao.addOrUpdate(storedMetadata)

        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)!!.childrenResponse == storedMetadata.childrenResponse)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)!!.childrenRequest == null)

        val expectedContent = listOf(
            getRequestEntryFromBookmarkFolder(favoritesFolder, listOf(bookmark1).map { it.id }),
        )

        repository.addRequestMetadata(expectedContent)

        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)!!.childrenResponse == storedMetadata.childrenResponse)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)!!.childrenRequest != null)
    }

    @Test
    fun whenResponseHasNoEntitiesThenMetadataIsNotUpdated() {
        repository.addResponseMetadata(emptyList())
        assertTrue(savedSitesMetadataDao.all().isEmpty())
    }

    @Test
    fun whenResponseHasNoEntitiesAfterAPatchRequestThenMetadataIsCopiedAsResponse() {
        givenSomeContentIn(folderId = bookmarksRootFolder.id, children = 5)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenResponse == null)

        repository.addResponseMetadata(emptyList())

        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenResponse != null)
    }

    @Test
    fun whenResponseHasEntitiesThenMetadataIsUpdated() {
        val responseEntries = listOf(
            getResponseEntryFromSavedSite(bookmark),
            getResponseEntryFromBookmarkFolder(folder, listOf(bookmark.id)),
            getResponseEntryFromBookmarkFolder(bookmarksRootFolder, listOf(folder.id)),
        )

        repository.addResponseMetadata(responseEntries)

        assertTrue(savedSitesMetadataDao.all().isNotEmpty())

        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenResponse != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest == null)
    }

    @Test
    fun whenDiscardRequestMetadataThenMetadataRequestIsRemoved() {
        val requestContent = listOf(
            getRequestEntryFromSavedSite(bookmark1),
            getRequestEntryFromSavedSite(bookmark3),
            getRequestEntryFromSavedSite(bookmark4),
            getRequestEntryFromBookmarkFolder(favoritesFolder, listOf(bookmark1).map { it.id }),
            getRequestEntryFromBookmarkFolder(bookmarksRootFolder, listOf(bookmark1, bookmark3, bookmark4).map { it.id }),
        )
        repository.addRequestMetadata(requestContent)

        repository.discardRequestMetadata()

        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)?.childrenRequest == null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest == null)
    }

    @Test
    fun whenDiscardRequestMetadataAndStoreResponseMetadataThenMetadataStatusOnlyReflectsResponse() {
        val requestContent = listOf(
            getRequestEntryFromBookmarkFolder(favoritesFolder, listOf(bookmark1).map { it.id }),
            getRequestEntryFromSavedSite(bookmark1),
            getRequestEntryFromSavedSite(bookmark3),
            getRequestEntryFromSavedSite(bookmark4),
            getRequestEntryFromBookmarkFolder(bookmarksRootFolder, listOf(bookmark1, bookmark3, bookmark4).map { it.id }),
        )
        repository.addRequestMetadata(requestContent)

        val responseEntries = listOf(
            getResponseEntryFromSavedSite(bookmark),
            getResponseEntryFromBookmarkFolder(folder, listOf(bookmark.id)),
            getResponseEntryFromBookmarkFolder(bookmarksRootFolder, listOf(folder.id)),
        )

        repository.discardRequestMetadata()
        repository.addResponseMetadata(responseEntries)

        assertTrue(savedSitesMetadataDao.all().isNotEmpty())

        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)?.childrenRequest == null)
        assertTrue(savedSitesMetadataDao.get(favoritesFolder.id)?.childrenResponse == null)

        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest == null)
        Assert.assertEquals("[\"folder1\"]", savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenResponse)

        assertTrue(savedSitesMetadataDao.get(folder.id)!!.childrenRequest == null)
        Assert.assertEquals("[\"bookmark1\"]", savedSitesMetadataDao.get(folder.id)!!.childrenResponse)
    }

    @Test
    fun whenResponseHasEntitiesAndOnlyContainsBookmarksThenMetadataIsNotUpdated() {
        val responseEntries = listOf(
            getResponseEntryFromSavedSite(bookmark),
        )

        repository.addResponseMetadata(responseEntries)

        assertTrue(savedSitesMetadataDao.all().isEmpty())
    }

    @Test
    fun whenResponseHasNoFoldersAfterAPatchRequestThenMetadataIsCopiedAsResponse() {
        givenSomeContentIn(folderId = bookmarksRootFolder.id, children = 5)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenResponse == null)

        val responseEntries = listOf(
            getResponseEntryFromSavedSite(bookmark),
        )

        repository.addResponseMetadata(responseEntries)

        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenRequest != null)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id)!!.childrenResponse != null)
    }

    @Test
    fun whenResponseHasDeletedFoldersThenMetadataIsDeleted() {
        givenSomeContentIn(folderId = bookmarksRootFolder.id, children = 5)
        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) != null)

        val response = listOf(getResponseEntryFromBookmarkFolder(bookmarksRootFolder, listOf(folder.id), deleted = true))
        repository.addResponseMetadata(response)

        assertTrue(savedSitesMetadataDao.get(bookmarksRootFolder.id) == null)
    }

    @Test
    fun whenUpdatingModifiedSinceThenDatesAreProperlyUpdated() {
        val oneHourAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))

        givenSomeContentIn(folderId = bookmarksRootFolder.id, children = 5)

        savedSitesEntitiesDao.updateModified(bookmarksRootFolder.id, oneHourAgo)

        assert(savedSitesEntitiesDao.entityById(bookmarksRootFolder.id)?.lastModified == oneHourAgo)
    }

    @Test
    fun whenEntitiesPresentBeforeDeduplicationThenTheirTimestampIsUpdated() {
        givenSomeContentIn(bookmarksRootFolder.id, 5)

        val oneHourAgo = DatabaseDateFormatter.iso8601(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
        repository.setLocalEntitiesForNextSync(oneHourAgo)

        assertTrue(savedSitesEntitiesDao.entityById(bookmarksRootFolder.id)!!.lastModified != oneHourAgo)
    }

    @Test
    fun whenDeduplicatingBookmarkThenRemoteBookmarkReplacesLocal() {
        // given a local bookmark
        repository.insert(bookmark1, favouritesRoot.entityId)

        // when replacing with a remote one
        val remoteBookmark1 = bookmark1.copy(id = "remoteBookmark")
        repository.replaceBookmark(remoteBookmark1, bookmark1.id)

        // entities have been replaced
        assertTrue(savedSitesEntitiesDao.entityById(bookmark1.id) == null)
        assertTrue(savedSitesEntitiesDao.entityById(remoteBookmark1.id) != null)
    }

    @Test
    fun whenBookmarkIsReplacedWithDifferentIdThenDataIsUpdated() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        savedSitesRepository.insert(rootFolder)

        val bookmark = savedSitesRepository.insert(
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

        val bookmarkUpdated = savedSitesRepository.getBookmark(bookmark.url)!!

        assertTrue(updatedBookmark.id == bookmarkUpdated.id)
        assertTrue(bookmark.url == bookmarkUpdated.url)
        assertTrue(bookmark.title == bookmarkUpdated.title)
    }

    @Test
    fun whenBookmarkIsReplacedWithSameIdThenDataIsUpdated() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        savedSitesRepository.insert(rootFolder)

        val bookmark = savedSitesRepository.insert(
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

        val bookmarkUpdated = savedSitesRepository.getBookmark(bookmark.url)!!

        assertTrue(bookmark.id == bookmarkUpdated.id)
        assertTrue(bookmark.url == bookmarkUpdated.url)
        assertTrue("title2" == bookmarkUpdated.title)
    }

    @Test
    fun whenReplacingBookmarThenDataIsUpdated() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        val subFolder = BookmarkFolder(id = "folder", name = "Folder", lastModified = "timestamp", parentId = rootFolder.id)
        savedSitesRepository.insert(rootFolder)
        savedSitesRepository.insert(subFolder)

        val bookmark = savedSitesRepository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = rootFolder.id,
            ),
        ) as Bookmark

        val updatedBookmark = bookmark.copy(title = "title2", parentId = subFolder.id)

        repository.replaceBookmark(updatedBookmark, bookmark.id)

        val bookmarkUpdated = savedSitesRepository.getBookmarkById(bookmark.id)!!

        assertTrue(bookmark.id == bookmarkUpdated.id)
        assertTrue(rootFolder.id == bookmarkUpdated.parentId)
    }

    @Test
    fun whenReplacingBookmarkWithBookmarkFromAnotherFolderThenDataIsUpdated() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        val subFolder = BookmarkFolder(id = "folder", name = "Folder", lastModified = "timestamp", parentId = rootFolder.id)
        savedSitesRepository.insert(rootFolder)
        savedSitesRepository.insert(subFolder)

        val bookmark = savedSitesRepository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = rootFolder.id,
            ),
        ) as Bookmark

        val updatedBookmark = bookmark.copy(title = "title2", parentId = subFolder.id)

        repository.replaceBookmarkFolder(rootFolder, emptyList())
        repository.replaceBookmarkFolder(subFolder, listOf(updatedBookmark.id))
        repository.replaceBookmark(updatedBookmark, bookmark.id)

        val bookmarkUpdated = savedSitesRepository.getBookmarkById(bookmark.id)!!

        assertTrue(bookmark.id == bookmarkUpdated.id)
        assertTrue(subFolder.id == bookmarkUpdated.parentId)
    }

    @Test
    fun whenMovingBookmarkToAnotherFolderThenBookmarkOnlyHasOnlyBelongsToOneFolder() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        val subFolder = BookmarkFolder(id = "folder", name = "Folder", lastModified = "timestamp", parentId = rootFolder.id)
        savedSitesRepository.insert(rootFolder)
        savedSitesRepository.insert(subFolder)

        val bookmark = savedSitesRepository.insert(
            Bookmark(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                parentId = rootFolder.id,
            ),
        ) as Bookmark

        repository.replaceBookmarkFolder(subFolder, listOf(bookmark.id))

        val relations = savedSitesRelationsDao.relationsByEntityId(bookmark.id)
        assertTrue(relations.size == 1)
        assertTrue(relations.any { it.folderId == subFolder.id })
    }

    @Test
    fun whenMovingFavouriteToAnotherFolderThenBookmarkBelongsToFavouritesAndFolder() {
        val rootFolder = BookmarkFolder(id = SavedSitesNames.BOOKMARKS_ROOT, name = "root", lastModified = "timestamp", parentId = "")
        val subFolder = BookmarkFolder(id = "folder", name = "Folder", lastModified = "timestamp", parentId = rootFolder.id)
        savedSitesRepository.insert(rootFolder)
        savedSitesRepository.insert(subFolder)

        // given a favourite inserted in favourites root and bookmarks root
        val bookmark = savedSitesRepository.insert(
            Favorite(
                id = "bookmark1",
                title = "title",
                url = "foo.com",
                lastModified = "timestamp",
                position = 0,
            ),
        ) as Favorite

        // when moved to another folder
        repository.replaceBookmarkFolder(subFolder, listOf(bookmark.id))

        // then the bookmark belongs to the new folder and favourites root
        val relations = savedSitesRelationsDao.relationsByEntityId(bookmark.id)
        assertTrue(relations.size == 2)
        assertTrue(relations.any { it.folderId == subFolder.id })
        assertTrue(relations.any { it.folderId == SavedSitesNames.FAVORITES_ROOT })
    }

    @Test
    fun whenBookmarkModifiedAfterThresholdThenGetModifiedSinceHasBookmarks() {
        val since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
        val twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)

        val modifiedEarlierBookmarks = BookmarkTestUtils.givenSomeBookmarks(5, DatabaseDateFormatter.iso8601(twoHoursAgo))
        savedSitesEntitiesDao.insertList(modifiedEarlierBookmarks)

        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(3)
        savedSitesEntitiesDao.insertList(bookmarks)

        val relation = BookmarkTestUtils.givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, modifiedEarlierBookmarks.plus(bookmarks))
        savedSitesRelationsDao.insertList(relation)

        val modifiedSinceBookmarks = repository.getBookmarksModifiedSince(DatabaseDateFormatter.iso8601(since))
        TestCase.assertEquals(bookmarks.size, modifiedSinceBookmarks.size)
    }

    @Test
    fun whenBookmarkModifiedBeforeThresholdThenGetModifiedSinceIsEmpty() {
        val since = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)
        val twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)

        val modifiedEarlierBookmarks = BookmarkTestUtils.givenSomeBookmarks(5, DatabaseDateFormatter.iso8601(twoHoursAgo))
        savedSitesEntitiesDao.insertList(modifiedEarlierBookmarks)

        val relation = BookmarkTestUtils.givenFolderWithContent(SavedSitesNames.BOOKMARKS_ROOT, modifiedEarlierBookmarks)
        savedSitesRelationsDao.insertList(relation)

        val modifiedSinceBookmarks = repository.getBookmarksModifiedSince(DatabaseDateFormatter.iso8601(since))
        TestCase.assertTrue(modifiedSinceBookmarks.isEmpty())
    }

    @Test
    fun whenReplacingFavouritesFolderThenOnlyCurrentChildrenAreKept() {
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
        val favouritesMobileRootRelation = BookmarkTestUtils.givenFolderWithContent(favouritesMobileRoot.entityId, bookmarks)
        savedSitesRelationsDao.insertList(favouritesMobileRootRelation)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).size)
        Assert.assertEquals(10, savedSitesRelationsDao.relationsByFolderId(favouritesMobileRoot.entityId).size)

        // when replacing favourite folder
        val favourites = BookmarkTestUtils.givenSomeBookmarks(5)

        // then only new children are kept
        repository.replaceFavouriteFolder(favouritesRoot.entityId, favourites.map { it.entityId })
        Assert.assertEquals(favourites.map { it.entityId }, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).map { it.entityId })
    }

    @Test
    fun whenAddingToFavouritesFolderThenPreviousFavouritesAreKept() {
        // given some favourites
        val firstBatch = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesRelationsDao.insertList(BookmarkTestUtils.givenFolderWithContent(bookmarksRoot.entityId, firstBatch))
        repository.addToFavouriteFolder(favouritesRoot.entityId, firstBatch.map { it.entityId })
        Assert.assertEquals(firstBatch.map { it.entityId }, savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).map { it.entityId })

        // when adding new ones
        val secondBatch = BookmarkTestUtils.givenSomeBookmarks(15)
        savedSitesRelationsDao.insertList(BookmarkTestUtils.givenFolderWithContent(bookmarksRoot.entityId, secondBatch))
        repository.addToFavouriteFolder(favouritesRoot.entityId, secondBatch.map { it.entityId })

        // then all favourites are present
        Assert.assertEquals(
            firstBatch.map { it.entityId }.plus(secondBatch.map { it.entityId }),
            savedSitesRelationsDao.relationsByFolderId(favouritesRoot.entityId).map { it.entityId },
        )
    }

    @Test
    fun whenMarkSavedSitesIdsAsInvalidThenIdsStored() = runTest {
        val ids = listOf("id1", "id2", "id3")
        repository.markSavedSitesAsInvalid(ids)
        val invalidIds = store.invalidEntitiesIds

        assertTrue(invalidIds.containsAll(ids))
        assertTrue(invalidIds.size == ids.size)
    }

    @Test
    fun whenGetInvalidSavedSitesThenExpectedSavedSitesReturned() = runTest {
        val bookmarks = BookmarkTestUtils.givenSomeBookmarks(10)
        savedSitesEntitiesDao.insertList(bookmarks)
        val ids = bookmarks.map { it.entityId }
        repository.markSavedSitesAsInvalid(ids)
        val invalidIds = repository.getInvalidSavedSites().map { it.id }
        assertTrue(invalidIds.containsAll(ids))
        assertTrue(invalidIds.size == ids.size)
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
        metadataRequest: Boolean = true,
    ) {
        val entities = BookmarkTestUtils.givenSomeBookmarks(children)
        savedSitesEntitiesDao.insertList(entities)

        val relation = BookmarkTestUtils.givenFolderWithContent(folderId, entities)
        savedSitesRelationsDao.insertList(relation)

        if (saveMetadata) {
            val childrenJSON = stringListAdapter.toJson(entities.map { it.entityId })
            val metadata = if (metadataRequest) {
                SavedSitesSyncMetadataEntity(folderId, null, childrenJSON)
            } else {
                SavedSitesSyncMetadataEntity(folderId, childrenJSON, null)
            }
            savedSitesMetadataDao.addOrUpdate(metadata)
        }
    }
}
