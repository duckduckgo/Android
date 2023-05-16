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

package com.duckduckgo.savedsites.impl.sync

import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.threeten.bp.LocalDateTime

class SavedSitesSyncParserTest {

    private val repository: SavedSitesRepository = mock()
    private val syncCrypto: SyncCrypto = mock()
    private val store: FeatureSyncStore = mock()
    private lateinit var parser: SavedSitesSyncParser

    private val favoritesFolder = aFolder(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "")
    private val bookmarksRootFolder = aFolder(SavedSitesNames.BOOKMARKS_ROOT, SavedSitesNames.BOOKMARKS_NAME, "")
    private val subFolder = aFolder("1a8736c1-83ff-48ce-9f01-797887455891", "folder", SavedSitesNames.BOOKMARKS_ROOT)
    private val favourite = aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0)
    private val bookmark1 = aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com")
    private val bookmark2 = aBookmark("bookmark2", "Bookmark 2", "https://bookmark2.com")
    private val bookmark3 = aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com")
    private val bookmark4 = aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com")

    @Before
    fun before() {
        parser = SavedSitesSyncParser(repository, store, syncCrypto)

        whenever(syncCrypto.encrypt(ArgumentMatchers.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        whenever(store.modifiedSince).thenReturn("0")
    }

    @Test
    fun whenGettingAllContentAndUserHasNoBookmarksThenChangesAreEmpty() {
        whenever(repository.hasBookmarks()).thenReturn(false)
        whenever(repository.hasFavorites()).thenReturn(false)
        val syncChanges = parser.allContent()
        assertTrue(syncChanges.isEmpty())
    }

    @Test
    fun whenGettingAllContentAndUsersHasFavoritesThenChangesAreNotEmpty() {
        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(true)
        whenever(repository.getFolder(favoritesFolder.id)).thenReturn(favoritesFolder)
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(bookmarksRootFolder)
        whenever(repository.getFavoritesSync()).thenReturn(listOf(favourite))
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(bookmark1, bookmark3, bookmark4),
                emptyList(),
            ),
        )

        val expectedContent = listOf(
            fromBookmarkFolder(favoritesFolder, listOf(bookmark1).map { it.id }),
            fromSavedSite(bookmark1),
            fromSavedSite(bookmark3),
            fromSavedSite(bookmark4),
            fromBookmarkFolder(bookmarksRootFolder, listOf(bookmark1, bookmark3, bookmark4).map { it.id }),
        )

        val syncChanges = parser.allContent()
        assertEquals(syncChanges, expectedContent)
    }

    @Test
    fun whenFirstSyncAndUsersHasFavoritesThenChangesAreFormatted() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/parser_favourites.json")

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(true)
        whenever(repository.getFolder(favoritesFolder.id)).thenReturn(favoritesFolder)
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(bookmarksRootFolder)
        whenever(repository.getFavoritesSync()).thenReturn(listOf(favourite))
        whenever(repository.getAllFolderContentSync(favoritesFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3, bookmark4),
                emptyList(),
            ),
        )
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3, bookmark4),
                emptyList(),
            ),
        )

        val syncChanges = parser.getChanges("")
        assertEquals(syncChanges.updatesJSON, updatesJSON)
    }

    @Test
    fun whenFirstSyncAndUsersHasFoldersThenChangesAreFormatted() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/parser_folders.json")

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(false)
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(bookmarksRootFolder)
        whenever(repository.getFavoritesSync()).thenReturn(listOf(favourite))
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(listOf(bookmark3, bookmark4), emptyList()),
        )

        val syncChanges = parser.getChanges("")
        assertEquals(syncChanges.updatesJSON, updatesJSON)
    }

    @Test
    fun whenFirstSyncAndUsersHasFavoritesAndSubfoldersThenChangesAreFormatted() {
        val updatesJSON = FileUtilities.loadText(javaClass.classLoader!!, "json/parser_folders_and_favourites.json")

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(true)
        whenever(repository.getFolder(favoritesFolder.id)).thenReturn(favoritesFolder)
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(bookmarksRootFolder)
        whenever(repository.getFolder(subFolder.id)).thenReturn(subFolder)
        whenever(repository.getFavoritesSync()).thenReturn(listOf(favourite))
        whenever(repository.getAllFolderContentSync(favoritesFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3, bookmark4),
                emptyList(),
            ),
        )
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3, bookmark4),
                listOf(subFolder),
            ),
        )
        whenever(repository.getAllFolderContentSync(subFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3, bookmark4),
                emptyList(),
            ),
        )

        val syncChanges = parser.getChanges("")
        assertEquals(syncChanges.updatesJSON, updatesJSON)
    }

    @Test
    fun whenChangesAfterLastSyncInFavoritesThenChangesAreFormatted() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(true)
        whenever(repository.getFolder(favoritesFolder.id)).thenReturn(favoritesFolder.copy(lastModified = modificationTimestamp))
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(bookmarksRootFolder.copy(lastModified = modificationTimestamp))
        whenever(repository.getFoldersModifiedSince(lastSyncTimestamp)).thenReturn(listOf(bookmarksRootFolder, favoritesFolder))
        whenever(repository.getFavoritesSync()).thenReturn(
            listOf(favourite.copy(lastModified = modificationTimestamp)),
        )
        whenever(repository.getAllFolderContentSync(favoritesFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3.copy(lastModified = modificationTimestamp), bookmark4.copy(lastModified = modificationTimestamp)),
                emptyList(),
            ),
        )
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3.copy(lastModified = modificationTimestamp), bookmark4.copy(lastModified = modificationTimestamp)),
                emptyList(),
            ),
        )

        val changes = parser.changesSince(lastSyncTimestamp)

        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == bookmark3.id)
        assertTrue(changes[0].client_last_modified == modificationTimestamp)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[1].id == bookmark4.id)
        assertTrue(changes[1].client_last_modified == modificationTimestamp)
        assertTrue(changes[1].deleted == null)
        assertTrue(changes[2].id == bookmarksRootFolder.id)
        assertTrue(changes[2].client_last_modified == modificationTimestamp)
        assertTrue(changes[2].folder!!.children == listOf(bookmark3.id, bookmark4.id))
        assertTrue(changes[2].folder!!.children == listOf(bookmark3.id, bookmark4.id))
        assertTrue(changes[5].id == favoritesFolder.id)
        assertTrue(changes[5].client_last_modified == modificationTimestamp)
        assertTrue(changes[5].deleted == null)
        assertTrue(changes[5].folder!!.children == listOf(bookmark3.id, bookmark4.id))
    }

    @Test
    fun whenNoAfterLastSyncAreEmptyThenChangesAreEmpty() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(true)
        whenever(repository.getFolder(favoritesFolder.id)).thenReturn(favoritesFolder.copy(lastModified = modificationTimestamp))
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(bookmarksRootFolder.copy(lastModified = modificationTimestamp))
        whenever(repository.getFavoritesSync()).thenReturn(
            listOf(favourite.copy(lastModified = modificationTimestamp)),
        )
        whenever(repository.getAllFolderContentSync(favoritesFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3.copy(lastModified = modificationTimestamp), bookmark4.copy(lastModified = modificationTimestamp)),
                emptyList(),
            ),
        )
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(bookmark3.copy(lastModified = modificationTimestamp), bookmark4.copy(lastModified = modificationTimestamp)),
                emptyList(),
            ),
        )

        val syncChanges = parser.getChanges(lastSyncTimestamp)
        assertTrue(syncChanges.isEmpty())
    }

    @Test
    fun whenBookmarkDeletedAfterLastSyncThenDataIsCorrect() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        val modifiedFolder = bookmarksRootFolder.copy(lastModified = modificationTimestamp)

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(false)
        whenever(repository.getFoldersModifiedSince(lastSyncTimestamp)).thenReturn(listOf(modifiedFolder))
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(modifiedFolder)
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(
                    bookmark3.copy(lastModified = modificationTimestamp),
                    bookmark4.copy(lastModified = modificationTimestamp, deleted = modificationTimestamp),
                ),
                emptyList(),
            ),
        )

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == bookmark3.id)
        assertTrue(changes[0].deleted == null)
        assertTrue(changes[1].id == bookmark4.id)
        assertTrue(changes[1].deleted == modificationTimestamp)
    }

    @Test
    fun whenFolderDeletedAfterLastSyncThenDataIsCorrect() {
        val modificationTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now())
        val lastSyncTimestamp = DatabaseDateFormatter.iso8601(LocalDateTime.now().minusHours(2))

        val modifiedRootFolder = bookmarksRootFolder.copy(lastModified = modificationTimestamp)
        val modifiedSubFolder = subFolder.copy(lastModified = modificationTimestamp, deleted = modificationTimestamp)

        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(false)
        whenever(repository.getFoldersModifiedSince(lastSyncTimestamp)).thenReturn(listOf(modifiedRootFolder, modifiedSubFolder))
        whenever(repository.getFolder(bookmarksRootFolder.id)).thenReturn(modifiedRootFolder)
        whenever(repository.getFolder(subFolder.id)).thenReturn(modifiedSubFolder)
        whenever(repository.getAllFolderContentSync(bookmarksRootFolder.id)).thenReturn(
            Pair(
                listOf(
                    bookmark1,
                    bookmark2,
                ),
                listOf(modifiedSubFolder),
            ),
        )
        whenever(repository.getAllFolderContentSync(subFolder.id)).thenReturn(
            Pair(
                listOf(
                    bookmark3.copy(lastModified = modificationTimestamp),
                    bookmark4.copy(lastModified = modificationTimestamp, deleted = modificationTimestamp),
                ),
                emptyList(),
            ),
        )

        val changes = parser.changesSince(lastSyncTimestamp)
        assertTrue(changes.isNotEmpty())
        assertTrue(changes[0].id == subFolder.id)
        assertTrue(changes[0].deleted == modificationTimestamp)
        assertTrue(changes[1].id == bookmarksRootFolder.id)
        assertTrue(changes[1].client_last_modified == modificationTimestamp)
        assertTrue(changes[1].deleted == null)
    }

    private fun fromSavedSite(savedSite: SavedSite): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = savedSite.id,
            title = syncCrypto.encrypt(savedSite.title),
            page = SyncBookmarkPage(syncCrypto.encrypt(savedSite.url)),
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
            title = syncCrypto.encrypt(bookmarkFolder.name),
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
