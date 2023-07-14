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
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.sync.CredentialsSync
import com.duckduckgo.autofill.sync.CredentialsSyncMapper
import com.duckduckgo.autofill.sync.CrendentialsSyncEntries
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CrendentialsDedupStrategy constructor(
    private val credentialsSync: CredentialsSync,
    private val credentialsSyncMapper: CredentialsSyncMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(credentials: CrendentialsSyncEntries): SyncMergeResult<Boolean> {
        Timber.d("Sync-autofill-Persist: ======= MERGING DEDUPLICATION =======")

        return kotlin.runCatching {
            credentials.entries.forEach { remoteEntry ->
                if (remoteEntry.isDeleted()) return@forEach

                runBlocking(dispatchers.io()) {
                    val remoteLoginCredential: LoginCredentials = credentialsSyncMapper.toLoginCredential(
                        remoteEntry,
                        lastModified = credentials.last_modified, // remoteEntry.last_modified is always null
                    )
                    val localMatchesForDomain = credentialsSync.getCredentialsForDomain(remoteLoginCredential.domain)
                    if (localMatchesForDomain.isNullOrEmpty()) {
                        Timber.d("Sync-autofill-Persist: >>> no duplicate found, save remote $remoteLoginCredential")
                        credentialsSync.saveCredential(remoteLoginCredential, remoteId = remoteEntry.id)
                    } else {
                        var duplicateFound = false
                        localMatchesForDomain.forEach { localMatch ->
                            val result = compareCredentials(localMatch, remoteLoginCredential)
                            when {
                                result == null -> {}
                                result <= 0 -> {
                                    Timber.d("Sync-autofill-Persist: >>> duplicate found, update remote $remoteLoginCredential")
                                    remoteLoginCredential.copy(id = localMatch.id).also {
                                        credentialsSync.updateCredentials(it, remoteId = remoteEntry.id)
                                    }
                                    duplicateFound = true
                                }
                                result > 0 -> {
                                    Timber.d("Sync-autofill-Persist: >>> duplicate found, update local $localMatch")
                                    val localCredential = credentialsSync.getCredentialWithId(localMatch.id!!)!!
                                    credentialsSync.updateCredentials(
                                        loginCredential = localCredential,
                                        remoteId = remoteEntry.id,
                                    )
                                    duplicateFound = true
                                }
                            }
                        }
                        if (duplicateFound) return@runBlocking

                        Timber.d("Sync-autofill-Persist: >>> no duplicate found, save remote $remoteLoginCredential")
                        credentialsSync.saveCredential(remoteLoginCredential, remoteId = remoteEntry.id)
                    }
                }
            }
        }.getOrElse {
            Timber.d("Sync-autofill-Persist: merging failed with error $it")
            return Error(reason = "DeDup merge failed with error $it")
        }.let {
            Timber.d("Sync-autofill-Persist: merging completed")
            Success(true)
        }
    }

    private fun compareCredentials(
        localCredential: LoginCredentials,
        loginCredential: LoginCredentials,
    ): Int? {
        val isDuplicated = with(localCredential) {
            domain.orEmpty() == loginCredential.domain.orEmpty() &&
                username.orEmpty() == loginCredential.username.orEmpty() &&
                password.orEmpty() == loginCredential.password.orEmpty() &&
                notes.orEmpty() == loginCredential.notes.orEmpty()
        }
        return when (isDuplicated) {
            true -> localCredential.lastUpdatedMillis?.compareTo(loginCredential.lastUpdatedMillis ?: 0) ?: 0
            false -> null
        }
    }
}
