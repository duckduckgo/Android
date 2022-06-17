/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.store

import com.duckduckgo.app.global.extractSchemeAndDomain
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginCredentials
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.*
import timber.log.Timber

class SecureStoreBackedAutofillStore(val secureStorage: SecureStorage) : AutofillStore {

    override suspend fun getCredentials(rawUrl: String): List<LoginCredentials> {
        Timber.i("Querying secure store for stored credentials. rawUrl: %s, extractedDomain:%s", rawUrl, rawUrl.extractSchemeAndDomain())
        val url = rawUrl.extractSchemeAndDomain() ?: return emptyList()

        val storedCredentials = secureStorage.websiteLoginCredentialsForDomain(url).firstOrNull() ?: emptyList()
        Timber.v("Found %d credentials for %s", storedCredentials.size, url)

        return storedCredentials.map { it.toLoginCredentials() }
    }

    override suspend fun saveCredentials(
        rawUrl: String,
        credentials: LoginCredentials
    ) {
        val url = rawUrl.extractSchemeAndDomain()
        if (url == null) {
            Timber.w("Cannot save credentials as given url was in an unexpected format. Original url: %s", rawUrl)
            return
        }

        Timber.i("Saving login credentials for %s. username=%s", url, credentials.username)

        val loginDetails = WebsiteLoginDetails(domain = url, username = credentials.username)
        val webSiteLoginCredentials = WebsiteLoginCredentials(loginDetails, password = credentials.password)

        secureStorage.addWebsiteLoginCredential(webSiteLoginCredentials)
    }

    override suspend fun getAllCredentials(): Flow<List<LoginCredentials>> {
        return secureStorage.websiteLoginCredentials()
            .map { list ->
                list.map { it.toLoginCredentials() }
            }
    }

    override suspend fun deleteCredentials(id: Int) {
        secureStorage.deleteWebsiteLoginCredentials(id)
    }

    override suspend fun updateCredentials(credentials: LoginCredentials) {
        secureStorage.updateWebsiteLoginCredentials(credentials.toWebsiteLoginCredentials())
    }

    private fun WebsiteLoginCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password
        )
    }

    private fun LoginCredentials.toWebsiteLoginCredentials(): WebsiteLoginCredentials {
        return WebsiteLoginCredentials(
            details = WebsiteLoginDetails(domain = domain, username = username, id = id),
            password = password
        )
    }
}

@Module
@ContributesTo(AppScope::class)
class AutofillStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun autofillStore(secureStorage: SecureStorage): AutofillStore {
        return SecureStoreBackedAutofillStore(secureStorage)
    }
}
