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
import com.duckduckgo.autofill.sync.CredentialsSync
import com.duckduckgo.autofill.sync.CredentialsSyncMapper
import com.duckduckgo.autofill.sync.CrendentialsSyncEntries
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CredentialsRemoteWinsStrategy constructor(
    private val credentialsSync: CredentialsSync,
    private val credentialsSyncMapper: CredentialsSyncMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(credentials: CrendentialsSyncEntries): SyncMergeResult<Boolean> {
        Timber.d("Sync-autofill-Persist: ======= MERGING REMOTEWINS =======")
        return kotlin.runCatching {
            credentials.entries.forEach { entry ->
                runBlocking(dispatchers.io()) {
                    val localCredential = credentialsSync.getCredentialWithSyncId(entry.id)
                    if (localCredential != null) {
                        val localId = localCredential.id!!
                        if (entry.isDeleted()) {
                            Timber.d("Sync-autofill-Persist: >>> delete local $localId")
                            credentialsSync.deleteCredential(localId)
                            return@runBlocking
                        }
                        val updatedCredentials = credentialsSyncMapper.toLoginCredential(entry, localId, credentials.last_modified)
                        credentialsSync.updateCredentials(updatedCredentials, entry.id)
                    } else {
                        if (entry.isDeleted()) return@runBlocking
                        val updatedCredentials = credentialsSyncMapper.toLoginCredential(
                            remoteEntry = entry,
                            lastModified = credentials.last_modified,
                        )
                        Timber.d("Sync-autofill-Persist: >>> save remote $updatedCredentials")
                        credentialsSync.saveCredential(updatedCredentials, entry.id)
                    }
                }
            }
        }.getOrElse {
            Timber.d("Sync-autofill-Persist: merging failed with error $it")
            return Error(reason = "RemoteWins merge failed with error $it")
        }.let {
            Timber.d("Sync-autofill-Persist: merging completed")
            Success(true)
        }
    }
}
