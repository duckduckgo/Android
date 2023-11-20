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

import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.provider.LoginCredentialEntry
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.sync.api.SyncCrypto
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.flow.firstOrNull
import timber.log.Timber

typealias Iso8601String = String

@SingleInstanceIn(AppScope::class)
class CredentialsSync @Inject constructor(
    private val secureStorage: SecureStorage,
    private val credentialsSyncStore: CredentialsSyncStore,
    private val credentialsSyncMetadata: CredentialsSyncMetadata,
    private val syncCrypto: SyncCrypto,
) {

    suspend fun initMetadata() {
        credentialsSyncStore.serverModifiedSince = "0"
        credentialsSyncStore.clientModifiedSince = "0"

        secureStorage.websiteLoginDetailsWithCredentials().firstOrNull()
            ?.mapNotNull { it.details.id }
            ?.let { autofillIds ->
                credentialsSyncMetadata.initializeDatabase(autofillIds)
            }

        Timber.i("CredentialsSync: initMetadata ${credentialsSyncMetadata.getAllObservable().firstOrNull()}")
    }

    suspend fun getUpdatesSince(since: Iso8601String): List<LoginCredentialEntry> {
        credentialsSyncStore.startTimeStamp = SyncDateProvider.now()
        return if (since == "0") {
            allContentAsUpdates()
        } else {
            changesSince(since)
        }
    }

    suspend fun getCredentialWithSyncId(syncId: String): LoginCredentials? {
        val localId = credentialsSyncMetadata.getLocalId(syncId)
        val localCredential = localId?.let { secureStorage.getWebsiteLoginDetailsWithCredentials(localId)?.toLoginCredentials() }
        Timber.d("CredentialsSync: >>> getCredentialWithSyncId $syncId - credential $localCredential found")
        return localCredential
    }

    suspend fun getCredentialWithId(localId: Long): LoginCredentials? {
        return secureStorage.getWebsiteLoginDetailsWithCredentials(localId)?.toLoginCredentials()
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
        val loginDetails = WebsiteLoginDetails(
            id = loginCredential.id,
            domain = loginCredential.domain,
            username = loginCredential.username,
            domainTitle = loginCredential.domainTitle,
            lastUpdatedMillis = loginCredential.lastUpdatedMillis,
        )
        val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(
            details = loginDetails,
            password = loginCredential.password,
            notes = loginCredential.notes,
        )

        secureStorage.addWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)?.details?.id?.let { autofillId ->
            credentialsSyncMetadata.addOrUpdate(
                CredentialsSyncMetadataEntity(syncId = remoteId, localId = autofillId, deleted_at = null, modified_at = null),
            )
        }
    }

    suspend fun updateCredentials(
        loginCredential: LoginCredentials,
        remoteId: String,
    ) {
        val loginDetails = WebsiteLoginDetails(
            id = loginCredential.id,
            domain = loginCredential.domain,
            username = loginCredential.username,
            domainTitle = loginCredential.domainTitle,
            lastUpdatedMillis = loginCredential.lastUpdatedMillis,
        )
        val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(
            details = loginDetails,
            password = loginCredential.password,
            notes = loginCredential.notes,
        )
        secureStorage.updateWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)?.details?.id?.let { autofillId ->
            credentialsSyncMetadata.addOrUpdate(
                CredentialsSyncMetadataEntity(syncId = remoteId, localId = autofillId, deleted_at = null, modified_at = null),
            )
        }
    }

    suspend fun deleteCredential(localId: Long) {
        secureStorage.deleteWebsiteLoginDetailsWithCredentials(localId)
        credentialsSyncMetadata.removeEntityWith(localId)
    }

    private suspend fun allContentAsUpdates() = secureStorage.websiteLoginDetailsWithCredentials().firstOrNull().mapToLoginCredentialEntry()

    private suspend fun changesSince(since: Iso8601String): List<LoginCredentialEntry> {
        Timber.d("CredentialsSync: generating changes since $since")
        credentialsSyncMetadata.getAllCredentials().forEach {
            Timber.i("CredentialsSync: syncMetadata $it")
        }

        val changes = credentialsSyncMetadata.getChangesSince(since).map {
            secureStorage.getWebsiteLoginDetailsWithCredentials(it.localId)
        }.filterNotNull()

        val removedItems = credentialsSyncMetadata.getRemovedEntitiesSince(since).map {
            LoginCredentialEntry(id = it.syncId, deleted = "1", client_last_modified = it.deleted_at)
        }

        Timber.d("CredentialsSync: modifiedSince changes: $changes")
        Timber.d("CredentialsSync: modifiedSince removed: $removedItems")

        return changes.mapToLoginCredentialEntry() + removedItems
    }

    private fun String?.encrypt(): String? {
        if (this == null) return null
        if (this.isEmpty()) return ""
        return syncCrypto.encrypt(this)
    }

    private fun List<WebsiteLoginDetailsWithCredentials>?.mapToLoginCredentialEntry(): List<LoginCredentialEntry> {
        return this?.map {
            val loginId = it.details.id ?: return@map null
            val lastUpdatedMillis = it.details.lastUpdatedMillis ?: 0L
            val syncId = credentialsSyncMetadata.getSyncMetadata(loginId)?.syncId ?: credentialsSyncMetadata.createSyncId(loginId)
            LoginCredentialEntry(
                id = syncId,
                client_last_modified = DatabaseDateFormatter.parseMillisIso8601(lastUpdatedMillis),
                domain = it.details.domain.encrypt(),
                title = it.details.domainTitle.encrypt(),
                username = it.details.username.encrypt(),
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
