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

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface SavedSitesDuplicateFinder {

    fun findFolderDuplicate(
        bookmarkFolder: BookmarkFolder,
    ): SavedSitesDuplicateResult

    fun findFavouriteDuplicate(favorite: Favorite): SavedSitesDuplicateResult
    fun findBookmarkDuplicate(bookmark: Bookmark): SavedSitesDuplicateResult
}

sealed class SavedSitesDuplicateResult {
    object NotDuplicate : SavedSitesDuplicateResult()
    data class Duplicate(val id: String) : SavedSitesDuplicateResult()
}

@ContributesBinding(AppScope::class)
class RealSavedSitesDuplicateFinder @Inject constructor(val repository: SavedSitesRepository) : SavedSitesDuplicateFinder {
    override fun findFolderDuplicate(bookmarkFolder: BookmarkFolder): SavedSitesDuplicateResult {
        val present = repository.getFolder(bookmarkFolder.id)
        return if (present != null) {
            SavedSitesDuplicateResult.Duplicate(present.id)
        } else {
            val existingFolder = repository.getFolderByName(bookmarkFolder.name)
            if (existingFolder != null) {
                if (existingFolder.parentId == bookmarkFolder.parentId) {
                    SavedSitesDuplicateResult.Duplicate(existingFolder.id)
                } else {
                    SavedSitesDuplicateResult.NotDuplicate
                }
            } else {
                SavedSitesDuplicateResult.NotDuplicate
            }
        }
    }

    override fun findFavouriteDuplicate(favorite: Favorite): SavedSitesDuplicateResult {
        val present = repository.getFavoriteById(favorite.id)
        return if (present != null) {
            if (present.url == favorite.url && present.title == favorite.title) {
                SavedSitesDuplicateResult.Duplicate(present.id)
            } else {
                SavedSitesDuplicateResult.NotDuplicate
            }
        } else {
            // same favourite might have a different ID
            val presentUrl = repository.getFavorite(favorite.url)
            if (presentUrl != null) {
                if (presentUrl.title == favorite.title) {
                    SavedSitesDuplicateResult.Duplicate(presentUrl.id)
                } else {
                    SavedSitesDuplicateResult.NotDuplicate
                }
            } else {
                SavedSitesDuplicateResult.NotDuplicate
            }
        }
    }

    override fun findBookmarkDuplicate(bookmark: Bookmark): SavedSitesDuplicateResult {
        val present = repository.getBookmarkById(bookmark.id)
        return if (present != null) {
            if (present.url == bookmark.url && present.title == bookmark.title && present.parentId == bookmark.parentId) {
                SavedSitesDuplicateResult.Duplicate(present.id)
            } else {
                SavedSitesDuplicateResult.NotDuplicate
            }
        } else {
            // same bookmarks might have a different ID
            val presentUrl = repository.getBookmark(bookmark.url)
            if (presentUrl != null) {
                if (presentUrl.title == bookmark.title && presentUrl.parentId == bookmark.parentId) {
                    SavedSitesDuplicateResult.Duplicate(presentUrl.id)
                } else {
                    SavedSitesDuplicateResult.NotDuplicate
                }
            } else {
                SavedSitesDuplicateResult.NotDuplicate
            }
        }
    }
}
