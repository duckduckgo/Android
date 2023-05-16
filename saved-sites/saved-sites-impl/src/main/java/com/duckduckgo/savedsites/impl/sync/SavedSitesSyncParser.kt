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

import androidx.annotation.VisibleForTesting
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.BookmarkFolder
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.FeatureSyncStore
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncParser
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncablePlugin.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import org.threeten.bp.OffsetDateTime
import timber.log.Timber
import kotlin.math.sin

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncParser::class)
class SavedSitesSyncParser @Inject constructor(
    private val repository: SavedSitesRepository,
    private val savedSitesSyncStore: FeatureSyncStore,
    private val syncCrypto: SyncCrypto,
) : SyncParser, SyncablePlugin {

    override fun getChanges(since: String): SyncChanges {
        return parseChanges(since)
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        return SyncMergeResult.Success(true)
    }

    override fun onFeatureRemoved() {
        savedSitesSyncStore.modifiedSince = "0"
    }

    override fun parseChanges(since: String): SyncChanges {
        val updates = if (since.isEmpty()) {
            allContent()
        } else {
            changesSince(since)
        }
        return formatUpdates(updates)
    }

    @VisibleForTesting
    fun changesSince(since: String): List<SyncBookmarkEntry> {
        Timber.d("Sync: generating changes since $since")
        val updates = mutableListOf<SyncBookmarkEntry>()

        val folders = repository.getFoldersModifiedSince(since)
        folders.forEach { folder ->
            if (folder.deleted == null) {
                addFolderContent(folder.id, updates, since)
            } else {
                updates.add(deletedEntry(folder.id, folder.deleted!!))
            }
        }

        // bookmarks that were deleted won't be part of the previous check
        // we need to add them
        val bookmarks = repository.getBookmarksModifiedSince(since)
        bookmarks.forEach { bookmark ->
            if (bookmark.deleted != null) {
                updates.add(deletedEntry(bookmark.id, bookmark.deleted!!))
            }
        }

        return updates
    }

    @VisibleForTesting
    fun allContent(): List<SyncBookmarkEntry> {
        Timber.d("Sync: generating all content")
        val hasFavorites = repository.hasFavorites()
        val hasBookmarks = repository.hasBookmarks()

        if (!hasFavorites && !hasBookmarks) {
            Timber.d("Sync: nothing to generate, favourites and bookmarks empty")
            return emptyList()
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

        return addFolderContent(SavedSitesNames.BOOKMARKS_ROOT, updates)
    }

    private fun addFolderContent(
        folderId: String,
        updates: MutableList<SyncBookmarkEntry>,
        lastModified: String = "",
    ): List<SyncBookmarkEntry> {
        repository.getAllFolderContentSync(folderId).apply {
            val folder = repository.getFolder(folderId)
            if (folder != null) {
                val childrenIds = mutableListOf<String>()
                for (bookmark in this.first) {
                    if (bookmark.deleted != null) {
                        updates.add(deletedEntry(bookmark.id, bookmark.deleted!!))
                    } else {
                        childrenIds.add(bookmark.id)
                        if (bookmark.modifiedSince(lastModified)) {
                            updates.add(encryptSavedSite(bookmark))
                        }
                    }
                }
                for (eachFolder in this.second) {
                    if (eachFolder.deleted != null) {
                        updates.add(deletedEntry(eachFolder.id, eachFolder.deleted!!))
                    } else {
                        childrenIds.add(eachFolder.id)
                        if (eachFolder.modifiedSince(lastModified)) {
                            addFolderContent(eachFolder.id, updates)
                        }
                    }
                }
                updates.add(encryptFolder(folder, childrenIds))
            }
        }
        return updates
    }

    private fun BookmarkFolder.modifiedSince(since: String): Boolean {
        return if (since.isEmpty()) {
            true
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(since)
            entityModified.isAfter(sinceModified)
        }
    }

    private fun Bookmark.modifiedSince(since: String): Boolean {
        return if (since.isEmpty()) {
            true
        } else {
            val entityModified = OffsetDateTime.parse(this.lastModified)
            val sinceModified = OffsetDateTime.parse(since)
            entityModified.isAfter(sinceModified)
        }
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
            deleted = bookmarkFolder.deleted,
            client_last_modified = bookmarkFolder.lastModified ?: DatabaseDateFormatter.iso8601(),
        )
    }

    private fun deletedEntry(
        id: String,
        deleted: String
    ): SyncBookmarkEntry {
        return SyncBookmarkEntry(
            id = id,
            title = null,
            folder = null,
            page = null,
            deleted = deleted,
            client_last_modified = null,
        )
    }

    private fun formatUpdates(updates: List<SyncBookmarkEntry>): SyncChanges {
        return if (updates.isEmpty()) {
            SyncChanges.empty()
        } else {
            val bookmarkUpdates = SyncBookmarkUpdates(updates, savedSitesSyncStore.modifiedSince)
            val patch = SyncDataRequest(bookmarkUpdates)
            val allDataJSON = Adapters.patchAdapter.toJson(patch)
            SyncChanges(BOOKMARKS, allDataJSON)
        }
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
    val title: String?,
    val page: SyncBookmarkPage?,
    val folder: SyncFolderChildren?,
    val deleted: String?,
    val client_last_modified: String?,
)

fun SyncBookmarkEntry.isFolder(): Boolean = this.folder != null
fun SyncBookmarkEntry.titleOrFallback(): String = this.title ?: "Bookmark"

fun SyncBookmarkEntry.isBookmarksRoot(): Boolean = this.folder != null && this.id == SavedSitesNames.BOOKMARKS_ROOT
fun SyncBookmarkEntry.isFavouritesRoot(): Boolean = this.folder != null && this.id == SavedSitesNames.FAVORITES_ROOT
fun SyncBookmarkEntry.isBookmark(): Boolean = this.page != null

class SyncDataRequest(val bookmarks: SyncBookmarkUpdates)
class SyncBookmarkUpdates(
    val updates: List<SyncBookmarkEntry>,
    val modified_since: String = "0",
)

// e8b0c8ea-5e75-484f-8764-1dd82e9fe5b2
