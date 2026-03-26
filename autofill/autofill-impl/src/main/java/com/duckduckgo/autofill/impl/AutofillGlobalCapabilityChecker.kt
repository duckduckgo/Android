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

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.InternalTestUserChecker
import com.duckduckgo.autofill.impl.deviceauth.DeviceAuthenticator
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AutofillGlobalCapabilityChecker {
    suspend fun isSecureAutofillAvailable(): Boolean
    suspend fun isAutofillEnabledByConfiguration(url: String): Boolean
    suspend fun isAutofillEnabledByUser(): Boolean
}

@ContributesBinding(AppScope::class)
class AutofillGlobalCapabilityCheckerImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val internalTestUserChecker: InternalTestUserChecker,
    private val autofillStore: InternalAutofillStore,
    private val deviceAuthenticator: DeviceAuthenticator,
    private val autofill: com.duckduckgo.autofill.api.Autofill,
    private val dispatcherProvider: DispatcherProvider,
) : AutofillGlobalCapabilityChecker {

    override suspend fun isSecureAutofillAvailable(): Boolean {
        return withContext(dispatcherProvider.io()) {
            if (!autofillStore.autofillAvailable()) return@withContext false
            if (deviceAuthenticator.isAuthenticationRequiredForAutofill() && !deviceAuthenticator.hasValidDeviceAuthentication()) {
                return@withContext false
            }
            return@withContext true
        }
    }

    override suspend fun isAutofillEnabledByConfiguration(url: String): Boolean {
        return withContext(dispatcherProvider.io()) {
            val enabledAtTopLevel = isInternalTester() || isGlobalFeatureEnabled()
            val canIntegrateAutofill = autofillFeature.canIntegrateAutofillInWebView().isEnabled()
            enabledAtTopLevel && canIntegrateAutofill && !isAnException(url)
        }
    }

    override suspend fun isAutofillEnabledByUser(): Boolean {
        return withContext(dispatcherProvider.io()) {
            autofillStore.autofillEnabled
        }
    }

    private fun isAnException(url: String): Boolean = autofill.isAnException(url)
    private fun isInternalTester() = internalTestUserChecker.isInternalTestUser
    private fun isGlobalFeatureEnabled() = autofillFeature.self().isEnabled()
}
