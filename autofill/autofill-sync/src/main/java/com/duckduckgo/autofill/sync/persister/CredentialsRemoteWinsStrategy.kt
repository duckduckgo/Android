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
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.sync.LoginCredentialEntryResponse
import com.duckduckgo.autofill.sync.LoginCredentialsSync
import com.duckduckgo.autofill.sync.SyncCredentialsMapper
import com.duckduckgo.autofill.sync.SyncLoginCredentials
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import javax.inject.*
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CredentialsRemoteWinsStrategy constructor(
    private val autofillStore: AutofillStore,
    private val syncLoginCredentials: SyncLoginCredentials,
    private val syncCredentialsMapper: SyncCredentialsMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(entries: List<LoginCredentialEntryResponse>): SyncMergeResult<Boolean> {
        Timber.d("Sync-autofill-Persist: ======= MERGING REMOTEWINS =======")
        return kotlin.runCatching {
            entries.forEach { entry ->
                runBlocking(dispatchers.io()) {
                    val localId = syncLoginCredentials.getLocalId(entry.id)
                    Timber.d("Sync-autofill-Persist: >>> $localId found for syncId ${entry.id}")
                    if (localId != null) {
                        if (entry.isDeleted()) {
                            Timber.d("Sync-autofill-Persist: >>> delete local $localId")
                            autofillStore.deleteCredentials(localId)
                            return@runBlocking
                        }
                        autofillStore.getCredentialsWithId(localId)?.also {
                            Timber.d("Sync-autofill-Persist: >>> localCredentials $it found")
                            val updatedCredentials = syncCredentialsMapper.toLoginCredential(entry, localId)
                            Timber.d("Sync-autofill-Persist: >>> update with remote $updatedCredentials")
                            autofillStore.updateCredentials(updatedCredentials)
                        }
                    } else {
                        if (entry.isDeleted()) return@runBlocking
                        val updatedCredentials = syncCredentialsMapper.toLoginCredential(entry, localId)
                        Timber.d("Sync-autofill-Persist: >>> save remote $updatedCredentials")
                        autofillStore.saveCredentials(
                            rawUrl = updatedCredentials.domain ?: "",
                            credentials = updatedCredentials,
                        )?.id?.also { autofillId ->
                            syncLoginCredentials.add(LoginCredentialsSync(syncId = entry.id, id = autofillId))
                        }
                    }
                }
            }
        }.getOrElse {
            Timber.d("Sync-autofill-Persist: merging failed with error $it")
            Error(reason = "Something went wrong")
        }.let {
            Timber.d("Sync-autofill-Persist: merging completed")
            Success(true)
        }
    }
}
