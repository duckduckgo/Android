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
import com.duckduckgo.autofill.impl.store.NeverSavedSiteRepository
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface InBrowserImportPromo {
    suspend fun canShowPromo(
        credentialsAvailableForCurrentPage: Boolean,
        url: String?,
    ): Boolean
}

@ContributesBinding(AppScope::class)
class RealInBrowserImportPromo @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val dispatchers: DispatcherProvider,
    private val neverSavedSiteRepository: NeverSavedSiteRepository,
    private val autofillFeature: AutofillFeature,
    private val importPasswordCapabilityChecker: ImportGooglePasswordsCapabilityChecker,
    private val inBrowserPromoPreviousPromptsStore: InBrowserPromoPreviousPromptsStore,
) : InBrowserImportPromo {

    override suspend fun canShowPromo(
        credentialsAvailableForCurrentPage: Boolean,
        url: String?,
    ): Boolean {
        return withContext(dispatchers.io()) {
            if (credentialsAvailableForCurrentPage) {
                return@withContext false
            }

            if (url == null || url.isExcludedFromPromo()) {
                return@withContext false
            }

            if (inBrowserPromoPreviousPromptsStore.hasPromoBeenDisplayed(url)) {
                return@withContext false
            }

            if (featureEnabled().not()) {
                return@withContext false
            }

            if (autofillStore.hasEverImportedPasswords) {
                return@withContext false
            }

            if (autofillStore.hasDeclinedInBrowserPasswordImportPromo) {
                return@withContext false
            }

            if ((autofillStore.getCredentialCount().firstOrNull() ?: 0) >= MAX_CREDENTIALS_FOR_PROMO) {
                return@withContext false
            }

            if (autofillStore.inBrowserImportPromoShownCount >= MAX_PROMO_SHOWN_COUNT) {
                return@withContext false
            }

            if (neverSavedSiteRepository.isInNeverSaveList(url)) {
                return@withContext false
            }

            if (importPasswordCapabilityChecker.webViewCapableOfImporting().not()) {
                return@withContext false
            }

            return@withContext true
        }
    }

    private fun String.isExcludedFromPromo(): Boolean {
        return this.startsWith(EMAIL_PROTECTION_SETTINGS_URL_PREFIX)
    }

    private fun featureEnabled(): Boolean {
        if (autofillFeature.self().isEnabled().not()) return false
        if (autofillFeature.canPromoteImportGooglePasswordsInBrowser().isEnabled().not()) return false
        return true
    }

    companion object {
        const val MAX_PROMO_SHOWN_COUNT = 5
        const val MAX_CREDENTIALS_FOR_PROMO = 25
        private const val EMAIL_PROTECTION_SETTINGS_URL_PREFIX = "https://duckduckgo.com/email/"
    }
}
