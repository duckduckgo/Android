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
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.db.AppDatabase
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.savedsites.impl.RealSavedSitesRepository
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncParser
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncStore
import com.duckduckgo.savedsites.store.SavedSitesEntitiesDao
import com.duckduckgo.savedsites.store.SavedSitesRelationsDao
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.impl.SyncBookmarkEntry
import com.duckduckgo.sync.impl.SyncBookmarkPage
import com.duckduckgo.sync.impl.SyncFolderChildren
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.whenever
import org.threeten.bp.LocalDateTime

@RunWith(AndroidJUnit4::class)
class SavedSitesSyncParserTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    private lateinit var db: AppDatabase
    private lateinit var repository: SavedSitesRepository
    private lateinit var savedSitesEntitiesDao: SavedSitesEntitiesDao
    private lateinit var savedSitesRelationsDao: SavedSitesRelationsDao
    private lateinit var store: FeatureSyncStore

    private lateinit var parser: SavedSitesSyncParser

    private var favoritesFolder = aFolder(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "")
    private var bookmarksRootFolder = aFolder(SavedSitesNames.BOOKMARKS_ROOT, SavedSitesNames.BOOKMARKS_NAME, "")
    private val subFolder = aFolder("1a8736c1-83ff-48ce-9f01-797887455891", "folder", SavedSitesNames.BOOKMARKS_ROOT)
    private val favourite1 = aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0)
    private val bookmark1 = aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com")
    private val bookmark2 = aBookmark("bookmark2", "Bookmark 2", "https://bookmark2.com")
    private val bookmark3 = aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com")
    private val bookmark4 = aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com")

    @Before
    fun before() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        savedSitesEntitiesDao = db.syncEntitiesDao()
        savedSitesRelationsDao = db.syncRelationsDao()

        repository = RealSavedSitesRepository(savedSitesEntitiesDao, savedSitesRelationsDao)
        store = SavedSitesSyncStore(InstrumentationRegistry.getInstrumentation().context)

        parser = SavedSitesSyncParser(repository, store, FakeCrypto())

        favoritesFolder = repository.insert(favoritesFolder)
        bookmarksRootFolder = repository.insert(bookmarksRootFolder)
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
    fun whenFirstSyncAndUsersHasFavoritesThenChangesAreFormatted() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/parser_favourites.json")

        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)

        val syncChanges = parser.getChanges("")
        assertEquals(syncChanges.updatesJSON, updatesJSON)
    }

    @Test
    fun whenFirstSyncAndUsersHasFoldersThenChangesAreFormatted() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/parser_folders.json")

        repository.insert(bookmark3)
        repository.insert(bookmark4)

        val syncChanges = parser.getChanges("")
        assertEquals(syncChanges.updatesJSON, updatesJSON)
    }

    @Test
    fun whenFirstSyncAndUsersHasFavoritesAndSubfoldersThenChangesAreFormatted() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/parser_folders_and_favourites.json")

        repository.insert(bookmark1)
        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)
        repository.insert(subFolder)
        repository.updateBookmark(bookmark1.copy(parentId = subFolder.id), SavedSitesNames.BOOKMARKS_ROOT)
        repository.updateBookmark(bookmark2.copy(parentId = subFolder.id), SavedSitesNames.BOOKMARKS_ROOT)

        val syncChanges = parser.getChanges("")
        assertEquals(syncChanges.updatesJSON, updatesJSON)
    }

    @Test
    fun whenChangesAfterLastSyncInFavoritesThenChangesAreFormatted() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        repository.insert(bookmark3.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark4.copy(lastModified = modificationTimestamp))
        repository.insert(favourite1.copy(lastModified = modificationTimestamp))

        val changes = parser.changesSince(lastSyncTimestamp)

        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == bookmark1.id)
        assertTrue(changes[0].client_last_modified == modificationTimestamp)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[1].id == favoritesFolder.id)
        assertTrue(changes[1].client_last_modified == modificationTimestamp)
        assertTrue(changes[1].deleted == null)
        assertTrue(changes[1].folder!!.children == listOf(bookmark1.id))
        assertTrue(changes[2].id == bookmark3.id)
        assertTrue(changes[2].client_last_modified == modificationTimestamp)
        assertTrue(changes[2].deleted == null)
        assertTrue(changes[3].id == bookmark4.id)
        assertTrue(changes[3].client_last_modified == modificationTimestamp)
        assertTrue(changes[3].deleted == null)
        assertTrue(changes[4].id == bookmark1.id)
        assertTrue(changes[4].client_last_modified == modificationTimestamp)
        assertTrue(changes[4].deleted == null)
        assertTrue(changes[5].id == bookmarksRootFolder.id)
        assertTrue(changes[5].client_last_modified == modificationTimestamp)
        assertTrue(changes[5].folder!!.children == listOf(bookmark3.id, bookmark4.id, bookmark1.id))
        assertTrue(changes[4].deleted == null)
    }

    @Test
    fun whenNoAfterLastSyncAreEmptyThenChangesAreEmpty() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())

        repository.insert(bookmark3.copy(lastModified = modificationTimestamp))
        repository.insert(bookmark4.copy(lastModified = modificationTimestamp))
        repository.insert(favourite1.copy(lastModified = modificationTimestamp))

        val syncChanges = parser.getChanges(lastSyncTimestamp)
        assertTrue(syncChanges.isEmpty())
    }

    @Test
    fun whenBookmarkDeletedAfterLastSyncThenDataIsCorrect() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        val modifiedBookmark3 = bookmark3.copy(lastModified = modificationTimestamp)
        val modifiedBookmark4 = bookmark4.copy(lastModified = modificationTimestamp)

        repository.insert(modifiedBookmark3)
        repository.insert(modifiedBookmark4)
        repository.delete(modifiedBookmark4)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == bookmark3.id)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[1].id == bookmark3.parentId)
        assertTrue(changes[1].deleted == null)
        assertTrue(changes[1].folder!!.children == listOf(bookmark3.id))
        assertTrue(changes[2].id == bookmark4.id)
        assertTrue(changes[2].deleted == "1")
    }

    @Test
    fun whenFolderDeletedAfterLastSyncThenDataIsCorrect() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

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
    fun whenFavouriteDeletedAfterLastSyncThenDataIsCorrect() {
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)
        repository.delete(favourite1)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == SavedSitesNames.FAVORITES_ROOT)
        assertTrue(changes[0].folder!!.children.isEmpty())
    }

    @Test
    fun whenBookmarkAndFavouriteDeletedAfterLastSyncThenDataIsCorrect() {
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        repository.insert(bookmark1)
        repository.insert(favourite1)
        repository.insert(bookmark3)
        repository.insert(bookmark4)
        repository.delete(bookmark1)

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == SavedSitesNames.FAVORITES_ROOT)
        assertTrue(changes[0].folder!!.children.isEmpty())
        assertTrue(changes[1].id == SavedSitesNames.BOOKMARKS_ROOT)
        assertTrue(changes[1].folder!!.children == listOf(bookmark3.id, bookmark4.id))
        assertTrue(changes[2].id == bookmark1.id)
        assertTrue(changes[2].deleted == "1")
    }

    private fun fromSavedSite(savedSite: SavedSite): SyncBookmarkEntry {
        return SyncBookmarkEntry(
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
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = bookmarkFolder.id,
            title = bookmarkFolder.name,
            folder = SyncFolderChildren(children),
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

    private fun aBookmark(
        id: String,
        title: String,
        url: String,
        timestamp: String = "2023-05-10T16:10:32.338Z",
    ): Bookmark {
        return Bookmark(id, title, url, lastModified = timestamp)
    }
}
