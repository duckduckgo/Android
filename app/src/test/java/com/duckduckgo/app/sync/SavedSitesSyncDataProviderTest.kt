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

package com.duckduckgo.app.sync

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.aBookmark
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.createInvalidBookmark
import com.duckduckgo.app.bookmarks.BookmarkTestUtils.createInvalidFolder
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.MissingEntitiesRelationReconciler
import com.duckduckgo.savedsites.impl.RealFavoritesDelegate
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.BookmarksSyncLocalValidationFeature
import com.duckduckgo.savedsites.impl.sync.RealSavedSitesFormFactorSyncMigration
import com.duckduckgo.savedsites.impl.sync.RealSavedSitesSyncStore
import com.duckduckgo.savedsites.impl.sync.RealSyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SavedSitesFormFactorSyncMigration
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncDataProvider
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncDataProvider.Companion.MAX_FOLDER_TITLE_LENGTH
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkPage
import com.duckduckgo.savedsites.impl.sync.SyncBookmarksRequest
import com.duckduckgo.savedsites.impl.sync.SyncFolderChildren
import com.duckduckgo.savedsites.impl.sync.SyncSavedSiteRequestFolder
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SyncSavedSitesRequestEntry
import com.duckduckgo.savedsites.impl.sync.store.RealSavedSitesSyncEntitiesStore
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDao
import com.duckduckgo.savedsites.impl.sync.store.SavedSitesSyncMetadataDatabase
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.sync.api.SyncCrypto
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.*
import java.time.OffsetDateTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncDataProviderTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var savedSitesDatabase: SavedSitesSyncMetadataDatabase
    private lateinit var repository: SavedSitesRepository
    private lateinit var syncRepository: SyncSavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var savedSitesMetadataDao: SavedSitesSyncMetadataDao
    private lateinit var savedSitesFormFactorSyncMigration: SavedSitesFormFactorSyncMigration
    private val syncEntitiesStore = RealSavedSitesSyncEntitiesStore(
        InstrumentationRegistry.getInstrumentation().context,
    )
    private val syncFeatureStore = RealSavedSitesSyncStore(
        InstrumentationRegistry.getInstrumentation().context,
        coroutinesTestRule.testScope,
        coroutinesTestRule.testDispatcherProvider,
    )

    private lateinit var parser: SavedSitesSyncDataProvider

    private var favoritesFolder = aFolder(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "")
    private var bookmarksRootFolder = aFolder(SavedSitesNames.BOOKMARKS_ROOT, SavedSitesNames.BOOKMARKS_NAME, "")
    private val subFolder = aFolder("1a8736c1-83ff-48ce-9f01-797887455891", "folder", SavedSitesNames.BOOKMARKS_ROOT)
    private val favourite1 = aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0)
    private val bookmark1 = aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com")
    private val bookmark2 = aBookmark("bookmark2", "Bookmark 2", "https://bookmark2.com")
    private val bookmark3 = aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com")
    private val bookmark4 = aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com")
    private val invalidBookmark = createInvalidBookmark()
    private val invalidFolder = createInvalidFolder()

    private val threeHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3)
    private val twoHoursAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(2)
    private val oneHourAgo = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesDatabase = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SavedSitesSyncMetadataDatabase::class.java,
        )
            .allowMainThreadQueries()
            .build()
        savedSitesMetadataDao = savedSitesDatabase.syncMetadataDao()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        val savedSitesSettingsRepository = FakeDisplayModeSettingsRepository()
        val favoritesDelegate = RealFavoritesDelegate(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            savedSitesSettingsRepository,
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )

        syncRepository = RealSyncSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao, savedSitesMetadataDao, syncEntitiesStore)
        repository = RealSavedSitesRepository(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            favoritesDelegate,
            MissingEntitiesRelationReconciler(savedSitesEntitiesDao),
            coroutinesTestRule.testDispatcherProvider,
        )
        val featureFlag = mock<BookmarksSyncLocalValidationFeature>().apply {
            val enabledToggle = mock<Toggle>().apply {
                whenever(isEnabled()).thenReturn(true)
            }
            whenever(self()).thenReturn(enabledToggle)
        }

        savedSitesFormFactorSyncMigration = RealSavedSitesFormFactorSyncMigration(
            savedSitesEntitiesDao,
            savedSitesRelationsDao,
            savedSitesSettingsRepository,
        )
        parser = SavedSitesSyncDataProvider(
            repository,
            syncRepository,
            syncFeatureStore,
            FakeCrypto(),
            savedSitesFormFactorSyncMigration,
            featureFlag,
        )

        favoritesFolder = repository.insert(favoritesFolder)
        bookmarksRootFolder = repository.insert(bookmarksRootFolder)
    }

    @After
    fun after() {
        db.close()
    }

    @Test
    fun whenGettingAllContentAndUserHasNoBookmarksThenChangesAreEmpty() {
        val syncChanges = parser.allContent()
        assertTrue(syncChanges.isEmpty())
    }

    @Test
    fun whenGettingAllContentAndUsersHasFavoritesThenChangesAreNotEmpty() {
        repository.insert(favourite1)
        repository.insert(bookmark1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)

        val expectedContent = listOf(
            fromBookmarkFolder(favoritesFolder, listOf(bookmark1).map { it.id }),
            fromSavedSite(bookmark1),
            fromSavedSite(bookmark3),
            fromSavedSite(bookmark4),
            fromBookmarkFolder(bookmarksRootFolder, listOf(bookmark1, bookmark3, bookmark4).map { it.id }),
        ).map { it.id }

        val syncChanges = parser.allContent().map { it.id }
        assertEquals(syncChanges, expectedContent)
    }

    @Test
    fun whenFirstSyncAndUsersHasFavoritesThenFormFactorFolderPresent() {
        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 6)
        assertTrue(changes.bookmarks.updates[0].id == "favorites_root")
        assertTrue(changes.bookmarks.updates[0].folder!!.children.current == listOf(favourite1.id))
        assertTrue(changes.bookmarks.updates[1].id == "mobile_favorites_root")
        assertTrue(changes.bookmarks.updates[1].folder!!.children.current == listOf(favourite1.id))
        assertTrue(changes.bookmarks.updates[2].id == "bookmark1")
        assertTrue(changes.bookmarks.updates[3].id == "bookmark3")
        assertTrue(changes.bookmarks.updates[4].id == "bookmark4")
        assertTrue(changes.bookmarks.updates[5].id == "bookmarks_root")
    }

    @Test
    fun whenNewBookmarksSinceLastSyncThenChangesContainData() {
        repository.insert(bookmark3)
        repository.insert(bookmark4)

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 3)
        assertTrue(changes.bookmarks.updates[0].id == "bookmark3")
        assertTrue(changes.bookmarks.updates[1].id == "bookmark4")
        assertTrue(changes.bookmarks.updates[2].id == "bookmarks_root")
    }

    @Test
    fun whenOnFirstSyncBookmarkIsInvalidThenChangesDoesNotContainInvalidEntity() {
        repository.insert(invalidBookmark)

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 1)
        assertTrue(changes.bookmarks.updates[0].id == "bookmarks_root")
        assertTrue(changes.bookmarks.updates[0].folder?.children?.current?.contains(invalidBookmark.id) ?: false)
    }

    @Test
    fun whenOnFirstSyncFolderIsInvalidThenChangesContainDataFixed() {
        repository.insert(invalidBookmark)
        repository.insert(invalidFolder)

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 2)
        assertTrue(changes.bookmarks.updates[0].id == invalidFolder.id)
        assertTrue(changes.bookmarks.updates[0].title?.length == MAX_FOLDER_TITLE_LENGTH)
        assertTrue(changes.bookmarks.updates[1].id == "bookmarks_root")
        assertTrue(changes.bookmarks.updates[1].folder?.children?.current?.contains(invalidFolder.id) ?: false)
    }

    @Test
    fun whenNewBookmarkIsInvalidThenChangesDoesNotContainInvalidEntity() {
        setLastSyncTime(DatabaseDateFormatter.iso8601(twoHoursAgo))
        repository.insert(invalidBookmark.copy(lastModified = DatabaseDateFormatter.iso8601(oneHourAgo)))

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 1)
        assertTrue(changes.bookmarks.updates[0].id == "bookmarks_root")
        assertTrue(changes.bookmarks.updates[0].folder?.children?.current?.contains(invalidBookmark.id) ?: false)
    }

    @Test
    fun whenNewFolderIsInvalidThenChangesContainDataFixed() {
        setLastSyncTime(DatabaseDateFormatter.iso8601(twoHoursAgo))
        repository.insert(invalidBookmark.copy(lastModified = DatabaseDateFormatter.iso8601(oneHourAgo)))
        repository.insert(invalidFolder.copy(lastModified = DatabaseDateFormatter.iso8601(oneHourAgo)))

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 2)
        assertTrue(changes.bookmarks.updates.find { it.id == invalidFolder.id }!!.title?.length == MAX_FOLDER_TITLE_LENGTH)
        assertTrue(changes.bookmarks.updates.find { it.id == "bookmarks_root" }!!.folder?.children?.current?.contains(invalidFolder.id) ?: false)
    }

    @Test
    fun whenInvalidBookmarkPresentAndValidBookmarkAddedThenOnlyValidBookmarkIncluded() {
        repository.insert(invalidBookmark)
        syncRepository.markSavedSitesAsInvalid(listOf(invalidBookmark.id))
        setLastSyncTime(DatabaseDateFormatter.iso8601(twoHoursAgo))
        repository.insert(bookmark1.copy(lastModified = DatabaseDateFormatter.iso8601(oneHourAgo)))

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 2)
        assertTrue(changes.bookmarks.updates.find { it.id == "bookmarks_root" }!!.folder?.children?.current?.contains(invalidBookmark.id) ?: false)
        assertTrue(changes.bookmarks.updates.find { it.id == "bookmarks_root" }!!.folder?.children?.current?.contains(bookmark1.id) ?: false)
        assertTrue(changes.bookmarks.updates.find { it.id == bookmark1.id } != null)
        assertTrue(syncRepository.getInvalidSavedSites().size == 1)
    }

    @Test
    fun whenNewFoldersAndBookmarksAndFavouritesSinceLastSyncThenChangesContainData() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601()
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastSyncTimestamp)
        repository.insert(bookmark1.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark2.copy(lastModified = modificationTimestamp))
        repository.insert(favourite1.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark3.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark4.copy(lastModified = modificationTimestamp))
        repository.insert(subFolder.copy(lastModified = modificationTimestamp))
        repository.updateBookmark(bookmark1.copy(parentId = subFolder.id), SavedSitesNames.BOOKMARKS_ROOT)
        repository.updateBookmark(bookmark2.copy(parentId = subFolder.id), SavedSitesNames.BOOKMARKS_ROOT)

        val syncChanges = parser.getChanges()

        val changes = Adapters.adapter.fromJson(syncChanges.jsonString)!!
        assertTrue(changes.bookmarks.updates.size == 7)
        assertTrue(changes.bookmarks.updates[0].id == "favorites_root")
        assertTrue(changes.bookmarks.updates[1].id == "bookmarks_root")
        assertTrue(changes.bookmarks.updates[1].folder!!.children.current == listOf("bookmark3", "bookmark4", "1a8736c1-83ff-48ce-9f01-797887455891"))
        assertTrue(changes.bookmarks.updates[2].id == "1a8736c1-83ff-48ce-9f01-797887455891")
        assertTrue(changes.bookmarks.updates[2].folder!!.children.current == listOf("bookmark1", "bookmark2"))
        assertTrue(changes.bookmarks.updates[3].id == "bookmark1")
        assertTrue(changes.bookmarks.updates[4].id == "bookmark2")
        assertTrue(changes.bookmarks.updates[5].id == "bookmark3")
        assertTrue(changes.bookmarks.updates[6].id == "bookmark4")
    }

    @Test
    fun whenNewFavouritesSinceLastSyncThenChangesContainData() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601()
        val lastServerSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastServerSyncTimestamp, modificationTimestamp)

        repository.insert(bookmark3.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark4.copy(lastModified = modificationTimestamp))
        repository.insert(favourite1.copy(lastModified = modificationTimestamp))

        val changes = parser.changesSince(lastServerSyncTimestamp)

        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == favoritesFolder.id)
        assertTrue(changes[0].client_last_modified == modificationTimestamp)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[0].folder!!.children.current == listOf(bookmark1.id))
        assertTrue(changes[1].id == bookmarksRootFolder.id)
        assertTrue(changes[1].client_last_modified == modificationTimestamp)
        assertTrue(changes[1].folder!!.children.current == listOf(bookmark3.id, bookmark4.id, bookmark1.id))
        assertTrue(changes[2].id == bookmark3.id)
        assertTrue(changes[2].client_last_modified == modificationTimestamp)
        assertTrue(changes[2].deleted == null)
        assertTrue(changes[3].id == bookmark4.id)
        assertTrue(changes[3].client_last_modified == modificationTimestamp)
        assertTrue(changes[3].deleted == null)
        assertTrue(changes[4].id == favourite1.id)
        assertTrue(changes[4].deleted == null)
    }

    @Test
    fun whenNoChangesAfterLastSyncThenChangesAreEmpty() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601()
        setLastSyncTime(lastSyncTimestamp)

        repository.insert(bookmark3.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark4.copy(lastModified = modificationTimestamp))
        repository.insert(favourite1.copy(lastModified = modificationTimestamp))

        val syncChanges = parser.getChanges()
        assertTrue(syncChanges.isEmpty())
    }

    @Test
    fun whenBookmarkDeletedAfterLastSyncThenChangesContainData() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601()
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastSyncTimestamp)

        val modifiedBookmark3 = bookmark3.copy(lastModified = modificationTimestamp)
        val modifiedBookmark4 = bookmark4.copy(lastModified = modificationTimestamp)

        repository.insert(modifiedBookmark3)
        repository.insert(modifiedBookmark4)
        repository.delete(modifiedBookmark4)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == bookmarksRootFolder.id)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[0].folder!!.children.current == listOf(bookmark3.id))
        assertTrue(changes[1].id == bookmark3.id)
        assertTrue(changes[1].deleted == null)
        assertTrue(changes[2].id == bookmark4.id)
        assertTrue(changes[2].deleted == "1")
    }

    @Test
    fun whenFolderDeletedAfterLastSyncThenChangesContainData() {
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastSyncTimestamp)

        repository.insert(bookmark1)
        repository.insert(bookmark2)
        repository.insert(subFolder)
        repository.insert(bookmark3)
        repository.insert(bookmark4)
        repository.updateBookmark(bookmark3.copy(parentId = subFolder.id), SavedSitesNames.BOOKMARKS_ROOT)
        repository.updateBookmark(bookmark4.copy(parentId = subFolder.id), SavedSitesNames.BOOKMARKS_ROOT)
        repository.delete(subFolder)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == bookmarksRootFolder.id)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[1].id == subFolder.id)
        assertTrue(changes[1].deleted == "1")
        assertTrue(changes[2].id == bookmark3.id)
        assertTrue(changes[2].deleted == "1")
        assertTrue(changes[3].id == bookmark4.id)
        assertTrue(changes[3].deleted == "1")
    }

    @Test
    fun whenFavouriteDeletedAfterLastSyncThenChangesContainData() {
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastSyncTimestamp)

        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)
        repository.delete(favourite1)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == SavedSitesNames.FAVORITES_ROOT)
        assertTrue(changes[0].folder!!.children.current.isEmpty())
    }

    @Test
    fun whenFavouritesAndBookmarksDeletedAfterLastSyncThenChangesContainData() {
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastSyncTimestamp)

        repository.insert(bookmark1)
        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)
        repository.delete(bookmark1)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == SavedSitesNames.FAVORITES_ROOT)
        assertTrue(changes[0].folder!!.children.current.isEmpty())
        assertTrue(changes[1].id == SavedSitesNames.BOOKMARKS_ROOT)
        assertTrue(changes[1].folder!!.children.current == listOf(bookmark3.id, bookmark4.id))
        assertTrue(changes[2].id == bookmark1.id)
        assertTrue(changes[2].deleted == "1")
    }

    @Test
    fun whenFolderMovedToAnotherFolderAfterLastSyncThenChangesContainData() {
        val beforeLastSyncTimestamp = DatabaseDateFormatter.iso8601(threeHoursAgo)
        val modificationTimestamp = DatabaseDateFormatter.iso8601(oneHourAgo)
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(twoHoursAgo)
        setLastSyncTime(lastSyncTimestamp)

        val modifiedBookmark1 = bookmark1.copy(lastModified = beforeLastSyncTimestamp, parentId = subFolder.id)
        val modifiedFolder = subFolder.copy(lastModified = modificationTimestamp)

        repository.insert(modifiedBookmark1)
        repository.insert(modifiedFolder)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes.size == 2)
        assertTrue(changes[0].id == bookmarksRootFolder.id)
        assertTrue(changes[0].folder!!.children.current == listOf(subFolder.id))
        assertTrue(changes[1].id == subFolder.id)
        assertTrue(changes[1].folder!!.children.current == listOf(bookmark1.id))
    }

    @Test
    fun whenNoChangesNeedSyncThenChildrenRequestIsNotAddedToMetadata() {
        val syncChanges = parser.allContent()
        assertTrue(syncChanges.isEmpty())

        assertTrue(savedSitesMetadataDao.all().isEmpty())
    }

    @Test
    fun whenChangesNeedSyncThenChildrenRequestIsAddedToMetadata() {
        repository.insert(bookmark3)
        repository.insert(bookmark4)

        parser.getChanges()

        assertTrue(savedSitesMetadataDao.all().isNotEmpty())
    }

    private fun setLastSyncTime(
        lastServerSyncTimestamp: String,
        lastClientSyncTimestmp: String = lastServerSyncTimestamp,
    ) {
        syncFeatureStore.serverModifiedSince = lastServerSyncTimestamp
        syncFeatureStore.clientModifiedSince = lastClientSyncTimestmp
    }

    private fun fromSavedSite(savedSite: SavedSite): SyncSavedSitesRequestEntry {
        return SyncSavedSitesRequestEntry(
            id = savedSite.id,
            title = savedSite.title,
            page = SyncBookmarkPage(savedSite.url),
            folder = null,
            deleted = null,
            client_last_modified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun fromBookmarkFolder(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): SyncSavedSitesRequestEntry {
        return SyncSavedSitesRequestEntry(
            id = bookmarkFolder.id,
            title = bookmarkFolder.name,
            folder = SyncSavedSiteRequestFolder(SyncFolderChildren(current = children, insert = children, remove = emptyList())),
            page = null,
            deleted = null,
            client_last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun aFolder(
        id: String,
        name: String,
        parentId: String,
        timestamp: String = "2023-05-10T16:10:32.338Z",
    ): BookmarkFolder {
        return BookmarkFolder(id = id, name = name, parentId = parentId, lastModified = timestamp)
    }

    private fun aFavorite(
        id: String,
        title: String,
        url: String,
        position: Int,
        timestamp: String = "2023-05-10T16:10:32.338Z",
    ): Favorite {
        return Favorite(id, title, url, lastModified = timestamp, position)
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val adapter: JsonAdapter<SyncBookmarksRequest> =
                moshi.adapter(SyncBookmarksRequest::class.java)
        }
    }
}

class FakeCrypto : SyncCrypto {
    override fun encrypt(text: String): String {
        return text
    }

    override fun decrypt(data: String): String {
        return data
    }
}
