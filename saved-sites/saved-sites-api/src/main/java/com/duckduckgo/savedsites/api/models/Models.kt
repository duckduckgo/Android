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
import com.duckduckgo.savedsites.store.Relation
import java.io.Serializable

data class SavedSites(
    val favorites: List<Favorite>,
    val bookmarks: List<Bookmark>,
    val folders: List<BookmarkFolder>,
)

sealed class SavedSite(
    open val id: String,
    open val title: String,
    open val url: String,
) : Serializable {
    data class Favorite(
        override val id: String,
        override val title: String,
        override val url: String,
        val position: Int,
    ) : SavedSite(id, title, url)

    data class Bookmark(
        override val id: String,
        override val title: String,
        override val url: String,
        val parentId: String = Relation.BOOMARKS_ROOT,
    ) : SavedSite(id, title, url)
}
data class FolderBranch(
    val bookmarks: List<Bookmark>,
    val folders: List<BookmarkFolder>,
)
data class BookmarkFolderItem(
    val depth: Int,
    val bookmarkFolder: BookmarkFolder,
    val isSelected: Boolean = false,
)

data class BookmarkFolder(
    val id: String,
    val name: String,
    val parentId: String,
    val numBookmarks: Int = 0,
    val numFolders: Int = 0,
) : Serializable
