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

import com.duckduckgo.app.utils.checkMainThread
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.autofill.sync.CredentialsSyncDataPersister.Adapters.Companion.updatesAdapter
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
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.threeten.bp.Instant
import org.threeten.bp.ZoneOffset
import org.threeten.bp.format.DateTimeFormatter
import javax.inject.*
import timber.log.Timber

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataPersister::class)
class CredentialsSyncDataPersister @Inject constructor(
    private val credentialsSyncMetadata: CredentialsSyncMetadata,
    private val credentialsSyncStore: CredentialsSyncStore,
    private val strategies: DaggerMap<SyncConflictResolution, CredentialsMergeStrategy>,
    private val appBuildConfig: AppBuildConfig,
) : SyncableDataPersister {
    override fun persist(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        if (appBuildConfig.isInternalBuild()) checkMainThread()

        return if (changes.type == CREDENTIALS) {
            Timber.d("Sync-autofill-Persist: received remote changes ${changes.jsonString}")
            Timber.d("Sync-autofill-Persist: received remote changes, merging with resolution $conflictResolution")
            val result = process(changes, conflictResolution)
            Timber.d("Sync-autofill-Persist: merging credentials finished with $result")
            return result
        } else {
            Timber.d("Sync-autofill-Persist: no credentials to merge")
            Success(false)
        }
    }

    private fun process(
        changes: SyncChangesResponse,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        if (changes.jsonString.isEmpty()) {
            Timber.d("Sync-autofill-Persist: merging completed, no entries to merge")
            return Success(false)
        }

        val response = kotlin.runCatching {
            updatesAdapter.fromJson(changes.jsonString)!!
        }.getOrElse {
            Timber.d("Sync-autofill-Persist: failed to parse remote changes")
            return SyncMergeResult.Error(reason = "Error parsing credentials ${it.message}")
        }

        val result = processEntries(response.credentials, conflictResolution)

        if (result is Success) {
            pruneDeletedObjects(credentialsSyncStore.startTimeStamp)
        }

        return result
    }

    private fun processEntries(
        crendentials: CrendentialsSyncEntries,
        conflictResolution: SyncConflictResolution,
    ): SyncMergeResult<Boolean> {
        if (conflictResolution != SyncConflictResolution.DEDUPLICATION) {
            credentialsSyncStore.serverModifiedSince = crendentials.lastModified
            credentialsSyncStore.clientModifiedSince = credentialsSyncStore.startTimeStamp
            Timber.d("Sync-autofill-Persist: updating credentials server last_modified to ${credentialsSyncStore.serverModifiedSince}")
            Timber.d("Sync-autofill-Persist: updating credentials client last_modified to ${credentialsSyncStore.clientModifiedSince}")
        }

        return if (crendentials.entries.isEmpty()) {
            Timber.d("Sync-autofill-Persist: merging completed, no entries to merge")
            Success(false)
        } else {
            return strategies[conflictResolution]?.processEntries(crendentials, credentialsSyncStore.clientModifiedSince) ?: SyncMergeResult.Error(reason = "Merge Strategy not found")
        }
    }

    private fun pruneDeletedObjects(lastModified: String) {
        credentialsSyncMetadata.removeDeletedEntities(lastModified)
    }

    override fun onSyncDisabled() {
        credentialsSyncStore.serverModifiedSince = "0"
        credentialsSyncStore.startTimeStamp = "0"
        credentialsSyncStore.clientModifiedSince = "0"
    }

    private fun getUtcIsoLocalDateTime(): String {
        // returns YYYY-MM-ddTHH:mm:ss.SSSZ
        return Instant.now().atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder()
                .add(KotlinJsonAdapterFactory()).build()
            val updatesAdapter: JsonAdapter<CredentialsSyncRemoteUpdates> =
                moshi.adapter(CredentialsSyncRemoteUpdates::class.java)
        }
    }
}

data class CredentialsSyncRemoteUpdates(
    val credentials: CrendentialsSyncEntries,
)

data class CrendentialsSyncEntries(
    val entries: List<CredentialsSyncEntryResponse>,
    private val last_modified: String,
) {

    //val lastModified: String by lazy { getLastModifiedWithMillis() }
    val lastModified: String = last_modified
    private fun getLastModifiedWithMillis(): String {
        val instant = Instant.parse(last_modified)
        val ofPattern = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
        return ofPattern.format(instant)
    }
}

data class CredentialsSyncEntryResponse(
    val id: String,
    val deleted: String? = null,
    val last_modified: String? = null,
    val domain: String? = null,
    val title: String? = null,
    val username: String? = null,
    val password: String? = null,
    val notes: String? = null,
)

internal fun CredentialsSyncEntryResponse.isDeleted(): Boolean {
    return this.deleted != null
}
