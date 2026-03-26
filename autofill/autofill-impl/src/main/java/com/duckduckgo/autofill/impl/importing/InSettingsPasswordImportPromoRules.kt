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

package com.duckduckgo.autofill.impl.importing

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface InSettingsPasswordImportPromoRules {
    suspend fun canShowPromo(): Boolean
}

@ContributesBinding(AppScope::class)
class RealInSettingsPasswordImportPromoRules @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
    private val importPasswordCapabilityChecker: ImportGooglePasswordsCapabilityChecker,
) : InSettingsPasswordImportPromoRules {

    override suspend fun canShowPromo(): Boolean {
        return withContext(dispatchers.io()) {
            if (featureEnabled().not()) {
                return@withContext false
            }

            if (autofillStore.hasEverImportedPasswords) {
                return@withContext false
            }

            if (autofillStore.hasDismissedMainAppSettingsPromo) {
                return@withContext false
            }

            if ((autofillStore.getCredentialCount().firstOrNull() ?: 0) >= MAX_CREDENTIALS_FOR_PROMO) {
                return@withContext false
            }

            if (importPasswordCapabilityChecker.webViewCapableOfImporting().not()) {
                return@withContext false
            }

            return@withContext true
        }
    }

    private fun featureEnabled(): Boolean {
        if (autofillFeature.self().isEnabled().not()) return false
        if (autofillFeature.canShowImportOptionInAppSettings().isEnabled().not()) return false
        return true
    }

    companion object {
        const val MAX_CREDENTIALS_FOR_PROMO = 25
    }
}
