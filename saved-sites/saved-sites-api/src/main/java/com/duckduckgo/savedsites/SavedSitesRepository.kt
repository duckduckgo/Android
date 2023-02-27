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

package com.duckduckgo.savedsites

import com.duckduckgo.savedsites.SavedSite.Bookmark
import com.duckduckgo.savedsites.SavedSite.Favorite
import io.reactivex.Single
import kotlinx.coroutines.flow.Flow
import java.io.Serializable

interface SavedSitesRepository {

    suspend fun getSavedSites(folderId: String): Flow<SavedSites>

    suspend fun getFolderContent(folderId: String): Flow<Pair<List<Bookmark>, List<BookmarkFolder>>>

    suspend fun getFolderTree(
        selectedFolderId: String,
        currentFolder: BookmarkFolder?,
    ): List<BookmarkFolderItem>

    suspend fun insertFolderBranch(branchToInsert: FolderBranch)

    fun deleteFolderBranch(folder: BookmarkFolder): FolderBranch

    fun getFolderBranch(folder: BookmarkFolder): FolderBranch

    fun getBookmarks(): Flow<List<Bookmark>>

    fun getBookmarksObservable(): Single<List<Bookmark>>

    fun getBookmark(url: String): Bookmark?

    fun getFavorites(): Flow<List<Favorite>>

    fun getFavoritesObservable(): Single<List<Favorite>>

    fun getFavoritesSync(): List<Favorite>

    fun getFavoritesCountByDomain(domain: String): Int

    fun getFavorite(url: String): Favorite?

    fun hasBookmarks(): Boolean
    fun hasFavorites(): Boolean

    fun insertBookmark(
        url: String,
        title: String,
    ): Bookmark

    fun insertFavorite(
        url: String,
        title: String,
    ): Favorite

    fun insert(savedSite: SavedSite): SavedSite
    fun delete(savedSite: SavedSite)
    fun update(savedSite: SavedSite)
    fun updateWithPosition(favorites: List<Favorite>)

    fun insert(folder: BookmarkFolder): BookmarkFolder
    fun update(folder: BookmarkFolder)

    fun delete(folder: BookmarkFolder)
    fun getFolder(folderId: String): BookmarkFolder?
    fun deleteAll()
    fun bookmarksCount(): Long
    fun favoritesCount(): Long
}
