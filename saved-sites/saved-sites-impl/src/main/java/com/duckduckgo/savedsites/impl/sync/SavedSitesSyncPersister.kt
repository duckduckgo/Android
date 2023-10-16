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
            Timber.d("Sync-Feature: received remote changes, merging with resolution $conflictResolution")
            val result = process(changes, conflictResolution)
            Timber.d("Sync-Feature: merging bookmarks finished with $result")
            result
        } else {
            Timber.d("Sync-Feature: no bookmarks to merge")
            Success(false)
        }
    }

    override fun onSyncDisabled() {
        savedSitesSyncStore.modifiedSince = "0"
    }

    fun process(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        val result = when (val validation = validateChanges(changes)) {
            is SyncDataValidationResult.Error -> SyncMergeResult.Error(reason = validation.reason)
            is SyncDataValidationResult.Success -> processEntries(validation.data, conflictResolution)
            else -> SyncMergeResult.Error(reason = "Something went wrong")
        }

        if (result is Success) {
            pruneDeletedObjects()

            if (conflictResolution == DEDUPLICATION) {
                // first sync has a special case, bookmarks and favorites that were added previously to sync need to be updated to lastModified
                val modifiedSince = OffsetDateTime.parse(savedSitesSyncStore.modifiedSince)
                val updatedModifiedSince = modifiedSince.plusSeconds(1)
                savedSitesRepository.updateModifiedSince(savedSitesSyncStore.modifiedSince, DatabaseDateFormatter.iso8601(updatedModifiedSince))
            }
        }

        return result
    }

    private fun validateChanges(changes: SyncChangesResponse): SyncDataValidationResult<SyncBookmarkEntries> {
        val response = kotlin.runCatching { Adapters.updatesAdapter.fromJson(changes.jsonString) }.getOrElse {
            return SyncDataValidationResult.Error(reason = "Sync-Feature: JSON format incorrect, exception: $it")
        }

        if (response == null) {
            return SyncDataValidationResult.Error(reason = "Sync-Feature: merging failed, JSON format incorrect, response null")
        }

        if (response.bookmarks == null) {
            return SyncDataValidationResult.Error(reason = "Sync-Feature: merging failed, JSON format incorrect, bookmarks null")
        }

        if (response.bookmarks.entries == null) {
            Timber.d("Sync-Feature: JSON has null entries, replacing with empty list")
            return SyncDataValidationResult.Success(SyncBookmarkEntries(emptyList(), response.bookmarks.last_modified))
        }

        return SyncDataValidationResult.Success(response.bookmarks)
    }

    private fun processEntries(
        bookmarks: SyncBookmarkEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult {
        Timber.d("Sync-Feature: updating bookmarks last_modified to ${bookmarks.last_modified}")
        savedSitesSyncStore.modifiedSince = bookmarks.last_modified

        return if (bookmarks.entries.isEmpty()) {
            Timber.d("Sync-Feature: merging completed, no entries to merge")
            Success(false)
        } else {
            algorithm.processEntries(bookmarks, conflictResolution)
        }
    }

    private fun pruneDeletedObjects() {
        savedSitesRepository.pruneDeleted()
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
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
