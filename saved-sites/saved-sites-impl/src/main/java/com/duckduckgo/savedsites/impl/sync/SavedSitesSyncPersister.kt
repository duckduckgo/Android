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
import com.duckduckgo.savedsites.impl.sync.algorithm.SavedSitesSyncPersisterAlgorithm
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncDataValidationResult
import com.duckduckgo.sync.api.engine.SyncErrorResponse
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
import logcat.LogPriority.INFO
import logcat.logcat

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class SavedSitesSyncPersister @Inject constructor(
    private val savedSitesSyncStore: SavedSitesSyncStore,
    private val savedSitesSyncRepository: SyncSavedSitesRepository,
    private val algorithm: SavedSitesSyncPersisterAlgorithm,
    private val savedSitesFormFactorSyncMigration: SavedSitesFormFactorSyncMigration,
    private val savedSitesSyncState: SavedSitesSyncFeatureListener,
) : SyncableDataPersister {
    override fun onSyncEnabled() {
        if (isLocalDataDirty()) {
            onSyncDisabled()
        }
    }

    override fun onSuccess(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        return if (changes.type == BOOKMARKS) {
            logcat { "Sync-Bookmarks: received remote changes $changes, merging with resolution $conflictResolution" }
            savedSitesSyncState.onSuccess(changes)
            val result = process(changes, conflictResolution)
            logcat { "Sync-Bookmarks: merging bookmarks finished with $result" }

            result
        } else {
            Success(false)
        }
    }

    override fun onError(error: SyncErrorResponse) {
        if (error.type == BOOKMARKS) {
            savedSitesSyncState.onError(error.featureSyncError)
        }
    }

    override fun onSyncDisabled() {
        savedSitesSyncStore.serverModifiedSince = "0"
        savedSitesSyncStore.clientModifiedSince = "0"
        savedSitesSyncStore.startTimeStamp = "0"
        savedSitesFormFactorSyncMigration.onFormFactorFavouritesDisabled()
        savedSitesSyncState.onSyncDisabled()
        savedSitesSyncRepository.removeMetadata()
        savedSitesSyncRepository.markSavedSitesAsInvalid(emptyList())
    }

    fun process(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        val result = when (val validation = validateChanges(changes)) {
            is SyncDataValidationResult.Error -> SyncMergeResult.Error(reason = validation.reason)
            is SyncDataValidationResult.Success -> {
                processEntries(validation.data, conflictResolution)
            }

            else -> {
                // this is a 304 from the BE, we don't acknowledge the response
                Success()
            }
        }

        if (result is Success) {
            pruneDeletedObjects()
        }

        return result
    }

    private fun isLocalDataDirty(): Boolean {
        return savedSitesSyncStore.serverModifiedSince != "0"
    }

    private fun validateChanges(changes: SyncChangesResponse): SyncDataValidationResult<SyncBookmarkEntries> {
        if (changes.isEmpty()) {
            logcat { "Sync-Bookmarks: JSON doesn't have changes, nothing to store" }
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
        logcat(INFO) {
            """
            Sync-Bookmarks: updating server last_modified from ${savedSitesSyncStore.serverModifiedSince}
             to ${bookmarks.last_modified}
            """.trimIndent()
        }
        logcat(INFO) {
            """
            Sync-Bookmarks: updating client last_modified from ${savedSitesSyncStore.clientModifiedSince} 
            to ${savedSitesSyncStore.startTimeStamp}
            """.trimIndent()
        }

        savedSitesSyncStore.serverModifiedSince = bookmarks.last_modified
        savedSitesSyncStore.clientModifiedSince = savedSitesSyncStore.startTimeStamp

        val result = if (bookmarks.entries.isEmpty()) {
            logcat { "Sync-Bookmarks: merging completed, no entries to merge" }
            Success(false)
        } else {
            algorithm.processEntries(bookmarks, conflictResolution, savedSitesSyncStore.clientModifiedSince)
        }

        if (conflictResolution == DEDUPLICATION) {
            savedSitesSyncRepository.setLocalEntitiesForNextSync(savedSitesSyncStore.startTimeStamp)
            // if conflict resolution is deduplication, response comes from /get (instead of /patch).
            // If deduplication, we override metadata only with the response data. Discarding any request info stored.
            savedSitesSyncRepository.discardRequestMetadata()
        }

        // we need to update the metadata of the entities
        savedSitesSyncRepository.addResponseMetadata(bookmarks.entries)

        return result
    }

    private fun pruneDeletedObjects() {
        savedSitesSyncRepository.pruneDeleted()
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
    val entries: List<SyncSavedSitesResponseEntry>,
    val last_modified: String,
)
