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

import com.duckduckgo.app.global.DefaultDispatcherProvider
import com.duckduckgo.app.global.DispatcherProvider
import com.duckduckgo.app.global.extractSchemeAndDomain
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Password
import com.duckduckgo.autofill.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Username
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
import kotlinx.coroutines.withContext
import timber.log.Timber

class SecureStoreBackedAutofillStore(
    private val secureStorage: SecureStorage,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val lastUpdatedTimeProvider: LastUpdatedTimeProvider,
    private val autofillPrefsStore: AutofillPrefsStore,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider()
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

    override var autofillDeclineCount: Int
        get() = autofillPrefsStore.autofillDeclineCount
        set(value) {
            autofillPrefsStore.autofillDeclineCount = value
        }

    override var monitorDeclineCounts: Boolean
        get() = autofillPrefsStore.monitorDeclineCounts
        set(value) {
            autofillPrefsStore.monitorDeclineCounts = value
        }

    override suspend fun getCredentials(rawUrl: String): List<LoginCredentials> {
        return withContext(dispatcherProvider.io()) {
            return@withContext if (autofillEnabled && autofillAvailable) {
                Timber.i("Querying secure store for stored credentials. rawUrl: %s, extractedDomain:%s", rawUrl, rawUrl.extractSchemeAndDomain())
                val url = rawUrl.extractSchemeAndDomain() ?: return@withContext emptyList()

                val storedCredentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull() ?: emptyList()
                Timber.v("Found %d credentials for %s", storedCredentials.size, url)

                storedCredentials.map { it.toLoginCredentials() }
            } else {
                emptyList()
            }
        }
    }

    override suspend fun getCredentialsWithId(id: Long): LoginCredentials? =
        secureStorage.getWebsiteLoginDetailsWithCredentials(id)?.toLoginCredentials()

    override suspend fun saveCredentials(
        rawUrl: String,
        credentials: LoginCredentials
    ): LoginCredentials? {
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
            lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis()
        )
        val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(loginDetails, password = credentials.password)

        return withContext(dispatcherProvider.io()) {
            secureStorage.addWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)?.toLoginCredentials().also {
                showOnboardingWhenOfferingToSaveLogin = false
            }
        }
    }

    override suspend fun updateCredentials(
        rawUrl: String,
        credentials: LoginCredentials,
        updateType: CredentialUpdateType
    ): LoginCredentials? {
        val url = rawUrl.extractSchemeAndDomain()
        if (url == null) {
            Timber.w("Cannot update credentials as given url was in an unexpected format. Original url: %s", rawUrl)
            return null
        }

        val filter = when (updateType) {
            Username -> filterMatchingPassword(credentials)
            Password -> filterMatchingUsername(credentials)
            else -> return null
        }

        val matchingCredentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull()?.filter(filter)
        if (matchingCredentials.isNullOrEmpty()) {
            Timber.w("Cannot update credentials as no credentials were found for %s", url)
            return null
        }

        Timber.i("Updating %d saved login credentials for %s. username=%s", matchingCredentials.size, url, credentials.username)

        var updatedCredentials: WebsiteLoginDetailsWithCredentials? = null

        matchingCredentials.forEach {
            val modifiedDetails = it.details.copy(username = credentials.username, lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis())
            val modified = it.copy(password = credentials.password, details = modifiedDetails)
            updatedCredentials = secureStorage.updateWebsiteLoginDetailsWithCredentials(modified)
        }

        return updatedCredentials?.toLoginCredentials()
    }

    private fun filterMatchingUsername(credentials: LoginCredentials): (WebsiteLoginDetailsWithCredentials) -> Boolean =
        { it.details.username == credentials.username }

    private fun filterMatchingPassword(credentials: LoginCredentials): (WebsiteLoginDetailsWithCredentials) -> Boolean = {
        it.password == credentials.password && it.details.username.isNullOrEmpty()
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

    override suspend fun updateCredentials(credentials: LoginCredentials): LoginCredentials? {
        return secureStorage.updateWebsiteLoginDetailsWithCredentials(
            credentials.copy(lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis())
                .toWebsiteLoginCredentials()
        )?.toLoginCredentials()
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
        var missingUsername = false

        credentials.forEach {
            urlMatch = true

            if (it.details.username == null && it.password != null && it.password == password) {
                missingUsername = true
            } else if (it.details.username == username) {
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
        } else if (missingUsername) {
            ContainsCredentialsResult.UsernameMissing
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
            notes = notes,
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
                lastUpdatedMillis = lastUpdatedMillis
            ),
            password = password,
            notes = notes
        )
    }
}
