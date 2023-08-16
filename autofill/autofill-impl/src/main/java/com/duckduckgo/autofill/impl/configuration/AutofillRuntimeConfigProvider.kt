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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.api.email.EmailManager
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.email.incontext.availability.EmailProtectionInContextAvailabilityRules
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.autofill.impl.sharedcreds.ShareableCredentials
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface AutofillRuntimeConfigProvider {
    suspend fun getRuntimeConfiguration(
        rawJs: String,
        url: String?,
    ): String
}

@ContributesBinding(AppScope::class)
class RealAutofillRuntimeConfigProvider @Inject constructor(
    private val emailManager: EmailManager,
    private val autofillStore: AutofillStore,
    private val runtimeConfigurationWriter: RuntimeConfigurationWriter,
    private val autofillCapabilityChecker: AutofillCapabilityChecker,
    private val shareableCredentials: ShareableCredentials,
    private val emailProtectionInContextAvailabilityRules: EmailProtectionInContextAvailabilityRules,
) : AutofillRuntimeConfigProvider {
    override suspend fun getRuntimeConfiguration(
        rawJs: String,
        url: String?,
    ): String {
        Timber.v("BrowserAutofill: getRuntimeConfiguration called")

        val contentScope = runtimeConfigurationWriter.generateContentScope()
        val userUnprotectedDomains = runtimeConfigurationWriter.generateUserUnprotectedDomains()
        val userPreferences = runtimeConfigurationWriter.generateUserPreferences(
            autofillCredentials = canInjectCredentials(url),
            credentialSaving = canSaveCredentials(url),
            passwordGeneration = canGeneratePasswords(url),
            showInlineKeyIcon = true,
            showInContextEmailProtectionSignup = canShowInContextEmailProtectionSignup(url),
        )
        val availableInputTypes = generateAvailableInputTypes(url)

        return rawJs
            .replace("// INJECT contentScope HERE", contentScope)
            .replace("// INJECT userUnprotectedDomains HERE", userUnprotectedDomains)
            .replace("// INJECT userPreferences HERE", userPreferences)
            .replace("// INJECT availableInputTypes HERE", availableInputTypes)
    }

    private suspend fun generateAvailableInputTypes(url: String?): String {
        val credentialsAvailable = determineIfCredentialsAvailable(url)
        val emailAvailable = determineIfEmailAvailable()

        val json = runtimeConfigurationWriter.generateResponseGetAvailableInputTypes(credentialsAvailable, emailAvailable).also {
            Timber.v("availableInputTypes for %s: \n%s", url, it)
        }
        return "availableInputTypes = $json"
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

    private suspend fun canInjectCredentials(url: String?): Boolean {
        if (url == null) return false
        return autofillCapabilityChecker.canInjectCredentialsToWebView(url)
    }

    private suspend fun canSaveCredentials(url: String?): Boolean {
        if (url == null) return false
        return autofillCapabilityChecker.canSaveCredentialsFromWebView(url)
    }

    private suspend fun canGeneratePasswords(url: String?): Boolean {
        if (url == null) return false
        return autofillCapabilityChecker.canGeneratePasswordFromWebView(url)
    }

    private suspend fun canShowInContextEmailProtectionSignup(url: String?): Boolean {
        if (url == null) return false
        return emailProtectionInContextAvailabilityRules.permittedToShow(url)
    }

    private fun determineIfEmailAvailable(): Boolean = emailManager.isSignedIn()
}
