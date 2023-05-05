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

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncParser
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncParser::class)
class SavedSitesSyncParser @Inject constructor(
    private val repository: SavedSitesRepository,
    private val savedSitesSyncStore: FeatureSyncStore,
    private val syncCrypto: SyncCrypto,
) : SyncParser, SyncablePlugin {
    override fun parseChanges(since: String): SyncChanges {
        return if (since.isEmpty()) {
            // when since isEmpty it means we want all changes
            parseAllBookmarks()
        } else {
            SyncChanges(BOOKMARKS, "")
        }
    }

    private fun parseAllBookmarks(): SyncChanges {
        val hasFavorites = repository.hasFavorites()
        val hasBookmarks = repository.hasBookmarks()

        if (!hasFavorites && !hasBookmarks) {
            return SyncChanges(BOOKMARKS, "")
        }

        val updates = mutableListOf<SyncBookmarkEntry>()
        // favorites (we don't add individual items, they are added as we go through bookmark folders)
        if (hasFavorites) {
            val favorites = repository.getFavoritesSync()
            val favoriteFolder = repository.getFolder(SavedSitesNames.FAVORITES_ROOT)
            favoriteFolder?.let {
                updates.add(encryptFolder(favoriteFolder, favorites.map { it.id }))
            }
        }

        val bookmarks = addFolderContent(SavedSitesNames.BOOMARKS_ROOT, updates)

        return formatUpdates(bookmarks)
    }

    private fun addFolderContent(
        folderId: String,
        updates: MutableList<SyncBookmarkEntry>,
    ): List<SyncBookmarkEntry> {
        repository.getFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null) {
                val childrenIds = mutableListOf<String>()
                for (bookmark in this.first) {
                    childrenIds.add(bookmark.id)
                    updates.add(encryptSavedSite(bookmark))
                }
                for (eachFolder in this.second) {
                    childrenIds.add(eachFolder.id)
                    addFolderContent(eachFolder.id, updates)
                }
                updates.add(encryptFolder(folder, childrenIds))
            }
        }
        return updates
    }

    private fun encryptSavedSite(
        savedSite: SavedSite,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = savedSite.id,
            title = syncCrypto.encrypt(savedSite.title),
            page = SyncBookmarkPage(syncCrypto.encrypt(savedSite.url)),
            folder = null,
            deleted = null,
            client_last_modified = savedSite.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun encryptFolder(
        bookmarkFolder: BookmarkFolder,
        children: List<String>,
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = bookmarkFolder.id,
            title = syncCrypto.encrypt(bookmarkFolder.name),
            folder = SyncFolderChildren(children),
            page = null,
            deleted = null,
            client_last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    override fun getChanges(since: String): SyncChanges {
        return parseChanges(since)
    }

    private fun formatUpdates(updates: List<SyncBookmarkEntry>): SyncChanges {
        val bookmarkUpdates = SyncBookmarkUpdates(updates, savedSitesSyncStore.modifiedSince)
        val patch = SyncDataRequest(bookmarkUpdates)
        val allDataJSON = Adapters.patchAdapter.toJson(patch)

        return SyncChanges(BOOKMARKS, allDataJSON)
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String,
    ): SyncMergeResult<Boolean> {
        return SyncMergeResult.Success(true)
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val patchAdapter: JsonAdapter<SyncDataRequest> =
                moshi.adapter(SyncDataRequest::class.java)
        }
    }
}

data class SyncBookmarkPage(val url: String)
data class SyncFolderChildren(val children: List<String>)

data class SyncBookmarkEntry(
    val id: String,
    val title: String,
    val page: SyncBookmarkPage?,
    val folder: SyncFolderChildren?,
    val deleted: String?,
    val client_last_modified: String?,
)

fun SyncBookmarkEntry.isFolder(): Boolean = this.folder != null

fun SyncBookmarkEntry.isBookmarksRoot(): Boolean = this.folder != null && this.id == SavedSitesNames.BOOMARKS_ROOT
fun SyncBookmarkEntry.isFavouritesRoot(): Boolean = this.folder != null && this.id == SavedSitesNames.FAVORITES_ROOT
fun SyncBookmarkEntry.isBookmark(): Boolean = this.page != null

class SyncDataRequest(val bookmarks: SyncBookmarkUpdates)
class SyncBookmarkUpdates(
    val updates: List<SyncBookmarkEntry>,
    val modified_since: String = "0",
)

// e8b0c8ea-5e75-484f-8764-1dd82e9fe5b2
