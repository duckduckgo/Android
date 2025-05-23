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
import com.duckduckgo.autofill.impl.PasswordStoreEventListener
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetails
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.autofill.store.CredentialsSyncMetadataEntity
import com.duckduckgo.autofill.sync.provider.CredentialsSyncLocalValidationFeature
import com.duckduckgo.autofill.sync.provider.LoginCredentialEntry
import com.duckduckgo.common.utils.formatters.time.DatabaseDateFormatter
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.SyncCrypto
import dagger.SingleInstanceIn
import javax.inject.*
import kotlinx.coroutines.flow.firstOrNull
import logcat.logcat

typealias Iso8601String = String

@SingleInstanceIn(AppScope::class)
class CredentialsSync @Inject constructor(
    private val secureStorage: SecureStorage,
    private val credentialsSyncStore: CredentialsSyncStore,
    private val credentialsSyncMetadata: CredentialsSyncMetadata,
    private val syncCrypto: SyncCrypto,
    private val credentialsSyncLocalValidationFeature: CredentialsSyncLocalValidationFeature,
    private val passwordStoreEventPlugins: PluginPoint<PasswordStoreEventListener>,
) {

    suspend fun initMetadata() {
        credentialsSyncStore.serverModifiedSince = "0"
        credentialsSyncStore.clientModifiedSince = "0"

        secureStorage.websiteLoginDetailsWithCredentials().firstOrNull()
            ?.mapNotNull { it.details.id }
            ?.let { autofillIds ->
                credentialsSyncMetadata.initializeDatabase(autofillIds)
            }

        logcat { "CredentialsSync: initMetadata ${credentialsSyncMetadata.getAllObservable().firstOrNull()}" }
    }

    suspend fun getUpdatesSince(since: Iso8601String): List<LoginCredentialEntry> {
        credentialsSyncStore.startTimeStamp = SyncDateProvider.now()
        val changes = if (since == "0") {
            allContentAsUpdates()
        } else {
            changesSince(since)
        }
        val toRetryItems = getInvalidCredentialsLocalId()
            .mapNotNull { localId -> secureStorage.getWebsiteLoginDetailsWithCredentials(localId) }
            .mapToLoginCredentialEntry()

        val allChanges = changes + toRetryItems

        val filteredChanges = allChanges.validItems()
        markSavedSitesAsInvalid((allChanges - filteredChanges.toSet()).map { it.id })

        return filteredChanges
    }

    suspend fun getCredentialWithSyncId(syncId: String): LoginCredentials? {
        val localId = credentialsSyncMetadata.getLocalId(syncId)
        val localCredential = localId?.let { secureStorage.getWebsiteLoginDetailsWithCredentials(localId)?.toLoginCredentials() }
        logcat { "CredentialsSync: >>> getCredentialWithSyncId $syncId - credential $localCredential found" }
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
            passwordStoreEventPlugins.getPlugins().forEach { it.onCredentialAdded(autofillId) }
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

    suspend fun getInvalidCredentials(): List<LoginCredentials> {
        return getInvalidCredentialsLocalId().mapNotNull { localId -> getCredentialWithId(localId) }
    }

    private fun markSavedSitesAsInvalid(ids: List<String>) {
        logcat { "CredentialsSync: Storing invalid items: $ids" }
        credentialsSyncStore.invalidEntitiesIds = ids
    }

    private suspend fun allContentAsUpdates() = secureStorage.websiteLoginDetailsWithCredentials().firstOrNull().mapToLoginCredentialEntry()

    private suspend fun changesSince(since: Iso8601String): List<LoginCredentialEntry> {
        logcat { "CredentialsSync: generating changes since $since" }
        credentialsSyncMetadata.getAllCredentials().forEach {
            logcat { "CredentialsSync: syncMetadata $it" }
        }

        val changes = credentialsSyncMetadata.getChangesSince(since).map {
            secureStorage.getWebsiteLoginDetailsWithCredentials(it.localId)
        }.filterNotNull()

        val removedItems = credentialsSyncMetadata.getRemovedEntitiesSince(since).map {
            LoginCredentialEntry(id = it.syncId, deleted = "1", client_last_modified = it.deleted_at)
        }

        logcat { "CredentialsSync: modifiedSince changes: $changes" }
        logcat { "CredentialsSync: modifiedSince removed: $removedItems" }

        val changesEntries = changes.mapToLoginCredentialEntry()

        return changesEntries + removedItems
    }

    private fun getInvalidCredentialsLocalId(): List<Long> {
        return credentialsSyncStore.invalidEntitiesIds.takeIf { it.isNotEmpty() }?.mapNotNull { id ->
            credentialsSyncMetadata.getLocalId(id)
        } ?: emptyList()
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

    private fun List<LoginCredentialEntry>.validItems(): List<LoginCredentialEntry> {
        if (credentialsSyncLocalValidationFeature.self().isEnabled().not()) return this // Skip validation if feature is disabled
        return this.filter {
            (it.title?.length ?: 0) < MAX_ENCRYPTED_TITLE_LENGTH &&
                (it.domain?.length ?: 0) < MAX_ENCRYPTED_DOMAIN_LENGTH &&
                (it.notes?.length ?: 0) < MAX_ENCRYPTED_NOTES_LENGTH &&
                (it.username?.length ?: 0) < MAX_ENCRYPTED_USERNAME_LENGTH &&
                (it.password?.length ?: 0) < MAX_ENCRYPTED_PASSWORD_LENGTH
        }
    }

    companion object {
        const val MAX_ENCRYPTED_TITLE_LENGTH = 3_000
        const val MAX_ENCRYPTED_DOMAIN_LENGTH = 1_000
        const val MAX_ENCRYPTED_USERNAME_LENGTH = 1_000
        const val MAX_ENCRYPTED_PASSWORD_LENGTH = 1_000
        const val MAX_ENCRYPTED_NOTES_LENGTH = 10_000
    }
}
