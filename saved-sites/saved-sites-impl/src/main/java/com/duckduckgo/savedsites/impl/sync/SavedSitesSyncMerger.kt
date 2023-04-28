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
import com.duckduckgo.savedsites.impl.sync.SavedSitesSyncParser.Adapters
import com.duckduckgo.sync.api.SyncCrypto
import com.duckduckgo.sync.api.engine.SyncChanges
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMerger
import com.duckduckgo.sync.api.engine.SyncablePlugin
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import timber.log.Timber
import javax.inject.Inject

@ContributesMultibinding(scope = AppScope::class, boundType = SyncablePlugin::class)
@ContributesBinding(scope = AppScope::class, boundType = SyncMerger::class)
class SavedSitesSyncMerger @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val syncCrypto: SyncCrypto,
) : SyncMerger, SyncablePlugin {
    override fun merge(changes: SyncChanges): SyncMergeResult<Boolean> {
        Timber.d("Sync: merging remote bookmarks changes $changes")
        val remoteBookmarks = Adapters.updatesAdapter.fromJson(changes.updatesJSON)
            ?: return SyncMergeResult.Error(reason = "Sync: merging failed, JSON format incorrect")

        // parse folders
        // parse bookmarks
        // parse favourites
        val folders = mutableListOf<BookmarkFolder>()
        val bookmarks = mutableListOf<Bookmark>()
        val favorites = mutableListOf<Favorite>()
        remoteBookmarks.entries.forEach {
            
        }
        return SyncMergeResult.Success(true)
    }

    override fun getChanges(since: String): SyncChanges {
        return SyncChanges.empty()
    }

    override fun syncChanges(
        changes: List<SyncChanges>,
        timestamp: String,
    ) {
        Timber.d("Sync: received remote changes from $timestamp")
        changes.find { it.type == BOOKMARKS }?.let { bookmarkChanges ->
            merge(bookmarkChanges)
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val updatesAdapter: JsonAdapter<SyncBookmarkRemoteUpdates> =
                moshi.adapter(SyncBookmarkRemoteUpdates::class.java)
        }
    }
}

class SyncBookmarkRemoteUpdates(
    val entries: List<SyncBookmarkEntry>,
    val last_modified: String
)

