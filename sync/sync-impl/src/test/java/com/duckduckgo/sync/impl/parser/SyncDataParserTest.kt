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

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @ExperimentalCoroutinesApi
    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()

    lateinit var dataParser: SyncDataParser
    private val repository: SavedSitesRepository = mock()

    @Before
    fun before() {
        dataParser = RealSyncDataParser(repository)
    }

    @Test
    fun whenNoSavedSitesAddedThenGeneratedJSONIsCorrect() = runTest {
        whenever(repository.getFolderContentSync(Relation.BOOMARKS_ROOT)).thenReturn(
            Pair(
                emptyList(),
                emptyList(),
            ),
        )

        val json = dataParser.generateInitialList()
        assertEquals("", json)
    }

    @Test
    fun whenOnlyBookmarksThenGeneratedJSONIsCorrect() = runTest {
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
            "{\"bookmarks\":{\"updates\":[{\"id\":\"bookmark1\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 1\"},{\"id\":\"bookmark2\",\"page\":{\"url\":\"https://bookmark1.com\"},\"title\":\"Bookmark 2\"}]}}",
            json,
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
}
