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

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.duckduckgo.app.global.extractSchemeAndDomain
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber

class SecureStoreBackedAutofillStore(
    private val secureStorage: SecureStorage,
    private val applicationContext: Context
) : AutofillStore {

    private val prefs: SharedPreferences by lazy {
        applicationContext.getSharedPreferences(FILENAME, Context.MODE_PRIVATE)
    }

    override var autofillEnabled: Boolean
        get() = prefs.getBoolean(AUTOFILL_ENABLED, true)
        set(value) = prefs.edit { putBoolean(AUTOFILL_ENABLED, value) }

    override suspend fun getCredentials(rawUrl: String): List<LoginCredentials> {
        Timber.i("Querying secure store for stored credentials. rawUrl: %s, extractedDomain:%s", rawUrl, rawUrl.extractSchemeAndDomain())
        val url = rawUrl.extractSchemeAndDomain() ?: return emptyList()

        val storedCredentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull() ?: emptyList()
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
        val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(loginDetails, password = credentials.password)

        secureStorage.addWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)
    }

    override suspend fun getAllCredentials(): Flow<List<LoginCredentials>> {
        return secureStorage.websiteLoginDetailsWithCredentials()
            .map { list ->
                list.map { it.toLoginCredentials() }
            }
    }

    override suspend fun deleteCredentials(id: Int) {
        secureStorage.deleteWebsiteLoginDetailsWithCredentials(id)
    }

    override suspend fun updateCredentials(credentials: LoginCredentials) {
        secureStorage.updateWebsiteLoginDetailsWithCredentials(credentials.toWebsiteLoginCredentials())
    }

    private fun WebsiteLoginDetailsWithCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password
        )
    }

    private fun LoginCredentials.toWebsiteLoginCredentials(): WebsiteLoginDetailsWithCredentials {
        return WebsiteLoginDetailsWithCredentials(
            details = WebsiteLoginDetails(domain = domain, username = username, id = id),
            password = password
        )
    }

    companion object {
        const val FILENAME = "com.duckduckgo.autofill.store.autofill_store"
        const val AUTOFILL_ENABLED = "autofill_enabled"
    }
}

@Module
@ContributesTo(AppScope::class)
class AutofillStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    fun autofillStore(secureStorage: SecureStorage, context: Context): AutofillStore {
        return SecureStoreBackedAutofillStore(secureStorage, context)
    }
}
