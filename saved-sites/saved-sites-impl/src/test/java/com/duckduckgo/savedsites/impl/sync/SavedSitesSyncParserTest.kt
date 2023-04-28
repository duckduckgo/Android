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

import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.SyncCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SavedSitesSyncParserTest {

    private val repository: SavedSitesRepository = mock()
    private val syncCrypto: SyncCrypto = mock()
    private lateinit var parser: SavedSitesSyncParser

    @Before
    fun before() {
        parser = SavedSitesSyncParser(repository, syncCrypto)

        whenever(syncCrypto.encrypt(ArgumentMatchers.anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }
    }

    @Test
    fun whenFirstSyncAndUserHasNoBookmarksThenChangesAreEmpty() {
        whenever(repository.hasBookmarks()).thenReturn(false)
        whenever(repository.hasFavorites()).thenReturn(false)
        val syncChanges = parser.getChanges("")
        assertTrue(syncChanges.updatesJSON.isEmpty())
    }

    @Test
    fun whenFirstSyncAndUsersHasFavoritesThenChangesAreFormatted() {
        whenever(repository.hasBookmarks()).thenReturn(true)
        whenever(repository.hasFavorites()).thenReturn(true)
        whenever(repository.getFavoritesSync()).thenReturn(listOf(aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0)))
        val favoritesFolder = aFolder(SavedSitesNames.FAVORITES_ROOT, SavedSitesNames.FAVORITES_NAME, "")
        whenever(repository.getFolder(favoritesFolder.id)).thenReturn(favoritesFolder)
        whenever(repository.getFolderContentSync(favoritesFolder.id)).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com"),
                    aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com"),
                ),
                emptyList(),
            ),
        )

        val syncChanges = parser.getChanges("")
        assertEquals(
            syncChanges.updatesJSON,
            "{\"bookmarks\":{\"updates\":[{\"client_last_modified\":\"timestamp\",\"folder\":{\"children\":[\"bookmark1\"]},\"id\":\"folder1\",\"title\":\"Favorites\"}]}}",
        )
    }

    private fun aFolder(
        id: String,
        name: String,
        parentId: String,
    ): BookmarkFolder {
        return BookmarkFolder(id = id, name = name, parentId = parentId, lastModified = "timestamp")
    }

    private fun aFavorite(
        id: String,
        title: String,
        url: String,
        position: Int,
    ): Favorite {
        return Favorite(id, title, url, lastModified = "timestamp", position)
    }

    private fun aBookmark(
        id: String,
        title: String,
        url: String,
    ): Bookmark {
        return Bookmark(id, title, url, lastModified = "timestamp")
    }
}
