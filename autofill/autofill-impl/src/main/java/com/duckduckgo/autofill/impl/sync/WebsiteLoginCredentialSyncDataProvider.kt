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

package com.duckduckgo.autofill.impl.sync

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.store.sync.AutofillSyncStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.*

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataProvider::class)
class WebsiteLoginCredentialSyncDataProvider @Inject constructor(
    private val syncLoginCredentials: SyncLoginCredentials,
    private val autofillRepository: AutofillStore,
    private val autofillSyncStore: AutofillSyncStore,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatchers: DispatcherProvider,
) : SyncableDataProvider {

    override fun getChanges(): SyncChangesRequest {
        val since = autofillSyncStore.modifiedSince
        val updates = if (since == "0") {
            allContent()
        } else {
            changesSince(since)
        }
        val request = formatUpdates(allContent()) //update for updates
        Timber.d("Sync-autofill: request: $request")
        return SyncChangesRequest.empty()
    }

    override fun onSyncDisabled() {
        autofillSyncStore.modifiedSince = "0"
    }

    private fun allContent(): List<LoginCredentialEntry> {
        val values: List<LoginCredentialEntry> = runBlocking {
            withContext(dispatchers.io()) {
                return@withContext autofillRepository.getAllCredentials().first()
            }
        }.map {
            val loginId = it.id ?: return@map null
            val lastUpdatedMillis = it.lastUpdatedMillis ?: return@map null
            val syncId = syncLoginCredentials.getSyncId(loginId)
            LoginCredentialEntry(
                id = syncId,
                client_last_modified = DatabaseDateFormatter.parseMillisIso8601(lastUpdatedMillis),
                domain = it.domain,
                title = it.domainTitle,
                username = it.username,
                password = it.password,
                notes = it.notes,
            )
        }.filterNotNull()

        Timber.d("Sync-autofill: allContent: $values")
        return values
    }

    private fun changesSince(since: String): List<LoginCredentialEntry> {
        Timber.d("Sync-autofill: generating changes since $since")

        val modifiedSinceInMillis = DatabaseDateFormatter.parseIso8601ToMillis(since)

        val values: List<LoginCredentialEntry> = runBlocking {
            withContext(dispatchers.io()) {
                return@withContext autofillRepository.getAllCredentialsModifiedSince(modifiedSinceInMillis)
            }
        }.map {
            val loginId = it.id ?: return@map null
            val lastUpdatedMillis = it.lastUpdatedMillis ?: return@map null
            val syncId = syncLoginCredentials.getSyncId(loginId)
            LoginCredentialEntry(
                id = syncId,
                client_last_modified = DatabaseDateFormatter.parseMillisIso8601(lastUpdatedMillis),
                domain = it.domain,
                title = it.domainTitle,
                username = it.username,
                password = it.password,
                notes = it.notes,
            )
        }.filterNotNull()

        val removedItems = syncLoginCredentials.getRemovedEntitiesSince(since).map {
            LoginCredentialEntry(id = it.syncId, deleted = "1", client_last_modified = it.deleted_at)
        }

        Timber.d("Sync-autofill: modifiedSince: $values")
        Timber.d("Sync-autofill: modifiedSince removed: $removedItems")
        return values + removedItems
    }

    private fun formatUpdates(updates: List<LoginCredentialEntry>): SyncChangesRequest {
        return if (updates.isEmpty()) {
            SyncChangesRequest(CREDENTIALS, "", autofillSyncStore.modifiedSince)
        } else {
            val credentialsUpdates = SyncBookmarkUpdates(updates, autofillSyncStore.modifiedSince)
            val patch = SyncCredentialsRequest(credentialsUpdates, DatabaseDateFormatter.iso8601())
            val allDataJSON = Adapters.patchAdapter.toJson(patch)
            SyncChangesRequest(CREDENTIALS, allDataJSON, autofillSyncStore.modifiedSince)
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().build()
            val patchAdapter: JsonAdapter<SyncCredentialsRequest> = moshi.adapter(SyncCredentialsRequest::class.java)
        }
    }
}

class SyncCredentialsRequest(
    val credentials: SyncBookmarkUpdates,
    val client_timestamp: String,
)

class SyncBookmarkUpdates(
    val updates: List<LoginCredentialEntry>,
    val modified_since: String = "0",
)

data class LoginCredentialEntry(
    val id: String,
    val deleted: String? = null,
    val client_last_modified: String?,
    val domain: String? = null,
    val title: String? = null,
    val username: String? = null,
    val password: String? = null,
    val notes: String? = null,
)
