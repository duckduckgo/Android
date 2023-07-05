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

package com.duckduckgo.autofill.sync

import com.duckduckgo.autofill.sync.persister.CredentialsMergeStrategy
import com.duckduckgo.di.DaggerMap
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncChangesResponse
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import com.duckduckgo.sync.api.engine.SyncableDataPersister
import com.duckduckgo.sync.api.engine.SyncableDataPersister.SyncConflictResolution
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import javax.inject.*
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class WebsiteLoginCredentialPersister @Inject constructor(
    private val syncLoginCredentials: SyncLoginCredentials,
    private val autofillSyncStore: AutofillSyncStore,
    private val strategies: DaggerMap<SyncConflictResolution, CredentialsMergeStrategy>,
) : SyncableDataPersister {
    override fun persist(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        return if (changes.type == CREDENTIALS) {
            Timber.d("Sync-autofill-Persist: received remote changes ${changes.jsonString}")
            Timber.d("Sync-autofill-Persist: received remote changes, merging with resolution $conflictResolution")
            val result = process(changes, conflictResolution)
            Timber.d("Sync-autofill-Persist: merging credentials finished with $result")
            Success(true)
        } else {
            Timber.d("Sync-autofill-Persist: no credentials to merge")
            Success(false)
        }
    }

    private fun process(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        val response = kotlin.runCatching { Adapters.updatesAdapter.fromJson(changes.jsonString) }.getOrNull()
        if (response == null) {
            Timber.d("Sync-autofill-Persist: failed to parse remote changes")
            return SyncMergeResult.Error(reason = "Something went wrong")
        }
        val result = processEntries(response.credentials, conflictResolution)

        if (result is Success) {
            pruneDeletedObjects()
        }

        return result
    }

    private fun processEntries(
        crendentials: SyncCrendentialsEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        Timber.d("Sync-autofill-Persist: updating credentials last_modified to ${crendentials.last_modified}")
        autofillSyncStore.modifiedSince = crendentials.last_modified

        return if (crendentials.entries.isEmpty()) {
            Timber.d("Sync-autofill-Persist: merging completed, no entries to merge")
            Success(false)
        } else {
            return strategies[conflictResolution]?.processEntries(crendentials.entries) ?: SyncMergeResult.Error(reason = "Merge Strategy not found")
        }
    }

    private fun pruneDeletedObjects() {
        syncLoginCredentials.removeDeletedEntities(autofillSyncStore.modifiedSince)
    }

    override fun onSyncDisabled() {
        autofillSyncStore.modifiedSince = "0"
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val updatesAdapter: JsonAdapter<SyncCredentialsRemoteUpdates> =
                moshi.adapter(SyncCredentialsRemoteUpdates::class.java)
        }
    }
}

data class SyncCredentialsRemoteUpdates(
    val credentials: SyncCrendentialsEntries,
)

data class SyncCrendentialsEntries(
    val entries: List<LoginCredentialEntryResponse>,
    val last_modified: String,
)

data class LoginCredentialEntryResponse(
    val id: String,
    val deleted: String? = null,
    val last_modified: String?,
    val domain: String? = null,
    val title: String? = null,
    val username: String? = null,
    val password: String? = null,
    val notes: String? = null,
)

internal fun LoginCredentialEntryResponse.isDeleted(): Boolean {
    return this.deleted != null
}
