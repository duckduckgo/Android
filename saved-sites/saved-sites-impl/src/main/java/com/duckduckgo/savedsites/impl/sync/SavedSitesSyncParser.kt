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
import com.duckduckgo.savedsites.api.models.SavedSitesNames
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncParser
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncParser::class)
class SavedSitesSyncParser @Inject constructor(
    private val repository: SavedSitesRepository,
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
            updates.add(
                SyncBookmarkEntry.asFolder(
                    id = SavedSitesNames.FAVORITES_ROOT,
                    title = syncCrypto.encrypt(SavedSitesNames.FAVORITES_NAME),
                    children = favorites.map { it.id },
                    deleted = null,
                ),
            )
        }

        return formatUpdates(updates)
    }

    override fun getChanges(since: String): SyncChanges {

        return parseChanges(since)
    }

    private fun formatUpdates(updates: List<SyncBookmarkEntry>): SyncChanges {
        val bookmarkUpdates = SyncBookmarkUpdates(updates)
        val patch = SyncDataRequest(bookmarkUpdates)
        val allDataJSON = Adapters.patchAdapter.toJson(patch)

        return SyncChanges(BOOKMARKS, allDataJSON)
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String,
    ) {
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
) {
    companion object {
        fun asBookmark(
            id: String,
            title: String,
            url: String,
            deleted: String?,
        ): SyncBookmarkEntry {
            return SyncBookmarkEntry(id, title, SyncBookmarkPage(url), null, deleted)
        }

        fun asFolder(
            id: String,
            title: String,
            children: List<String>,
            deleted: String?,
        ): SyncBookmarkEntry {
            return SyncBookmarkEntry(id, title, null, SyncFolderChildren(children), deleted)
        }
    }
}

fun SyncBookmarkEntry.isFolder(): Boolean = this.folder != null
fun SyncBookmarkEntry.isBookmark(): Boolean = this.page != null

class SyncDataRequest(val bookmarks: SyncBookmarkUpdates)
class SyncBookmarkUpdates(val updates: List<SyncBookmarkEntry>)
