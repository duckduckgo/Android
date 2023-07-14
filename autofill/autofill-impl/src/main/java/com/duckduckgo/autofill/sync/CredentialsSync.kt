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

import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.provider.LoginCredentialEntry
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.sync.api.SyncCrypto
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
class CredentialsSync @Inject constructor(
    private val autofillStore: AutofillStore,
    private val secureStorage: SecureStorage,
    private val credentialsSyncMetadata: CredentialsSyncMetadata,
    private val syncCrypto: SyncCrypto,
) {
    suspend fun getUpdatesSince(since: String): List<LoginCredentialEntry> {
        return if (since == "0") {
            allContentAsUpdates()
        } else {
            changesSince(since)
        }
    }

    suspend fun getCredentialWithSyncId(syncId: String): LoginCredentials? {
        val localId = credentialsSyncMetadata.getLocalId(syncId)
        Timber.d("Sync-autofill-Persist: >>> $localId found for syncId $syncId")
        val localCredential = localId?.let { autofillStore.getCredentialsWithId(localId) }
        Timber.d("Sync-autofill-Persist: >>> localCredential $localCredential found")
        return localCredential
    }

    suspend fun getCredentialWithId(localId: Long): LoginCredentials? {
        return autofillStore.getCredentialsWithId(localId)
    }

    suspend fun getCredentialsForDomain(domain: String?): List<LoginCredentials> {
        return secureStorage.websiteLoginDetailsWithCredentialsForDomain(domain.orEmpty()).firstOrNull()?.map {
            it.toLoginCredentials()
        } ?: emptyList()
    }

    suspend fun saveCredential(
        loginCredential: LoginCredentials,
        remoteId: String,
    ) {
        autofillStore.saveCredentials(
            rawUrl = loginCredential.domain ?: "",
            credentials = loginCredential,
        )?.id?.also { autofillId ->
            credentialsSyncMetadata.addOrUpdate(CredentialsSyncMetadataEntity(syncId = remoteId, id = autofillId))
        }
    }

    suspend fun updateCredentials(
        loginCredential: LoginCredentials,
        remoteId: String,
    ) {
        autofillStore.updateCredentials(loginCredential)?.id?.also { autofillId ->
            credentialsSyncMetadata.addOrUpdate(CredentialsSyncMetadataEntity(syncId = remoteId, id = autofillId))
        }
    }

    suspend fun deleteCredential(localId: Long) {
        secureStorage.deleteWebsiteLoginDetailsWithCredentials(localId)
        credentialsSyncMetadata.removeEntityWithLocalId(localId)
    }

    private suspend fun allContentAsUpdates() = autofillStore.getAllCredentials().firstOrNull().mapToLoginCredentialEntry()

    private suspend fun changesSince(since: String): List<LoginCredentialEntry> {
        Timber.d("Sync-autofill: generating changes since $since")

        val modifiedSinceInMillis = DatabaseDateFormatter.parseIso8601ToMillis(since)

        val values = autofillStore
            .getAllCredentialsModifiedSince(modifiedSinceInMillis)
            .mapToLoginCredentialEntry()

        val removedItems = credentialsSyncMetadata.getRemovedEntitiesSince(since).map {
            LoginCredentialEntry(id = it.syncId, deleted = "1", client_last_modified = it.deleted_at)
        }

        Timber.d("Sync-autofill: modifiedSince: $values")
        Timber.d("Sync-autofill: modifiedSince removed: $removedItems")
        return values + removedItems
    }

    private fun String?.encrypt(): String? {
        if (this == null) return null
        if (this.isEmpty()) return ""
        return syncCrypto.encrypt(this)
    }

    private fun List<LoginCredentials>?.mapToLoginCredentialEntry(): List<LoginCredentialEntry> {
        return this?.map {
            val loginId = it.id ?: return@map null
            val lastUpdatedMillis = it.lastUpdatedMillis ?: 0L
            val syncId = credentialsSyncMetadata.getSyncId(loginId) ?: credentialsSyncMetadata.createSyncId(loginId)
            LoginCredentialEntry(
                id = syncId,
                client_last_modified = DatabaseDateFormatter.parseMillisIso8601(lastUpdatedMillis),
                domain = it.domain.encrypt(),
                title = it.domainTitle.encrypt(),
                username = it.username.encrypt(),
                password = it.password.encrypt(),
                notes = it.notes.encrypt(),
            )
        }?.filterNotNull() ?: emptyList()
    }

    private fun WebsiteLoginDetailsWithCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password,
            domainTitle = details.domainTitle,
            notes = notes,
            lastUpdatedMillis = details.lastUpdatedMillis,
        )
    }
}
