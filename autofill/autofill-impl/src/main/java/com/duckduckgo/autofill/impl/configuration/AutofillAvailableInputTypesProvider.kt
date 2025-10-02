/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.impl.configuration.AutofillAvailableInputTypesProvider.AvailableInputTypes
import com.duckduckgo.autofill.impl.importing.InBrowserImportPromo
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.store.ReAuthenticationDetails
import com.duckduckgo.autofill.impl.store.emptyReAuthenticationDetails
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AutofillAvailableInputTypesProvider {
    suspend fun getTypes(
        url: String?,
        reAuthenticationDetails: ReAuthenticationDetails = emptyReAuthenticationDetails(),
    ): AvailableInputTypes

    data class AvailableInputTypes(
        val username: Boolean = false,
        val password: Boolean = false,
        val email: Boolean = false,
        val credentialsImport: Boolean = false,
    )
}

@ContributesBinding(AppScope::class)
class RealAutofillAvailableInputTypesProvider @Inject constructor(
    private val emailManager: EmailManager,
    private val autofillStore: InternalAutofillStore,
    private val shareableCredentials: ShareableCredentials,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val inBrowserPromo: InBrowserImportPromo,
    private val dispatchers: DispatcherProvider,
) : AutofillAvailableInputTypesProvider {

    override suspend fun getTypes(
        url: String?,
        reAuthenticationDetails: ReAuthenticationDetails,
    ): AvailableInputTypes {
        return withContext(dispatchers.io()) {
            val availableInputTypeCredentials = determineIfCredentialsAvailable(url)
            val reauthCredentials = determineIfReauthenticationDetailsAvailable(reAuthenticationDetails)

            val finalCredentials = AvailableInputTypeCredentials(
                username = availableInputTypeCredentials.username || reauthCredentials.username,
                password = availableInputTypeCredentials.password || reauthCredentials.password,
            )

            val credentialsAvailableOnThisPage = finalCredentials.username || finalCredentials.password
            val emailAvailable = determineIfEmailAvailable()
            val importPromoAvailable = inBrowserPromo.canShowPromo(credentialsAvailableOnThisPage, url)

            AvailableInputTypes(
                username = finalCredentials.username,
                password = finalCredentials.password,
                email = emailAvailable,
                credentialsImport = importPromoAvailable,
            )
        }
    }

    private suspend fun determineIfCredentialsAvailable(url: String?): AvailableInputTypeCredentials {
        return if (url == null || !autofillCapabilityChecker.canInjectCredentialsToWebView(url)) {
            AvailableInputTypeCredentials(username = false, password = false)
        } else {
            val matches = mutableListOf<LoginCredentials>()
            val directMatches = autofillStore.getCredentials(url)
            val shareableMatches = shareableCredentials.shareableCredentials(url)
            matches.addAll(directMatches)
            matches.addAll(shareableMatches)

            val usernameSearch = matches.find { !it.username.isNullOrEmpty() }
            val passwordSearch = matches.find { !it.password.isNullOrEmpty() }

            AvailableInputTypeCredentials(username = usernameSearch != null, password = passwordSearch != null)
        }
    }

    private fun determineIfReauthenticationDetailsAvailable(reAuthenticationDetails: ReAuthenticationDetails): AvailableInputTypeCredentials {
        val reauthPasswordAvailable = !reAuthenticationDetails.password.isNullOrEmpty()

        return AvailableInputTypeCredentials(
            username = false, // Re-authentication only provides passwords
            password = reauthPasswordAvailable,
        )
    }

    private fun determineIfEmailAvailable(): Boolean = emailManager.isSignedIn()
}
