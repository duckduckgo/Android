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

    fun isFolderDuplicate(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): Boolean

    fun isFavouriteDuplicate(favorite: Favorite): Boolean
    fun isBookmarkDuplicate(bookmark: Bookmark): Boolean
}

@ContributesBinding(AppScope::class)
class RealSavedSitesDuplicateFinder @Inject constructor(val repository: SavedSitesRepository) : SavedSitesDuplicateFinder {
    override fun isFolderDuplicate(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): Boolean {
        val present = repository.getFolder(bookmarkFolder.id)
        return if (present != null) {
            val content = repository.getFolderContentSync(bookmarkFolder.id)
            isFolderContentDuplicate(content, children)
        } else {
            false
        }
    }

    private fun isFolderContentDuplicate(
        content: Pair<List<Bookmark>, List<BookmarkFolder>>,
        children: List<String>,
    ): Boolean {
        val presentChildren = content.first.map { it.id }.plus(content.second.map { it.id }).sorted()
        return if (presentChildren == children.sorted()) {
            // content of the folder is the same, but is the content of each item also the same?
            var sameBookmarks = false
            content.first.forEach {
                if (isBookmarkDuplicate(it)) {
                    sameBookmarks = true
                }
            }
            sameBookmarks
        } else {
            false
        }
    }

    override fun isFavouriteDuplicate(favorite: Favorite): Boolean {
        val present = repository.getFavoriteById(favorite.id)
        return if (present != null) {
            present.url == favorite.url && present.title == favorite.title
        } else {
            // same favourite might have a different ID
            val presentUrl = repository.getFavorite(favorite.url)
            if (presentUrl != null) {
                presentUrl.title == favorite.title
            } else {
                false
            }
        }
    }

    override fun isBookmarkDuplicate(bookmark: Bookmark): Boolean {
        val present = repository.getBookmarkById(bookmark.id)
        return if (present != null) {
            present.url == bookmark.url && present.title == bookmark.title && present.parentId == bookmark.parentId
        } else {
            // same bookmarks might have a different ID
            val presentUrl = repository.getBookmark(bookmark.url)
            if (presentUrl != null) {
                presentUrl.title == bookmark.title && presentUrl.parentId == bookmark.parentId
            } else {
                false
            }
        }
    }
}
