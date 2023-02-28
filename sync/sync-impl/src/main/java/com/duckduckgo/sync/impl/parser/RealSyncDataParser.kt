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

import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.store.Relation
import com.duckduckgo.sync.api.parser.SyncDataParser
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import timber.log.Timber

class RealSyncDataParser(
    private val repository: SavedSitesRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
) : SyncDataParser {

    override suspend fun generateInitialList(): String {
        val json = withContext(dispatcherProvider.io()) {
            val updates = mutableListOf<SyncUpdate>()
            val allUpdates = addFolderContent(Relation.BOOMARKS_ROOT, updates)
            return@withContext if (allUpdates.isEmpty()) {
                ""
            } else {
                val bookmarkUpdates = SyncDataUpdates(allUpdates)
                val dataBookmarks = SyncDataBookmarks(bookmarkUpdates)

                val moshi: Moshi = Moshi.Builder().build()
                val jsonAdapter = moshi.adapter(SyncDataBookmarks::class.java)
                val json: String = jsonAdapter.toJson(dataBookmarks)
                json
            }
        }
        return json
    }

    private suspend fun addFolderContent(
        folderId: String,
        updates: MutableList<SyncUpdate>
    ): List<SyncUpdate> {
        repository.getFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null){
                val childrenIds = mutableListOf<String>()
                for (bookmark in this.first) {
                    childrenIds.add(bookmark.id)
                    updates.add(SyncUpdate.asBookmark(id = bookmark.id, title = bookmark.title, url = bookmark.url, deleted = null))
                }
                updates.add(SyncUpdate.asFolder(id = folder.id, title = folder.name, children = childrenIds, deleted = null))
                for (folder in this.second) {
                    val childrenIds = getIdsFromFolder(folder.id)
                    // updates.add(SyncUpdate.asFolder(id = folder.id, title = folder.name, children = childrenIds, deleted = null))
                    addFolderContent(folder.id, updates)
                }
            }
        }
        return updates
    }

    private suspend fun getIdsFromFolder(folderId: String): List<String> {
        val ids = mutableListOf<String>()
        repository.getFolderContentSync(folderId).apply {
            for (bookmark in this.first) {
                ids.add(bookmark.id)
            }
            for (folder in this.second) {
                ids.add(folder.id)
            }
        }
        return ids
    }

    override fun parse() {
        TODO("Not yet implemented")
    }
}

data class SyncBookmarkPage(val url: String)
data class SyncFolderChildren(val children: List<String>)

data class SyncUpdate(
    val id: String,
    val title: String,
    val page: SyncBookmarkPage?,
    val folder: SyncFolderChildren?,
    val deleted: String?
) {
    companion object {
        fun asBookmark(
            id: String,
            title: String,
            url: String,
            deleted: String?
        ): SyncUpdate {
            return SyncUpdate(id, title, SyncBookmarkPage(url), null, deleted)
        }

        fun asFolder(
            id: String,
            title: String,
            children: List<String>,
            deleted: String?
        ): SyncUpdate {
            return SyncUpdate(id, title, null, SyncFolderChildren(children), deleted)
        }
    }
}

class SyncDataBookmarks(val bookmarks: SyncDataUpdates)
class SyncDataUpdates(val updates: List<SyncUpdate>)
