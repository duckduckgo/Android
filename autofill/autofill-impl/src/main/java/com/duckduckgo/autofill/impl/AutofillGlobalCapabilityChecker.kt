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

import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.api.feature.AutofillFeatureName.Autofill
import com.duckduckgo.autofill.api.store.AutofillStore
import com.duckduckgo.deviceauth.api.DeviceAuthenticator
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.feature.toggles.api.FeatureToggle
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject

interface AutofillGlobalCapabilityChecker {
    suspend fun isSecureAutofillAvailable(): Boolean
    suspend fun isAutofillEnabledByConfiguration(): Boolean
    suspend fun isAutofillEnabledByUser(): Boolean
}

@ContributesBinding(AppScope::class)
class AutofillGlobalCapabilityCheckerImpl @Inject constructor(
    private val featureToggle: FeatureToggle,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val autofillStore: AutofillStore,
    private val deviceAuthenticator: DeviceAuthenticator,
) : AutofillGlobalCapabilityChecker {

    override suspend fun isSecureAutofillAvailable(): Boolean {
        if (!autofillStore.autofillAvailable) return false
        if (!deviceAuthenticator.hasValidDeviceAuthentication()) return false
        return true
    }

    override suspend fun isAutofillEnabledByConfiguration(): Boolean {
        return isInternalTester() || isGlobalFeatureEnabled()
    }

    override suspend fun isAutofillEnabledByUser(): Boolean {
        return autofillStore.autofillEnabled
    }

    private fun isInternalTester() = internalTestUserChecker.isInternalTestUser
    private fun isGlobalFeatureEnabled() = featureToggle.isFeatureEnabled(Autofill.value, defaultValue = false)
}
