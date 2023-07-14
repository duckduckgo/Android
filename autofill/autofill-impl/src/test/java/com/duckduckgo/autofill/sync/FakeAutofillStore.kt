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

import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.api.store.AutofillStore.ContainsCredentialsResult
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class FakeAutofillStore(
    private val secureStorage: SecureStorage,
) : AutofillStore {

    override var autofillEnabled: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override val autofillAvailable: Boolean
        get() = TODO("Not yet implemented")
    override var hasEverBeenPromptedToSaveLogin: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var monitorDeclineCounts: Boolean
        get() = TODO("Not yet implemented")
        set(value) {}
    override var autofillDeclineCount: Int
        get() = TODO("Not yet implemented")
        set(value) {}

    override suspend fun getCredentials(rawUrl: String): List<LoginCredentials> {
        return secureStorage.websiteLoginDetailsWithCredentialsForDomain(rawUrl).firstOrNull()?.map {
            LoginCredentials(
                id = it.details.id,
                domain = it.details.domain,
                username = it.details.username,
                password = it.password,
                domainTitle = it.details.domainTitle,
                notes = it.notes,
                lastUpdatedMillis = it.details.lastUpdatedMillis,
            )
        } ?: emptyList()
    }

    override suspend fun getCredentialsWithId(id: Long): LoginCredentials? {
        return secureStorage.getWebsiteLoginDetailsWithCredentials(id)?.toLoginCredentials()
    }

    override suspend fun saveCredentials(
        rawUrl: String,
        credentials: LoginCredentials,
    ): LoginCredentials? {
        return secureStorage.addWebsiteLoginDetailsWithCredentials(credentials.toWebsiteLoginCredentials())?.toLoginCredentials()
    }

    override suspend fun updateCredentials(
        rawUrl: String,
        credentials: LoginCredentials,
        updateType: CredentialUpdateType,
    ): LoginCredentials? {
        TODO("Not yet implemented")
    }

    override suspend fun updateCredentials(credentials: LoginCredentials): LoginCredentials? {
        return secureStorage.updateWebsiteLoginDetailsWithCredentials(credentials.toWebsiteLoginCredentials())?.toLoginCredentials()
    }

    override suspend fun getAllCredentials(): Flow<List<LoginCredentials>> {
        return secureStorage.websiteLoginDetailsWithCredentials()
            .map { list ->
                list.map { it.toLoginCredentials() }
            }
    }

    override suspend fun getAllCredentialsModifiedSince(modifiedSince: Long): List<LoginCredentials> {
        return secureStorage.websiteLoginDetailsWithCredentialsModifiedSince(modifiedSince)
            .map {
                it.toLoginCredentials()
            }
    }

    override suspend fun getCredentialCount(): Flow<Int> {
        TODO("Not yet implemented")
    }

    override suspend fun deleteCredentials(id: Long): LoginCredentials? {
        secureStorage.deleteWebsiteLoginDetailsWithCredentials(id)
        return null
    }

    override suspend fun reinsertCredentials(credentials: LoginCredentials) {
        TODO("Not yet implemented")
    }

    override suspend fun containsCredentials(
        rawUrl: String,
        username: String?,
        password: String?,
    ): ContainsCredentialsResult {
        TODO("Not yet implemented")
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

    private fun LoginCredentials.toWebsiteLoginCredentials(): WebsiteLoginDetailsWithCredentials {
        return WebsiteLoginDetailsWithCredentials(
            details = WebsiteLoginDetails(
                domain = domain,
                username = username,
                id = id,
                domainTitle = domainTitle,
                lastUpdatedMillis = lastUpdatedMillis,
            ),
            password = password,
            notes = notes,
        )
    }
}
