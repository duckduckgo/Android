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

package com.duckduckgo.autofill.impl

import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Password
import com.duckduckgo.autofill.api.CredentialUpdateExistingCredentialsDialog.CredentialUpdateType.Username
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult.NoMatch
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetails
import com.duckduckgo.autofill.impl.securestorage.WebsiteLoginDetailsWithCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.store.AutofillPrefsStore
import com.duckduckgo.autofill.store.LastUpdatedTimeProvider
import com.duckduckgo.autofill.sync.SyncCredentialsListener
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class SecureStoreBackedAutofillStore @Inject constructor(
    private val secureStorage: SecureStorage,
    private val lastUpdatedTimeProvider: LastUpdatedTimeProvider,
    private val autofillPrefsStore: AutofillPrefsStore,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
    private val autofillUrlMatcher: AutofillUrlMatcher,
    private val syncCredentialsListener: SyncCredentialsListener,
) : InternalAutofillStore {

    override val autofillAvailable: Boolean
        get() = secureStorage.canAccessSecureStorage()

    override var autofillEnabled: Boolean
        get() = autofillPrefsStore.isEnabled
        set(value) {
            autofillPrefsStore.isEnabled = value
        }

    override var hasEverBeenPromptedToSaveLogin: Boolean
        get() = autofillPrefsStore.hasEverBeenPromptedToSaveLogin
        set(value) {
            autofillPrefsStore.hasEverBeenPromptedToSaveLogin = value
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
                Timber.i("Querying secure store for stored credentials. rawUrl: %s", rawUrl)

                val visitedSite = autofillUrlMatcher.extractUrlPartsForAutofill(rawUrl)
                if (visitedSite.eTldPlus1 == null) return@withContext emptyList()

                // first part of domain matching happens at the DB level
                val storedCredentials =
                    secureStorage.websiteLoginDetailsWithCredentialsForDomain(visitedSite.eTldPlus1!!).firstOrNull() ?: emptyList()

                // second part of domain matching requires filtering at code level
                storedCredentials.filter {
                    val storedDomain = it.details.domain ?: return@filter false
                    val savedSite = autofillUrlMatcher.extractUrlPartsForAutofill(storedDomain)
                    return@filter autofillUrlMatcher.matchingForAutofill(visitedSite, savedSite)
                }.map { it.toLoginCredentials() }
            } else {
                emptyList()
            }
        }
    }

    override suspend fun getCredentialsWithId(id: Long): LoginCredentials? =
        secureStorage.getWebsiteLoginDetailsWithCredentials(id)?.toLoginCredentials()

    override suspend fun saveCredentials(
        rawUrl: String,
        credentials: LoginCredentials,
    ): LoginCredentials? {
        val url = autofillUrlMatcher.cleanRawUrl(rawUrl)

        Timber.i("Saving login credentials for %s. username=%s", url, credentials.username)

        val loginDetails = WebsiteLoginDetails(
            domain = url,
            username = credentials.username,
            domainTitle = credentials.domainTitle,
            lastUpdatedMillis = lastUpdatedTimeProvider.getInMillis(),
        )
        val webSiteLoginCredentials = WebsiteLoginDetailsWithCredentials(
            details = loginDetails,
            password = credentials.password,
            notes = credentials.notes,
        )

        return withContext(dispatcherProvider.io()) {
            secureStorage.addWebsiteLoginDetailsWithCredentials(webSiteLoginCredentials)?.toLoginCredentials().also {
                syncCredentialsListener.onCredentialAdded(it?.id!!)
            }
        }
    }

    override suspend fun updateCredentials(
        rawUrl: String,
        credentials: LoginCredentials,
        updateType: CredentialUpdateType,
    ): LoginCredentials? {
        val url = autofillUrlMatcher.cleanRawUrl(rawUrl)

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
            updatedCredentials = secureStorage.updateWebsiteLoginDetailsWithCredentials(modified)?.also {
                syncCredentialsListener.onCredentialUpdated(it.details.id!!)
            }
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

    override suspend fun getCredentialCount(): Flow<Int> {
        return secureStorage.websiteLoginDetailsWithCredentials().map { it.size }
    }

    override suspend fun deleteCredentials(id: Long): LoginCredentials? {
        val existingCredential = secureStorage.getWebsiteLoginDetailsWithCredentials(id)
        secureStorage.deleteWebsiteLoginDetailsWithCredentials(id)
        syncCredentialsListener.onCredentialRemoved(id)
        return existingCredential?.toLoginCredentials()
    }

    override suspend fun deleteAllCredentials(): List<LoginCredentials> {
        val savedCredentials = secureStorage.websiteLoginDetailsWithCredentials().firstOrNull() ?: emptyList()
        val idsToDelete = savedCredentials.mapNotNull { it.details.id }
        secureStorage.deleteWebSiteLoginDetailsWithCredentials(idsToDelete)
        Timber.i("Deleted %d credentials", idsToDelete.size)
        syncCredentialsListener.onCredentialRemoved(idsToDelete)
        return savedCredentials.map { it.toLoginCredentials() }
    }

    override suspend fun updateCredentials(credentials: LoginCredentials, refreshLastUpdatedTimestamp: Boolean): LoginCredentials? {
        val cleanedDomain: String? = credentials.domain?.let {
            autofillUrlMatcher.cleanRawUrl(it)
        }

        val lastUpdated = if (refreshLastUpdatedTimestamp) lastUpdatedTimeProvider.getInMillis() else credentials.lastUpdatedMillis

        return secureStorage.updateWebsiteLoginDetailsWithCredentials(
            credentials.copy(lastUpdatedMillis = lastUpdated, domain = cleanedDomain).toWebsiteLoginCredentials(),
        )?.toLoginCredentials()?.also {
            syncCredentialsListener.onCredentialUpdated(it.id!!)
        }
    }

    override suspend fun containsCredentials(
        rawUrl: String,
        username: String?,
        password: String?,
    ): ContainsCredentialsResult {
        val url = autofillUrlMatcher.cleanRawUrl(rawUrl)
        val credentials = secureStorage.websiteLoginDetailsWithCredentialsForDomain(url).firstOrNull() ?: return NoMatch

        var exactMatchFound = false
        var usernameMatchFound = false
        var urlMatch = false
        var missingUsername = false

        credentials.forEach {
            urlMatch = true

            if (usernameMissing(it, username, password)) {
                missingUsername = true
            } else if (usernameMatch(it, username)) {
                usernameMatchFound = true
                if (it.password == password) {
                    exactMatchFound = true
                }
            }
        }

        val matchType = if (exactMatchFound) {
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

        Timber.v("Determined match type is %s", matchType.javaClass.simpleName)
        return matchType
    }

    private fun LoginCredentials.prepareForReinsertion(): WebsiteLoginDetailsWithCredentials {
        val loginDetails = WebsiteLoginDetails(
            id = id,
            domain = domain,
            username = username,
            domainTitle = domainTitle,
            lastUpdatedMillis = lastUpdatedMillis,
        )
        return WebsiteLoginDetailsWithCredentials(
            details = loginDetails,
            password = password,
            notes = notes,
        )
    }

    override suspend fun reinsertCredentials(credentials: LoginCredentials) {
        withContext(dispatcherProvider.io()) {
            secureStorage.addWebsiteLoginDetailsWithCredentials(credentials.prepareForReinsertion())?.also {
                syncCredentialsListener.onCredentialAdded(it.details.id!!)
            }
        }
    }

    override suspend fun reinsertCredentials(credentials: List<LoginCredentials>) {
        withContext(dispatcherProvider.io()) {
            val mappedCredentials = credentials.map { it.prepareForReinsertion() }
            secureStorage.addWebsiteLoginDetailsWithCredentials(mappedCredentials).also {
                val ids = mappedCredentials.mapNotNull { it.details.id }
                syncCredentialsListener.onCredentialsAdded(ids)
            }
        }
    }

    private fun usernameMatch(
        credentials: WebsiteLoginDetailsWithCredentials,
        username: String?,
    ): Boolean {
        return credentials.details.username != null && credentials.details.username == username
    }

    private fun usernameMissing(
        credentials: WebsiteLoginDetailsWithCredentials,
        username: String?,
        password: String?,
    ): Boolean {
        return credentials.details.username == null &&
            !username.isNullOrEmpty() &&
            credentials.password != null &&
            credentials.password == password
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
            lastUsedMillis = details.lastUsedInMillis,
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
                lastUsedInMillis = lastUsedMillis,
            ),
            password = password,
            notes = notes,
        )
    }
}
