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
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.sync.LoginCredentialEntryResponse
import com.duckduckgo.autofill.sync.LoginCredentialsSync
import com.duckduckgo.autofill.sync.SyncCredentialsMapper
import com.duckduckgo.autofill.sync.SyncLoginCredentials
import com.duckduckgo.autofill.sync.isDeleted
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.sync.api.engine.SyncMergeResult
import com.duckduckgo.sync.api.engine.SyncMergeResult.Error
import com.duckduckgo.sync.api.engine.SyncMergeResult.Success
import javax.inject.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import timber.log.Timber

class CrendentialsDedupStrategy constructor(
    private val autofillStore: AutofillStore,
    private val secureStorage: SecureStorage,
    private val syncLoginCredentials: SyncLoginCredentials,
    private val syncCredentialsMapper: SyncCredentialsMapper,
    private val dispatchers: DispatcherProvider,
) : CredentialsMergeStrategy {
    override fun processEntries(entries: List<LoginCredentialEntryResponse>): SyncMergeResult<Boolean> {
        Timber.d("Sync-autofill-Persist: ======= MERGING DEDUPLICATION =======")
        return kotlin.runCatching {
            entries.forEach { entry ->
                if (entry.isDeleted()) return@forEach

                runBlocking(dispatchers.io()) {
                    val credentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(entry.domain.orEmpty()).firstOrNull()
                    if (credentials.isNullOrEmpty()) {
                        val updatedCredentials = syncCredentialsMapper.toLoginCredential(entry)
                        Timber.d("Sync-autofill-Persist: >>> no duplicate found, save remote $updatedCredentials")
                        autofillStore.saveCredentials(
                            rawUrl = updatedCredentials.domain ?: "",
                            credentials = updatedCredentials,
                        )?.id?.also { autofillId ->
                            syncLoginCredentials.add(LoginCredentialsSync(syncId = entry.id, id = autofillId))
                        }
                    } else {
                        credentials.forEach {
                            val updatedCredentials = syncCredentialsMapper.toLoginCredential(entry)
                            val result = compareCrendetials(it, updatedCredentials)
                            when {
                                result == null -> {
                                    Timber.d("Sync-autofill-Persist: >>> no duplicate found, save remote $updatedCredentials")
                                    autofillStore.saveCredentials(
                                        rawUrl = updatedCredentials.domain ?: "",
                                        credentials = updatedCredentials,
                                    )?.id?.also { autofillId ->
                                        syncLoginCredentials.add(LoginCredentialsSync(syncId = entry.id, id = autofillId))
                                    }
                                }
                                result <= 0 -> {
                                    Timber.d("Sync-autofill-Persist: >>> duplicate found, update remote $updatedCredentials")
                                    updatedCredentials.copy(id = it.details.id).also {
                                        autofillStore.updateCredentials(credentials = it)
                                        syncLoginCredentials.add(LoginCredentialsSync(syncId = entry.id, id = it.id!!))
                                    }
                                }
                                result > 0 -> {
                                    Timber.d("Sync-autofill-Persist: >>> duplicate found, update local $updatedCredentials")
                                    syncLoginCredentials.add(LoginCredentialsSync(syncId = entry.id, id = it.details.id!!))
                                }
                            }
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

    private fun compareCrendetials(
        it: WebsiteLoginDetailsWithCredentials,
        updatedCredentials: LoginCredentials,
    ): Int? {
        val isDuplicated = with(it) {
            details.username == updatedCredentials.username &&
                password == updatedCredentials.password &&
                notes == updatedCredentials.notes
        }
        return when (isDuplicated) {
            true -> it.details.lastUpdatedMillis?.compareTo(updatedCredentials.lastUpdatedMillis ?: 0) ?: 0
            false -> null
        }
    }
}
