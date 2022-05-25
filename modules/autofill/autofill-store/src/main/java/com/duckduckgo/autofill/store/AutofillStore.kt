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

import androidx.core.net.toUri
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginCredentials
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import timber.log.Timber

class SecureStoreBackedAutofillStore(val secureStorage: SecureStorage) : AutofillStore {

    override suspend fun getCredentials(rawUrl: String): List<LoginCredentials> {
        val url = rawUrl.extractOriginFromUrl()

        val storedCredentials = secureStorage.getWebsiteLoginCredentialsForDomain(url).firstOrNull() ?: emptyList()
        Timber.v("Found %d credentials for %s", storedCredentials.size, url)

        return storedCredentials.map { it.toLoginCredentials() }
    }

    override suspend fun saveCredentials(rawUrl: String, credentials: LoginCredentials) {
        val url = rawUrl.extractOriginFromUrl()
        Timber.i("Saving login credentials for %s. username=%s", url, credentials.username)

        val loginDetails = WebsiteLoginDetails(domain = url, username = credentials.username)
        val webSiteLoginCredentials = WebsiteLoginCredentials(loginDetails, password = credentials.password)

        // todo this should be handled internally by secure storage
        withContext(Dispatchers.IO) {
            secureStorage.addWebsiteLoginCredential(webSiteLoginCredentials)
        }
    }

    override suspend fun getAllCredentials(): List<LoginCredentials> = withContext(Dispatchers.IO) {
        val savedCredentials = secureStorage.getAllWebsiteLoginCredentials().firstOrNull() ?: emptyList()
        return@withContext savedCredentials.map { it.toLoginCredentials() }
    }

    private fun WebsiteLoginCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password
        )
    }

    private fun String.extractOriginFromUrl(): String {
        val url = this.toUri()
        val scheme = if (url.scheme != null) "${url.scheme}://" else ""

        return String.format("%s%s", scheme, url.host).also {
            Timber.i("Extracted origin from URL.\ninput=%s\noutput=%s\nhost=%s", this, it, url.host)
        }
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
