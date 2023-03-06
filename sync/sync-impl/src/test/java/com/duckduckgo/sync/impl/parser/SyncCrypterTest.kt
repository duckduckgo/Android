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

package com.duckduckgo.sync.impl.parser

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.sync.TestSyncFixtures
import com.duckduckgo.sync.api.parser.SyncBookmarkPage
import com.duckduckgo.sync.api.parser.SyncCrypter
import com.duckduckgo.sync.api.parser.SyncEntry
import com.duckduckgo.sync.api.parser.SyncFolderChildren
import com.duckduckgo.sync.crypto.EncryptResult
import com.duckduckgo.sync.crypto.SyncLib
import com.duckduckgo.sync.store.SyncStore
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever

class SyncCrypterTest {

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi @get:Rule var coroutinesTestRule = CoroutineTestRule()

    lateinit var syncCrypter: SyncCrypter
    private val repository: SavedSitesRepository = mock()
    private val nativeLib: SyncLib = mock()
    private val store: SyncStore = mock()

    @Before fun before() {
        syncCrypter = RealSyncCrypter(repository, nativeLib, store)

        whenever(repository.getFolder(Relation.BOOMARKS_ROOT)).thenReturn(BookmarkFolder(Relation.BOOMARKS_ROOT, "Bookmarks", "", 0, 0))
        whenever(repository.getFolder(Relation.FAVORITES_ROOT)).thenReturn(BookmarkFolder(Relation.FAVORITES_ROOT, "Favorites", "", 0, 0))
        whenever(store.primaryKey).thenReturn("primaryKey")

        whenever(nativeLib.encrypt(anyString(), anyString()))
            .thenAnswer { invocation -> EncryptResult(result = 0L, encryptedData = invocation.getArgument(0)) }

        whenever(nativeLib.seal(anyString(), anyString()))
            .thenAnswer { invocation -> invocation.getArgument(0) }

        givenSomeFavorites()
        givenSomeBookmarks()
    }

    @Test fun whenUserNotLoggedInThenGeneratedDataIsEmpty() = runTest {
        whenever(repository.getFavoritesSync()).thenReturn(
            listOf(
                aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0),
                aFavorite("bookmark2", "Bookmark 2", "https://bookmark1.com", 1),
                aFavorite("bookmark4", "Bookmark 4", "https://bookmark1.com", 2),
            ),
        )
        whenever(repository.getFolderContentSync(Relation.BOOMARKS_ROOT)).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com"),
                    aBookmark("bookmark2", "Bookmark 2", "https://bookmark1.com"),
                ),
                listOf(aFolder("folder1", "Folder One", Relation.BOOMARKS_ROOT)),
            ),
        )
        whenever(repository.getFolder("folder1")).thenReturn(BookmarkFolder("folder 1", "Folder One", Relation.BOOMARKS_ROOT, 2, 0))
        whenever(repository.getFolderContentSync("folder1")).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com"),
                    aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com"),
                ),
                emptyList(),
            ),
        )

        whenever(store.primaryKey).thenReturn(null)

        val allData = syncCrypter.generateAllData()
        assertTrue(allData.bookmarks.updates.isEmpty())
    }

    @Test fun whenNoSavedSitesAddedThenGeneratedDataIsCorrect() = runTest {
        givenNoFavorites()
        givenNoBookmarks()

        val allData = syncCrypter.generateAllData()
        assertTrue(allData.bookmarks.updates.isEmpty())
    }

    @Test fun whenOnlyBookmarksThenGeneratedDataIsCorrect() = runTest {
        givenNoFavorites()
        whenever(repository.getFolderContentSync(Relation.BOOMARKS_ROOT)).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com"),
                    aBookmark("bookmark2", "Bookmark 2", "https://bookmark1.com"),
                ),
                emptyList(),
            ),
        )

        val allData = syncCrypter.generateAllData()

        assertTrue(allData.bookmarks.updates.size == 3)
        val bookmarksRoot = allData.bookmarks.updates[2]
        assertEquals(bookmarksRoot.id, Relation.BOOMARKS_ROOT)
        assertEquals(bookmarksRoot.folder!!.children.size, 2)

        val folder = allData.bookmarks.updates.first()
        assertEquals(folder.id, "bookmark1")
        assertEquals(folder.page!!.url, "https://bookmark1.com")
    }

    @Test fun whenFolderWithBookmarksThenGeneratedDataIsCorrect() = runTest {
        givenNoFavorites()
        whenever(repository.getFolderContentSync(Relation.BOOMARKS_ROOT)).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com"),
                    aBookmark("bookmark2", "Bookmark 2", "https://bookmark1.com"),
                ),
                listOf(aFolder("folder1", "Folder One", Relation.BOOMARKS_ROOT)),
            ),
        )
        whenever(repository.getFolder("folder1")).thenReturn(BookmarkFolder("folder 1", "Folder One", Relation.BOOMARKS_ROOT, 2, 0))
        whenever(repository.getFolderContentSync("folder1")).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com"),
                    aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com"),
                ),
                emptyList(),
            ),
        )

        val allData = syncCrypter.generateAllData()

        assertTrue(allData.bookmarks.updates.size == 6)
        val bookmarksRoot = allData.bookmarks.updates[2]
        assertEquals(bookmarksRoot.id, Relation.BOOMARKS_ROOT)
        assertEquals(bookmarksRoot.folder!!.children.size, 2)

        val folder = allData.bookmarks.updates.last()
        assertEquals(folder.id, "folder 1")
        assertEquals(folder.folder!!.children.size, 2)
    }

    @Test fun whenFavoritesPresentThenGeneratedDataIsCorrect() = runTest {
        whenever(repository.getFavoritesSync()).thenReturn(
            listOf(
                aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0),
                aFavorite("bookmark2", "Bookmark 2", "https://bookmark1.com", 1),
                aFavorite("bookmark4", "Bookmark 4", "https://bookmark1.com", 2),
            ),
        )
        whenever(repository.getFolderContentSync(Relation.BOOMARKS_ROOT)).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com"),
                    aBookmark("bookmark2", "Bookmark 2", "https://bookmark1.com"),
                ),
                listOf(aFolder("folder1", "Folder One", Relation.BOOMARKS_ROOT)),
            ),
        )
        whenever(repository.getFolder("folder1")).thenReturn(BookmarkFolder("folder 1", "Folder One", Relation.BOOMARKS_ROOT, 2, 0))
        whenever(repository.getFolderContentSync("folder1")).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark3", "Bookmark 3", "https://bookmark3.com"),
                    aBookmark("bookmark4", "Bookmark 4", "https://bookmark4.com"),
                ),
                emptyList(),
            ),
        )

        val allData = syncCrypter.generateAllData()
        assertTrue(allData.bookmarks.updates.size == 7)

        val favoritesRoot = allData.bookmarks.updates.first()
        assertEquals(favoritesRoot.id, Relation.FAVORITES_ROOT)
        assertEquals(favoritesRoot.folder!!.children.size, 3)

        val folder = allData.bookmarks.updates.last()
        assertEquals(folder.id, "folder 1")
        assertEquals(folder.folder!!.children.size, 2)
    }

    @Test fun whenOneBookmarkAndFavouriteThenGeneratedDataIsCorrect() = runTest {
        whenever(repository.getFavoritesSync()).thenReturn(
            listOf(
                aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0),
            ),
        )
        whenever(repository.getFolderContentSync(Relation.BOOMARKS_ROOT)).thenReturn(
            Pair(
                listOf(
                    aBookmark("bookmark1", "Bookmark 1", "https://bookmark1.com"),
                ),
                emptyList(),
            ),
        )

        val allData = syncCrypter.generateAllData()
        assertTrue(allData.bookmarks.updates.size == 3)

        val favoritesRoot = allData.bookmarks.updates.first()
        assertEquals(favoritesRoot.id, Relation.FAVORITES_ROOT)
        assertEquals(favoritesRoot.folder!!.children.size, 1)

        val folder = allData.bookmarks.updates.last()
        assertEquals(folder.id, Relation.BOOMARKS_ROOT)
        assertEquals(folder.folder!!.children.size, 1)
    }

    @Test fun whenNoDataIsFetchedThenNothingIsStored() {
        syncCrypter.store(emptyList())
        verifyNoInteractions(repository)
    }

    @Test
    fun whenBookmarksToStoreThenRepositoryStoresThem() {
        whenever(store.primaryKey).thenReturn(TestSyncFixtures.primaryKey)
        whenever(nativeLib.decrypt(any(), any())).thenReturn(TestSyncFixtures.decryptedSecretKey)

        val entries = givenSomeBookmarkSyncEntries(10, Relation.BOOMARKS_ROOT)
        syncCrypter.store(entries)

        verify(repository, times(1)).insert(any<BookmarkFolder>())
        verify(repository, times(10)).insert(any<Bookmark>())
    }

    private fun givenNoFavorites() {
        whenever(repository.hasFavorites()).thenReturn(false)
    }

    private fun givenNoBookmarks() {
        whenever(repository.hasBookmarks()).thenReturn(false)
    }

    private fun givenSomeFavorites() {
        whenever(repository.hasFavorites()).thenReturn(true)
    }

    private fun givenSomeBookmarks() {
        whenever(repository.hasBookmarks()).thenReturn(true)
    }

    private fun givenSomeBookmarkSyncEntries(
        totalEntries: Int,
        folderId: String
    ): List<SyncEntry> {
        val entries = mutableListOf<SyncEntry>()
        val childrenId = mutableListOf<String>()
        for (index in 1..totalEntries) {
            entries.add(SyncEntry("entity$index", "title$index", SyncBookmarkPage("https://testUrl$index"), null, null))
            childrenId.add("entity$index")
        }
        entries.add(SyncEntry(folderId, "Bookmarks $folderId", null, SyncFolderChildren(childrenId), null))
        return entries
    }

    private fun aFolder(
        id: String,
        name: String,
        parentId: String
    ): BookmarkFolder {
        return BookmarkFolder(id = id, name = name, parentId = parentId)
    }

    private fun aBookmark(
        id: String,
        title: String,
        url: String
    ): Bookmark {
        return Bookmark(id, title, url)
    }

    private fun aFavorite(
        id: String,
        title: String,
        url: String,
        position: Int
    ): Favorite {
        return Favorite(id, title, url, position)
    }
}
