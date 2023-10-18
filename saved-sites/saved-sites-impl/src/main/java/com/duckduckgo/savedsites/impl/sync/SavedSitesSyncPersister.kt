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
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesSyncPersisterAlgorithm
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncDataValidationResult
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution.DEDUPLICATION
import com.duckduckgo.sync.api.engine.SyncableType.BOOKMARKS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.Inject
import org.threeten.bp.OffsetDateTime
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class SavedSitesSyncPersister @Inject constructor(
    private val savedSitesRepository: SavedSitesRepository,
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val algorithm: SavedSitesSyncPersisterAlgorithm,
) : SyncableDataPersister {

    override fun persist(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        return if (changes.type == BOOKMARKS) {
            Timber.d("Sync-Bookmarks: received remote changes $changes, merging with resolution $conflictResolution")
            val result = process(changes, conflictResolution)
            Timber.d("Sync-Bookmarks: merging bookmarks finished with $result")
            result
        } else {
            Success(false)
        }
    }

    override fun onSyncDisabled() {
        savedSitesSyncStore.serverModifiedSince = "0"
        savedSitesSyncStore.clientModifiedSince = "0"
        savedSitesSyncStore.startTimeStamp = "0"
    }

    fun process(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        val result = when (val validation = validateChanges(changes)) {
            is SyncDataValidationResult.Error -> SyncMergeResult.Error(reason = validation.reason)
            is SyncDataValidationResult.Success -> processEntries(validation.data, conflictResolution)
            else -> Success(false)
        }

        if (result is Success) {
            pruneDeletedObjects()
        }

        return result
    }

    private fun validateChanges(changes: SyncChangesResponse): SyncDataValidationResult<SyncBookmarkEntries> {
        if (changes.isEmpty()) {
            Timber.d("Sync-Bookmarks: JSON doesn't have changes, nothing to store")
            return SyncDataValidationResult.NoChanges
        }

        val response = kotlin.runCatching { Adapters.updatesAdapter.fromJson(changes.jsonString)!! }.getOrElse {
            return SyncDataValidationResult.Error(reason = "Sync-Bookmarks: JSON format incorrect, exception: $it")
        }

        return SyncDataValidationResult.Success(response.bookmarks)
    }

    private fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        Timber.i("Sync-Bookmarks: updating server last_modified from ${savedSitesSyncStore.serverModifiedSince} to ${bookmarks.last_modified}")
        Timber.i(
            "Sync-Bookmarks: updating client last_modified from ${savedSitesSyncStore.clientModifiedSince} to ${savedSitesSyncStore.startTimeStamp}",
        )

        savedSitesSyncStore.serverModifiedSince = bookmarks.last_modified
        savedSitesSyncStore.clientModifiedSince = savedSitesSyncStore.startTimeStamp

        val result = if (bookmarks.entries.isEmpty()) {
            Timber.d("Sync-Bookmarks: merging completed, no entries to merge")
            Success(false)
        } else {
            algorithm.processEntries(bookmarks, conflictResolution, savedSitesSyncStore.clientModifiedSince)
        }

        // it's possible that there were entities present in the device before the first sync
        // we need to make sure that those entities are sent to the BE after all new data has been stored
        // we do that by updating the modifiedSince date to a newer date that the last sync
        if (conflictResolution == DEDUPLICATION) {
            val modifiedSince = OffsetDateTime.now().plusSeconds(1)
            savedSitesRepository.getEntitiesModifiedBefore(savedSitesSyncStore.startTimeStamp).forEach {
                savedSitesRepository.updateModifiedSince(it, DatabaseDateFormatter.iso8601(modifiedSince))
                Timber.d("Sync-Bookmarks: updating $it modifiedSince to $modifiedSince")
            }
        }

        return result
    }

    private fun pruneDeletedObjects() {
        savedSitesRepository.pruneDeleted()
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory()).build()
            val updatesAdapter: JsonAdapter<SyncBookmarkRemoteUpdates> =
                moshi.adapter(SyncBookmarkRemoteUpdates::class.java)
        }
    }
}

data class SyncBookmarkRemoteUpdates(
    val bookmarks: SyncBookmarkEntries,
)

data class SyncBookmarkEntries(
    val entries: List<SyncBookmarkEntry>,
    val last_modified: String,
)
