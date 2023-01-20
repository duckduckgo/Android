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
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.api.feature.AutofillFeatureName.Autofill
import com.duckduckgo.autofill.api.feature.AutofillFeatureToggle
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.AccessCredentialManagement
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.InjectCredentials
import com.duckduckgo.autofill.api.feature.AutofillSubfeatureName.SaveCredentials
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class AutofillCapabilityCheckerImpl @Inject constructor(
    private val featureToggle: FeatureToggle,
    private val autofillFeatureToggle: AutofillFeatureToggle,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val autofillGlobalCapabilityChecker: AutofillGlobalCapabilityChecker,
) : AutofillCapabilityChecker {

    override suspend fun isAutofillEnabledByConfiguration(): Boolean = autofillGlobalCapabilityChecker.isAutofillEnabledByConfiguration()
    override suspend fun isSecureAutofillAvailable(): Boolean = autofillGlobalCapabilityChecker.isSecureAutofillAvailable()
    override suspend fun isAutofillEnabledByUser(): Boolean = autofillGlobalCapabilityChecker.isAutofillEnabledByUser()

    override suspend fun canInjectCredentialsToWebView(): Boolean {
        if (!isSecureAutofillAvailable()) return false
        if (!isAutofillEnabledByConfiguration()) return false
        if (!isAutofillEnabledByUser()) return false

        if (isInternalTester()) return true

        return autofillFeatureToggle.isFeatureEnabled(InjectCredentials, defaultValue = false)
    }

    override suspend fun canSaveCredentialsFromWebView(): Boolean {
        if (!isSecureAutofillAvailable()) return false
        if (!isAutofillEnabledByConfiguration()) return false
        if (!isAutofillEnabledByUser()) return false

        if (isInternalTester()) return true

        return autofillFeatureToggle.isFeatureEnabled(SaveCredentials, defaultValue = false)
    }

    /**
     * Because the credential management screen handles the states where the user has toggled autofill off, or the device can't support it,
     * this feature is not dependent those checks.
     *
     * We purposely don't couple this check against [isSecureAutofillAvailable] or [isAutofillEnabledByUser].
     */
    override suspend fun canAccessCredentialManagementScreen(): Boolean {
        if (isInternalTester()) return true
        if (!isGlobalFeatureEnabled()) return false

        return autofillFeatureToggle.isFeatureEnabled(AccessCredentialManagement, defaultValue = false)
    }

    private fun isInternalTester() = internalTestUserChecker.isInternalTestUser
    private fun isGlobalFeatureEnabled() = featureToggle.isFeatureEnabled(Autofill.value, defaultValue = false)
}
