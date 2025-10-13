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

import com.duckduckgo.autofill.api.AutofillCapabilityChecker
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class AutofillCapabilityCheckerImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val autofillGlobalCapabilityChecker: AutofillGlobalCapabilityChecker,
    private val dispatcherProvider: DispatcherProvider,
) : AutofillCapabilityChecker {

    override suspend fun canInjectCredentialsToWebView(url: String): Boolean = withContext(dispatcherProvider.io()) {
        if (!isSecureAutofillAvailable()) return@withContext false
        if (!isAutofillEnabledByConfiguration(url)) return@withContext false
        if (!isAutofillEnabledByUser()) return@withContext false

        if (isInternalTester()) return@withContext true

        return@withContext autofillFeature.canInjectCredentials().isEnabled()
    }

    override suspend fun canSaveCredentialsFromWebView(url: String): Boolean = withContext(dispatcherProvider.io()) {
        if (!isSecureAutofillAvailable()) return@withContext false
        if (!isAutofillEnabledByConfiguration(url)) return@withContext false
        if (!isAutofillEnabledByUser()) return@withContext false

        if (isInternalTester()) return@withContext true

        return@withContext autofillFeature.canSaveCredentials().isEnabled()
    }

    override suspend fun canGeneratePasswordFromWebView(url: String): Boolean = withContext(dispatcherProvider.io()) {
        if (!isSecureAutofillAvailable()) return@withContext false
        if (!isAutofillEnabledByConfiguration(url)) return@withContext false
        if (!isAutofillEnabledByUser()) return@withContext false

        if (isInternalTester()) return@withContext true

        return@withContext autofillFeature.canGeneratePasswords().isEnabled()
    }

    /**
     * Because the credential management screen handles the states where the user has toggled autofill off, or the device can't support it,
     * this feature is not dependent those checks.
     *
     * We purposely don't couple this check against [isSecureAutofillAvailable] or [isAutofillEnabledByUser].
     */
    override suspend fun canAccessCredentialManagementScreen(): Boolean = withContext(dispatcherProvider.io()) {
        if (isInternalTester()) return@withContext true
        if (!isGlobalFeatureEnabled()) return@withContext false
        return@withContext autofillFeature.canAccessCredentialManagement().isEnabled()
    }

    private suspend fun isInternalTester(): Boolean {
        return withContext(dispatcherProvider.io()) {
            internalTestUserChecker.isInternalTestUser
        }
    }

    private suspend fun isGlobalFeatureEnabled(): Boolean {
        return withContext(dispatcherProvider.io()) {
            autofillFeature.self().isEnabled()
        }
    }

    override suspend fun isAutofillEnabledByConfiguration(url: String) = autofillGlobalCapabilityChecker.isAutofillEnabledByConfiguration(url)

    private suspend fun isSecureAutofillAvailable() = autofillGlobalCapabilityChecker.isSecureAutofillAvailable()

    private suspend fun isAutofillEnabledByUser() = autofillGlobalCapabilityChecker.isAutofillEnabledByUser()
}
