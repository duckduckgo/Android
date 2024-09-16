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

package com.duckduckgo.savedsites.api.models

import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import java.io.Serializable
import java.util.*

data class SavedSites(
    val favorites: List<Favorite>,
    val bookmarks: List<Any>,
)

sealed class SavedSite(
    open val id: String,
    open val title: String,
    open val url: String,
    open val lastModified: String?,
    open val deleted: String? = null,
) : Serializable {
    data class Favorite(
        override val id: String,
        override val title: String,
        override val url: String,
        override val lastModified: String?,
        val position: Int,
        override val deleted: String? = null,
    ) : SavedSite(id, title, url, lastModified)

    data class Bookmark(
        override val id: String,
        override val title: String,
        override val url: String,
        val parentId: String = SavedSitesNames.BOOKMARKS_ROOT,
        override val lastModified: String?,
        override val deleted: String? = null,
        val isFavorite: Boolean = false,
    ) : SavedSite(id, title, url, lastModified)
}

/**
 * Used to represent the content of a [BookmarkFolder]
 */
data class FolderBranch(
    val bookmarks: List<Bookmark>,
    val folders: List<BookmarkFolder>,
)

/**
 * UI model used in the Bookmarks Management screen to edit a [BookmarkFolder]
 */
data class BookmarkFolderItem(
    val depth: Int,
    val bookmarkFolder: BookmarkFolder,
    val isSelected: Boolean = false,
)

/**
 * UI model used in the Bookmarks Management screen to represent a [BookmarkFolder]
 */
data class BookmarkFolder(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val parentId: String,
    val numBookmarks: Int = 0,
    val numFolders: Int = 0,
    val lastModified: String? = null,
    val deleted: String? = null,
) : Serializable

object SavedSitesNames {
    const val FAVORITES_ROOT = "favorites_root"
    const val FAVORITES_NAME = "Favorites"
    const val FAVORITES_DESKTOP_ROOT = "desktop_favorites_root"
    const val FAVORITES_DESKTOP_NAME = "Desktop Favorites"
    const val FAVORITES_MOBILE_ROOT = "mobile_favorites_root"
    const val FAVORITES_MOBILE_NAME = "Mobile Favorites"
    const val BOOKMARKS_ROOT = "bookmarks_root"
    const val BOOKMARKS_NAME = "Bookmarks"
    const val BOOMARKS_ROOT_ID = 0L
}

/**
 * Used to build up a folder tree of [Bookmark]s and [BookmarkFolder]s
 */
data class FolderTreeItem(
    val id: String,
    val name: String,
    val parentId: String,
    val url: String?,
    val depth: Int = 0,
)
