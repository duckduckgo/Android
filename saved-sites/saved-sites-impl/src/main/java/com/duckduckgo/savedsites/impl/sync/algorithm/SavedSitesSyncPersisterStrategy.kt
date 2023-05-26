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

package com.duckduckgo.savedsites.impl.sync.algorithm

import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.sync.SyncBookmarkEntry

interface SavedSitesSyncPersisterStrategy {

    fun processBookmarkFolder(
        folder: BookmarkFolder,
        parentId: String,
        lastModified: String,
    )

    fun processFavourite(
        favourite: Favorite,
        lastModified: String,
    )

    fun processBookmark(
        bookmark: Bookmark,
        bookmarkId: String,
        folderId: String,
        lastModified: String
    )
}
