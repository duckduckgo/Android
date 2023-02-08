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

package com.duckduckgo.app.bookmarks.mapper

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.duckduckgo.app.FileUtilities
import com.duckduckgo.app.bookmarks.db.BookmarkEntity
import com.duckduckgo.app.bookmarks.db.BookmarkFolderEntity
import com.duckduckgo.app.bookmarks.model.BookmarkFolder
import com.duckduckgo.app.bookmarks.model.BookmarksRepository
import com.duckduckgo.app.bookmarks.model.SavedSite
import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.bookmarks.service.RealSavedSitesParser
import com.duckduckgo.sync.store.EntityType.BOOKMARK
import com.duckduckgo.sync.store.EntityType.FOLDER
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncMapperTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var mapper: SyncMapper

    @Before
    fun before(){
        mapper = AppSyncMapper()
    }

    @Test
    fun whenBookmarksAreMappedThenEntitiesAreCreated() = runTest {
        val firstBookmark = Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 0)
        val secondBookmark = Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 0)

        val bookmarks = listOf(firstBookmark, secondBookmark)

        val mapBookmarks = mapper.mapEntities(bookmarks, emptyList() , emptyList())
        Assert.assertEquals(2, mapBookmarks.size)
        Assert.assertEquals(2, mapBookmarks.filter { it.type == BOOKMARK }.size)
        Assert.assertEquals(0, mapBookmarks.filter { it.type == FOLDER }.size)
    }

    @Test
    fun whenBookmarksAndFoldersAreMappedThenEntitiesAreCreated() = runTest {
        val rootFolder = BookmarkFolder(id = 1, name = "name", parentId = 0, numFolders =  1)
        val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1, numBookmarks = 2, numFolders = 0)
        val firstBookmark = Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 1)
        val secondBookmark = Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 1)

        val bookmarks = listOf(firstBookmark, secondBookmark)
        val folders = listOf(rootFolder, childFolder)

        val mapBookmarks = mapper.mapEntities(bookmarks, folders , emptyList())
        Assert.assertEquals(4, mapBookmarks.size)
        Assert.assertEquals(2, mapBookmarks.filter { it.type == BOOKMARK }.size)
        Assert.assertEquals(2, mapBookmarks.filter { it.type == FOLDER }.size)
    }

    @Test
    fun whenBookmarksFoldersAndFavoritesAreMappedThenEntitiesAreCreated() = runTest {
        val rootFolder = BookmarkFolder(id = 1, name = "name", parentId = 0, numFolders =  1)
        val childFolder = BookmarkFolder(id = 2, name = "another name", parentId = 1, numBookmarks = 2, numFolders = 0)
        val firstBookmark = Bookmark(id = 1, title = "title", url = "www.website.com", parentId = 1)
        val secondBookmark = Bookmark(id = 2, title = "other title", url = "www.other-website.com", parentId = 1)
        val firstFavourite = Favorite(id = 1, title = "title", url = "www.website.com", position = 0)
        val secondFavourite = Favorite(id = 2, title = "other title", url = "www.other-website.com", position = 1)

        val bookmarks = listOf(firstBookmark, secondBookmark)
        val folders = listOf(rootFolder, childFolder)
        val favourites = listOf(firstFavourite, secondFavourite)

        val mapBookmarks = mapper.mapEntities(bookmarks, folders , favourites)
        Assert.assertEquals(6, mapBookmarks.size)
        Assert.assertEquals(4, mapBookmarks.filter { it.type == BOOKMARK }.size)
        Assert.assertEquals(2, mapBookmarks.filter { it.type == FOLDER }.size)
    }

}

