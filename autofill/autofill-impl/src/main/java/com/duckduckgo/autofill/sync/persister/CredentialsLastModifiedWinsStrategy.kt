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

package com.duckduckgo.autofill.sync.persister

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.autofill.api.domain.app.*
import com.duckduckgo.autofill.sync.*
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CredentialsLastModifiedWinsStrategy(
    private val credentialsSync: CredentialsSync,
    private val credentialsSyncMapper: CredentialsSyncMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(
        credentials: credentialsSyncEntries,
        clientModifiedSince: String,
    ): SyncMergeResult<Boolean> {
        Timber.d("Sync-autofill-Persist: ======= MERGING TIMESTAMP =======")
        return kotlin.runCatching {
            runBlocking(dispatchers.io()) {
                credentials.entries.forEach { entry ->
                    val localCredential = credentialsSync.getCredentialWithSyncId(entry.id)
                    if (localCredential == null) {
                        processNewEntry(entry, clientModifiedSince)
                    } else {
                        processExistingEntry(localCredential, entry, clientModifiedSince)
                    }
                }
            }
        }.getOrElse {
            Timber.d("Sync-autofill-Persist: merging failed with error $it")
            return Error(reason = "LastModified merge failed with error $it")
        }.let {
            Timber.d("Sync-autofill-Persist: merging completed")
            Success(true)
        }
    }

    private suspend fun processNewEntry(entry: CredentialsSyncEntryResponse, clientModifiedSince: String) {
        if (entry.isDeleted()) return
        val newCredential = credentialsSyncMapper.toLoginCredential(
            remoteEntry = entry,
            lastModified = clientModifiedSince,
        )
        Timber.d("Sync-autofill-Persist: >>> save remote $newCredential")
        credentialsSync.saveCredential(newCredential, remoteId = entry.id)
    }

    private suspend fun processExistingEntry(localCredential: LoginCredentials, remoteEntry: CredentialsSyncEntryResponse, clientModifiedSince: String) {
        val localId = localCredential.id!!
        if (remoteEntry.isDeleted()) {
            Timber.d("Sync-autofill-Persist: >>> delete local ${localCredential.id}")
            credentialsSync.deleteCredential(localId)
            return
        }
        val hasDataChangedWhileSyncing = (localCredential.lastUpdatedMillis ?: 0) >= DatabaseDateFormatter.parseIso8601ToMillis(
            clientModifiedSince,
        )
        if (!hasDataChangedWhileSyncing) {
            val remoteCredentials = mapRemoteToLocalLoginCredential(remoteEntry, localId, clientModifiedSince)
            Timber.d("Sync-autofill-Persist: >>> update with remote $remoteCredentials")
            credentialsSync.updateCredentials(remoteCredentials, remoteEntry.id)
        }
    }

    private fun mapRemoteToLocalLoginCredential(remoteEntry: CredentialsSyncEntryResponse, localId: Long, clientModifiedSince: String): LoginCredentials {
        return credentialsSyncMapper.toLoginCredential(
            remoteEntry = remoteEntry,
            localId = localId,
            lastModified = clientModifiedSince,
        )
    }
}
