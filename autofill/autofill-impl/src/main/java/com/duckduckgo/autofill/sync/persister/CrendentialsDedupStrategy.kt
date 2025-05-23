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

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.sync.*
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import kotlinx.coroutines.runBlocking
import logcat.LogPriority.INFO
import logcat.logcat

class CredentialsDedupStrategy(
    private val credentialsSync: CredentialsSync,
    private val credentialsSyncMapper: CredentialsSyncMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(
        credentials: credentialsSyncEntries,
        clientModifiedSince: String,
    ): SyncMergeResult {
        logcat { "Sync-autofill-Persist: ======= MERGING DEDUPLICATION =======" }

        return kotlin.runCatching {
            runBlocking(dispatchers.io()) {
                credentials.entries.forEach { remoteEntry ->
                    if (remoteEntry.isDeleted()) return@forEach

                    val remoteLoginCredential = mapRemoteToLocalLoginCredential(remoteEntry, credentials.last_modified)

                    val localMatchesForDomain = credentialsSync.getCredentialsForDomain(remoteLoginCredential.domain)
                    if (localMatchesForDomain.isEmpty()) {
                        logcat { "Sync-autofill-Persist: >>> no duplicate found, save remote $remoteLoginCredential" }
                        credentialsSync.saveCredential(remoteLoginCredential, remoteId = remoteEntry.id)
                    } else {
                        val duplicateFound = findDuplicates(localMatchesForDomain, remoteLoginCredential, remoteEntry.id)
                        if (duplicateFound) return@forEach

                        logcat { "Sync-autofill-Persist: >>> no duplicate found, save remote $remoteLoginCredential" }
                        credentialsSync.saveCredential(remoteLoginCredential, remoteId = remoteEntry.id)
                    }
                }
            }
        }.getOrElse {
            logcat { "Sync-autofill-Persist: merging failed with error $it" }
            return Error(reason = "DeDup merge failed with error $it")
        }.let {
            logcat { "Sync-autofill-Persist: merging completed" }
            Success()
        }
    }

    private suspend fun findDuplicates(
        localMatchesForDomain: List<LoginCredentials>,
        remoteLoginCredential: LoginCredentials,
        remoteId: String,
    ): Boolean {
        var duplicateFound = false
        localMatchesForDomain.forEach { localMatch ->
            val result = compareCredentials(localMatch, remoteLoginCredential)
            when {
                result == null -> {}
                result <= 0 -> {
                    logcat { "Sync-autofill-Persist: >>> duplicate found $localMatch, update remote $remoteLoginCredential" }
                    remoteLoginCredential.copy(id = localMatch.id).also {
                        credentialsSync.updateCredentials(it, remoteId = remoteId)
                    }
                    duplicateFound = true
                }
                result > 0 -> {
                    logcat { "Sync-autofill-Persist: >>> duplicate found $localMatch, update local $localMatch" }
                    val localCredential = credentialsSync.getCredentialWithId(localMatch.id!!)!!
                    credentialsSync.updateCredentials(
                        loginCredential = localCredential,
                        remoteId = remoteId,
                    )
                    duplicateFound = true
                }
            }
        }
        return duplicateFound
    }

    private fun compareCredentials(
        localCredential: LoginCredentials,
        loginCredential: LoginCredentials,
    ): Int? {
        logcat(INFO) { "Duplicate: compareCredentials local $localCredential vs remote $loginCredential" }
        val isDuplicated = with(localCredential) {
            domain.orEmpty() == loginCredential.domain.orEmpty() &&
                username.orEmpty() == loginCredential.username.orEmpty() &&
                password.orEmpty() == loginCredential.password.orEmpty() &&
                notes.orEmpty() == loginCredential.notes.orEmpty()
        }
        val comparison = when (isDuplicated) {
            true -> localCredential.lastUpdatedMillis?.compareTo(loginCredential.lastUpdatedMillis ?: 0) ?: 0
            false -> null
        }

        return comparison
    }

    private fun mapRemoteToLocalLoginCredential(
        remoteEntry: CredentialsSyncEntryResponse,
        clientModifiedSince: String,
    ): LoginCredentials {
        return credentialsSyncMapper.toLoginCredential(
            remoteEntry = remoteEntry,
            localId = null,
            lastModified = clientModifiedSince,
        )
    }
}
