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
import com.duckduckgo.sync.api.parser.SyncDataParser
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncDataParserTest {

    @get:Rule @Suppress("unused") var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi @get:Rule var coroutinesTestRule = CoroutineTestRule()

    lateinit var dataParser: SyncDataParser
    private val repository: SavedSitesRepository = mock()

    @Before fun before() {
        dataParser = RealSyncDataParser(repository)
        whenever(repository.getFolder(Relation.BOOMARKS_ROOT)).thenReturn(BookmarkFolder(Relation.BOOMARKS_ROOT, "Bookmarks", "", 0, 0))
        whenever(repository.getFolder(Relation.FAVORITES_ROOT)).thenReturn(BookmarkFolder(Relation.FAVORITES_ROOT, "Favorites", "", 0, 0))

        givenSomeFavorites()
        givenSomeBookmarks()
    }

    @Test fun whenNoSavedSitesAddedThenGeneratedJSONIsCorrect() = runTest {
        givenNoFavorites()
        givenNoBookmarks()

        val json = dataParser.generateInitialList()
        assertEquals("", json)
    }

    @Test fun whenOnlyBookmarksThenGeneratedJSONIsCorrect() = runTest {
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

        val json = dataParser.generateInitialList()
        assertEquals(
            "{\"bookmarks\":{\"updates\":[{\"id\":\"bookmark1\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 1\"},{\"id\":\"bookmark2\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 2\"},{\"folder\":{\"children\":[\"bookmark1\",\"bookmark2\"]},\"id\":\"bookmarks_root\",\"title\":\"Bookmarks\"}]}}",
            json,
        )
    }

    @Test fun whenFolderWithBookmarksThenGeneratedJSONIsCorrect() = runTest {
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

        val json = dataParser.generateInitialList()
        assertEquals(
            "{\"bookmarks\":{\"updates\":[{\"id\":\"bookmark1\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 1\"},{\"id\":\"bookmark2\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 2\"},{\"folder\":{\"children\":[\"bookmark1\",\"bookmark2\"]},\"id\":\"bookmarks_root\",\"title\":\"Bookmarks\"},{\"id\":\"bookmark3\",\"page\":{\"url\":\"https://bookmark3.com\"},\"title\":\"Bookmark 3\"},{\"id\":\"bookmark4\",\"page\":{\"url\":\"https://bookmark4.com\"},\"title\":\"Bookmark 4\"},{\"folder\":{\"children\":[\"bookmark3\",\"bookmark4\"]},\"id\":\"folder 1\",\"title\":\"Folder One\"}]}}",
            json,
        )
    }

    @Test fun whenFavoritesPresentThenGeneratedJSONIsCorrect() = runTest {
        whenever(repository.getFavoritesSync()).thenReturn(
            listOf(
                aFavorite("bookmark1", "Bookmark 1", "https://bookmark1.com", 0),
                aFavorite("bookmark2", "Bookmark 2", "https://bookmark1.com", 1),
                aFavorite("bookmark4", "Bookmark 4", "https://bookmark1.com", 2)
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

        val json = dataParser.generateInitialList()
        assertEquals(
            "{\"bookmarks\":{\"updates\":[{\"folder\":{\"children\":[\"bookmark1\",\"bookmark2\",\"bookmark4\"]},\"id\":\"favorites_root\",\"title\":\"Favorites\"},{\"id\":\"bookmark1\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 1\"},{\"id\":\"bookmark2\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 2\"},{\"folder\":{\"children\":[\"bookmark1\",\"bookmark2\"]},\"id\":\"bookmarks_root\",\"title\":\"Bookmarks\"},{\"id\":\"bookmark3\",\"page\":{\"url\":\"https://bookmark3.com\"},\"title\":\"Bookmark 3\"},{\"id\":\"bookmark4\",\"page\":{\"url\":\"https://bookmark4.com\"},\"title\":\"Bookmark 4\"},{\"folder\":{\"children\":[\"bookmark3\",\"bookmark4\"]},\"id\":\"folder 1\",\"title\":\"Folder One\"}]}}",
            json,
        )
    }

    private fun givenNoFavorites(){
        whenever(repository.hasFavorites()).thenReturn(false)
    }

    private fun givenNoBookmarks(){
        whenever(repository.hasBookmarks()).thenReturn(false)
    }

    private fun givenSomeFavorites(){
        whenever(repository.hasFavorites()).thenReturn(true)
    }

    private fun givenSomeBookmarks(){
        whenever(repository.hasBookmarks()).thenReturn(true)
    }
    private suspend fun givenEmptyFolder(folderId: String){
        whenever(repository.getFolderContentSync(folderId)).thenReturn(
            Pair(
                emptyList(),
                emptyList(),
            ),
        )
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
