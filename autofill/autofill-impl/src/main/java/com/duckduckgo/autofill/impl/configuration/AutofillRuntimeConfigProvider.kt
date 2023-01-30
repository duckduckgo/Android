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

import com.duckduckgo.app.email.EmailManager
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
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
    private val deviceAuthenticator: DeviceAuthenticator,
    private val autofillStore: AutofillStore,
    private val runtimeConfigurationWriter: RuntimeConfigurationWriter,
) : AutofillRuntimeConfigProvider {
    override suspend fun getRuntimeConfiguration(
        rawJs: String,
        url: String?,
    ): String {
        Timber.v("BrowserAutofill: getRuntimeConfiguration called")

        val contentScope = runtimeConfigurationWriter.generateContentScope()
        val userUnprotectedDomains = runtimeConfigurationWriter.generateUserUnprotectedDomains()
        val userPreferences = runtimeConfigurationWriter.generateUserPreferences(
            autofillCredentials = determineIfAutofillEnabled(),
            showInlineKeyIcon = true,
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

    // in the future, we'll also tie this into feature toggles and remote config
    private fun determineIfAutofillEnabled(): Boolean =
        autofillStore.autofillAvailable && autofillStore.autofillEnabled && deviceAuthenticator.hasValidDeviceAuthentication()

    private suspend fun determineIfCredentialsAvailable(url: String?): AvailableInputTypeCredentials {
        return if (url == null || !determineIfAutofillEnabled()) {
            AvailableInputTypeCredentials(username = false, password = false)
        } else {
            val savedCredentials = autofillStore.getCredentials(url)

            val usernameSearch = savedCredentials.find { !it.username.isNullOrEmpty() }
            val passwordSearch = savedCredentials.find { !it.password.isNullOrEmpty() }

            AvailableInputTypeCredentials(username = usernameSearch != null, password = passwordSearch != null)
        }
    }

    private fun determineIfEmailAvailable(): Boolean = emailManager.isSignedIn()
}
