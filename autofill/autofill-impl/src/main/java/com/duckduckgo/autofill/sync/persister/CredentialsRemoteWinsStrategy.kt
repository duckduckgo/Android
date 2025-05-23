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

import com.duckduckgo.autofill.api.domain.app.*
import com.duckduckgo.autofill.sync.*
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import kotlinx.coroutines.runBlocking
import logcat.logcat

class CredentialsRemoteWinsStrategy constructor(
    private val credentialsSync: CredentialsSync,
    private val credentialsSyncMapper: CredentialsSyncMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(
        credentials: credentialsSyncEntries,
        clientModifiedSince: String,
    ): SyncMergeResult {
        logcat { "Sync-autofill-Persist: ======= MERGING REMOTEWINS =======" }
        return kotlin.runCatching {
            runBlocking(dispatchers.io()) {
                credentials.entries.forEach { entry ->
                    val localCredential = credentialsSync.getCredentialWithSyncId(entry.id)
                    if (localCredential != null) {
                        processExistingEntry(localCredential, entry, credentials.last_modified)
                    } else {
                        processNewEntry(entry, credentials.last_modified)
                    }
                }
            }
        }.getOrElse {
            logcat { "Sync-autofill-Persist: merging failed with error $it" }
            return Error(reason = "RemoteWins merge failed with error $it")
        }.let {
            logcat { "Sync-autofill-Persist: merging completed" }
            Success()
        }
    }

    private suspend fun processNewEntry(remoteEntry: CredentialsSyncEntryResponse, clientModifiedSince: String) {
        if (remoteEntry.isDeleted()) return
        val updatedCredentials = credentialsSyncMapper.toLoginCredential(
            remoteEntry = remoteEntry,
            lastModified = clientModifiedSince,
        )
        logcat { "Sync-autofill-Persist: >>> save remote $updatedCredentials" }
        credentialsSync.saveCredential(updatedCredentials, remoteEntry.id)
    }

    private suspend fun processExistingEntry(
        localCredential: LoginCredentials,
        remoteEntry: CredentialsSyncEntryResponse,
        clientModifiedSince: String,
    ) {
        val localId = localCredential.id!!
        if (remoteEntry.isDeleted()) {
            logcat { "Sync-autofill-Persist: >>> delete local $localId" }
            credentialsSync.deleteCredential(localId)
            return
        }
        val updatedCredentials = credentialsSyncMapper.toLoginCredential(remoteEntry, localId, clientModifiedSince)
        credentialsSync.updateCredentials(updatedCredentials, remoteEntry.id)
    }
}
