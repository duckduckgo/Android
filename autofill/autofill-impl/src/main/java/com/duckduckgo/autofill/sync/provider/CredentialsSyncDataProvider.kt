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

package com.duckduckgo.autofill.sync.provider

import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.formatters.time.DatabaseDateFormatter
import com.duckduckgo.app.utils.checkMainThread
import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.autofill.sync.CredentialsSync
import com.duckduckgo.autofill.sync.CredentialsSyncStore
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.sync.api.engine.SyncChangesRequest
import com.duckduckgo.sync.api.engine.SyncableDataProvider
import com.duckduckgo.sync.api.engine.SyncableType.CREDENTIALS
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import javax.inject.*
import kotlinx.coroutines.runBlocking

@ContributesMultibinding(scope = AppScope::class, boundType = SyncableDataProvider::class)
class CredentialsSyncDataProvider @Inject constructor(
    private val credentialsSyncStore: CredentialsSyncStore,
    private val credentialsSync: CredentialsSync,
    private val dispatchers: DispatcherProvider,
    private val appBuildConfig: AppBuildConfig,
) : SyncableDataProvider {

    override fun getChanges(): SyncChangesRequest {
        if (appBuildConfig.isInternalBuild()) checkMainThread()
        return runBlocking(dispatchers.io()) {
            val since = credentialsSyncStore.modifiedSince
            val updates = credentialsSync.getUpdatesSince(since)
            val request = formatUpdates(updates)
            return@runBlocking request
        }
    }

    private fun formatUpdates(updates: List<LoginCredentialEntry>): SyncChangesRequest {
        return if (updates.isEmpty()) {
            SyncChangesRequest(CREDENTIALS, "", credentialsSyncStore.modifiedSince)
        } else {
            val credentialsUpdates = SyncCredentialsUpdates(
                updates = updates,
                modified_since = credentialsSyncStore.modifiedSince,
            )
            val patch = SyncCredentialsRequest(
                credentials = credentialsUpdates,
                client_timestamp = DatabaseDateFormatter.iso8601(),
            )
            val allDataJSON = Adapters.patchAdapter.toJson(patch)
            SyncChangesRequest(CREDENTIALS, allDataJSON, credentialsSyncStore.modifiedSince)
        }
    }

    private class Adapters {
        companion object {
            private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
            val patchAdapter: JsonAdapter<SyncCredentialsRequest> = moshi.adapter(SyncCredentialsRequest::class.java).serializeNulls()
        }
    }
}

class SyncCredentialsRequest(
    val credentials: SyncCredentialsUpdates,
    val client_timestamp: String,
)

class SyncCredentialsUpdates(
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
