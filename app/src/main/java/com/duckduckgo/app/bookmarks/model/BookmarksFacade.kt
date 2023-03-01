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

import com.duckduckgo.app.bookmarks.model.SavedSite.Bookmark
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.dev.settings.db.DevSettingsDataStore
import kotlinx.coroutines.flow.Flow

interface BookmarksFacade {

    fun bookmark(url: String): Bookmark?
    suspend fun hasBookmarks(): Boolean
    fun bookmarks(): Flow<List<Bookmark>>
    suspend fun fetchBookmarksAndFolders(parentId: Long?): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    suspend fun insertBookmark(url: String, title: String): Bookmark

    suspend fun insertFavorite(url: String, title: String): Favorite

    suspend fun insert(bookmark: Bookmark)
    suspend fun insert(bookmarkFolder: BookmarkFolder)
    suspend fun delete(bookmark: Bookmark)
    suspend fun update(bookmark: Bookmark)

    suspend fun getBookmarkFolderByParentId(parentId: Long): BookmarkFolder?

    suspend fun update(bookmarkFolder: BookmarkFolder)

    suspend fun insertFolderBranch(branchToInsert: BookmarkFolderBranch)
    suspend fun deleteFolderBranch(bookmarkFolder: BookmarkFolder): BookmarkFolderBranch

    fun favorites(): Flow<List<Favorite>>

    fun favorite(url: String): Favorite?

    suspend fun insert(favorite: Favorite)
    suspend fun delete(favorite: Favorite)
    suspend fun update(favorite: Favorite)

    fun updateWithPosition(favorites: List<SavedSite.Favorite>)
}

class RealBookmarksFacade(
    val devSettingsDataStore: DevSettingsDataStore,
    val bookmarksRepository: BookmarksRepository,
    val favoritesRepository: FavoritesRepository,
    val savedSitesRepository: SavedSitesRepository,
) : BookmarksFacade {

    override fun bookmark(url: String): Bookmark? {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.getBookmark(url)
        } else {
            bookmarksRepository.getBookmark(url)
        }
    }

    override suspend fun hasBookmarks(): Boolean {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.hasBookmarks()
        } else {
            bookmarksRepository.hasBookmarks()
        }
    }

    override fun bookmarks(): Flow<List<Bookmark>> {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.getBookmarks()
        } else {
            bookmarksRepository.bookmarks()
        }
    }

    override suspend fun fetchBookmarksAndFolders(parentId: Long?): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>> {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.getFolderContent(parentId)
        } else {
            bookmarksRepository.fetchBookmarksAndFolders(parentId)
        }
    }

    override suspend fun insert(bookmark: Bookmark) {
        bookmarksRepository.insert(bookmark)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.insert(bookmark)
        }
    }

    override suspend fun insertBookmark(
        url: String,
        title: String,
    ): Bookmark {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.insertBookmark(url, title)
        } else {
            bookmarksRepository.insert(url, title)
        }
    }

    override suspend fun insertFavorite(
        url: String,
        title: String,
    ): Favorite {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.insertFavorite(url, title)
        } else {
            favoritesRepository.insert(url, title)
        }
    }

    override suspend fun insert(favorite: Favorite) {
        favoritesRepository.insert(favorite)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.insert(favorite)
        }
    }

    override suspend fun delete(bookmark: Bookmark) {
        bookmarksRepository.delete(bookmark)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.delete(bookmark)
        }
    }

    override suspend fun delete(favorite: Favorite) {
        favoritesRepository.delete(favorite)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.delete(favorite)
        }
    }

    override suspend fun update(bookmark: Bookmark) {
        bookmarksRepository.update(bookmark)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.update(bookmark)
        }
    }

    override suspend fun update(bookmarkFolder: BookmarkFolder) {
        bookmarksRepository.update(bookmarkFolder)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.update(bookmarkFolder)
        }
    }

    override suspend fun getBookmarkFolderByParentId(parentId: Long): BookmarkFolder? {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.getFolder(parentId)
        } else {
            bookmarksRepository.getBookmarkFolderByParentId(parentId)
        }
    }

    override suspend fun update(favorite: Favorite) {
        favoritesRepository.update(favorite)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.update(favorite)
        }
    }

    override suspend fun insertFolderBranch(branchToInsert: BookmarkFolderBranch) {
        if (devSettingsDataStore.syncBookmarksEnabled) {
            bookmarksRepository.insertFolderBranch(branchToInsert)
        } else {
            bookmarksRepository.insertFolderBranch(branchToInsert)
        }
    }

    override suspend fun insert(bookmarkFolder: BookmarkFolder) {
        bookmarksRepository.insert(bookmarkFolder)
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.insert(bookmarkFolder)
        }
    }

    // this will need to return a different entity than BookmarkFolderBranch
    // used when undo deleting a folder, so we have to pass back a Relation + Entities
    override suspend fun deleteFolderBranch(bookmarkFolder: BookmarkFolder): BookmarkFolderBranch {
        if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.delete(bookmarkFolder)
        }
        return bookmarksRepository.deleteFolderBranch(bookmarkFolder)
    }

    override fun favorites(): Flow<List<Favorite>> {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.getFavorites()
        } else {
            favoritesRepository.favorites()
        }
    }

    override fun favorite(url: String): Favorite? {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.getFavorite(url)
        } else {
            favoritesRepository.favorite(url)
        }
    }

    override fun updateWithPosition(favorites: List<Favorite>) {
        return if (devSettingsDataStore.syncBookmarksEnabled) {
            savedSitesRepository.updateWithPosition(favorites)
        } else {
            favoritesRepository.updateWithPosition(favorites)
        }
    }
}
