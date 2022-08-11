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
import com.duckduckgo.autofill.InternalTestUserChecker
import com.duckduckgo.autofill.domain.app.LoginCredentials
import com.duckduckgo.autofill.store.AutofillStore.ContainsCredentialsResult
import com.duckduckgo.autofill.store.AutofillStore.ContainsCredentialsResult.NoMatch
import com.duckduckgo.securestorage.api.SecureStorage
import com.duckduckgo.securestorage.api.WebsiteLoginDetails
import com.duckduckgo.securestorage.api.WebsiteLoginDetailsWithCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber

class SecureStoreBackedAutofillStore(
    private val secureStorage: SecureStorage,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val lastUpdatedTimeProvider: LastUpdatedTimeProvider,
    private val autofillPrefsStore: AutofillPrefsStore
) : AutofillStore {

    override val autofillAvailable: Boolean
        get() = internalTestUserChecker.isInternalTestUser && secureStorage.canAccessSecureStorage()

    override var autofillEnabled: Boolean
        get() = autofillPrefsStore.isEnabled
        set(value) {
            autofillPrefsStore.isEnabled = value
        }

    override var showOnboardingWhenOfferingToSaveLogin: Boolean
        get() = autofillPrefsStore.showOnboardingWhenOfferingToSaveLogin
        set(value) {
            autofillPrefsStore.showOnboardingWhenOfferingToSaveLogin = value
        }

    override suspend fun getCredentials(rawUrl: String): List<LoginCredentials> {
        return if (autofillEnabled && autofillAvailable) {
            Timber.i("Querying secure store for stored credentials. rawUrl: %s, extractedDomain:%s", rawUrl, rawUrl.extractSchemeAndDomain())
            val url = rawUrl.extractSchemeAndDomain() ?: return emptyList()

            val storedCredentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull() ?: emptyList()
            Timber.v("Found %d credentials for %s", storedCredentials.size, url)

            storedCredentials.map { it.toLoginCredentials() }
        } else {
            emptyList()
        }
    }

    override suspend fun getCredentialsWithId(id: Long): LoginCredentials? =
        secureStorage.getWebsiteLoginDetailsWithCredentials(id)?.toLoginCredentials()

    override suspend fun saveCredentials(
        rawUrl: String,
        credentials: LoginCredentials
    ): Long? {
        val url = rawUrl.extractSchemeAndDomain()
        if (url == null) {
            Timber.w("Cannot save credentials as given url was in an unexpected format. Original url: %s", rawUrl)
            return null
        }

        Timber.i("Saving login credentials for %s. username=%s", url, credentials.username)

        val loginDetails = WebsiteLoginDetails(
            domain = url,
            username = credentials.username,
            domainTitle = credentials.domainTitle,
            notes = credentials.notes,
            lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis()
        )
        val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(loginDetails, password = credentials.password)

        val newId = secureStorage.addWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)

        showOnboardingWhenOfferingToSaveLogin = false

        return newId
    }

    override suspend fun updateCredentials(
        rawUrl: String,
        credentials: LoginCredentials
    ) {
        val url = rawUrl.extractSchemeAndDomain()
        if (url == null) {
            Timber.w("Cannot update credentials as given url was in an unexpected format. Original url: %s", rawUrl)
            return
        }

        val matchingCredentials =
            secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull()?.filter { it.details.username == credentials.username }
        if (matchingCredentials.isNullOrEmpty()) {
            Timber.w("Cannot update credentials as no credentials were found for %s", url)
            return
        }

        Timber.i("Updating %d saved login credentials for %s. username=%s", matchingCredentials.size, url, credentials.username)

        matchingCredentials.forEach {
            val modifiedDetails = it.details.copy(username = credentials.username, lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis())
            val modified = it.copy(password = credentials.password, details = modifiedDetails)
            secureStorage.updateWebsiteLoginDetailsWithCredentials(modified)
        }
    }

    override suspend fun getAllCredentials(): Flow<List<LoginCredentials>> {
        return secureStorage.websiteLoginDetailsWithCredentials()
            .map { list ->
                list.map { it.toLoginCredentials() }
            }
    }

    override suspend fun deleteCredentials(id: Long) {
        secureStorage.deleteWebsiteLoginDetailsWithCredentials(id)
    }

    override suspend fun updateCredentials(credentials: LoginCredentials) {
        secureStorage.updateWebsiteLoginDetailsWithCredentials(
            credentials.copy(lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis())
                .toWebsiteLoginCredentials()
        )
    }

    override suspend fun containsCredentials(
        rawUrl: String,
        username: String?,
        password: String?
    ): ContainsCredentialsResult {
        val url = rawUrl.extractSchemeAndDomain() ?: return NoMatch
        val credentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull() ?: return NoMatch

        var exactMatchFound = false
        var usernameMatchFound = false
        var urlMatch = false

        credentials.forEach {
            urlMatch = true

            if (it.details.username == username) {
                usernameMatchFound = true
                if (it.password == password) {
                    exactMatchFound = true
                }
            }
        }

        return if (exactMatchFound) {
            ContainsCredentialsResult.ExactMatch
        } else if (usernameMatchFound) {
            ContainsCredentialsResult.UsernameMatch
        } else if (urlMatch) {
            ContainsCredentialsResult.UrlOnlyMatch
        } else {
            NoMatch
        }
    }

    private fun WebsiteLoginDetailsWithCredentials.toLoginCredentials(): LoginCredentials {
        return LoginCredentials(
            id = details.id,
            domain = details.domain,
            username = details.username,
            password = password,
            domainTitle = details.domainTitle,
            notes = details.notes,
            lastUpdatedMillis = details.lastUpdatedMillis
        )
    }

    private fun LoginCredentials.toWebsiteLoginCredentials(): WebsiteLoginDetailsWithCredentials {
        return WebsiteLoginDetailsWithCredentials(
            details = WebsiteLoginDetails(
                domain = domain,
                username = username,
                id = id,
                domainTitle = domainTitle,
                notes = notes,
                lastUpdatedMillis = lastUpdatedMillis
            ),
            password = password
        )
    }
}
