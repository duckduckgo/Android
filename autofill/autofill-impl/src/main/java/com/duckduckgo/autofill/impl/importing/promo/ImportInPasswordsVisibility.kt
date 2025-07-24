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

package com.duckduckgo.autofill.impl.importing.promo

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.capability.ImportGooglePasswordsCapabilityChecker
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import logcat.logcat

interface ImportInPasswordsVisibility {
    fun canShowImportInPasswords(numberSavedPasswords: Int): Boolean
    fun onPromoDismissed()
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealImportInPasswordsVisibility @Inject constructor(
    private val internalAutofillStore: InternalAutofillStore,
    private val autofillFeature: AutofillFeature,
    private val importGooglePasswordsCapabilityChecker: ImportGooglePasswordsCapabilityChecker,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val dispatcherProvider: DispatcherProvider,
) : ImportInPasswordsVisibility {

    private var canShowImportPasswords = false
    private var importedPasswordsCollector: Job? = null

    init {
        importedPasswordsCollector = appCoroutineScope.launch(dispatcherProvider.io()) {
            logcat { "Autofill: Evaluating if user can show import promo" }
            canShowImportPasswords = evaluateIfUserCanShowImportPromo()
            logcat { "Autofill: Evaluation result, can show import promo? $canShowImportPasswords" }

            if (!canShowImportPasswords) return@launch

            // Observe changes of hasEverImportedPasswordsFlow only if the promo can be shown
            internalAutofillStore.hasEverImportedPasswordsFlow().collect { hasImported ->
                logcat { "Autofill: hasEverImportedPasswords changed to $hasImported" }
                if (hasImported) {
                    canShowImportPasswords = false
                    logcat { "Autofill: User has imported passwords, hiding promo" }
                    importedPasswordsCollector?.cancel()
                }
            }
        }
    }

    override fun canShowImportInPasswords(numberSavedPasswords: Int): Boolean {
        if (numberSavedPasswords < MIN_PASSWORDS_TO_SHOW_PROMO || numberSavedPasswords > MAX_PASSWORDS_TO_SHOW_PROMO) return false
        return canShowImportPasswords
    }

    override fun onPromoDismissed() {
        internalAutofillStore.hasDeclinedPasswordManagementImportPromo = true
        canShowImportPasswords = false
    }

    private suspend fun evaluateIfUserCanShowImportPromo(): Boolean {
        if (autofillFeature.canPromoteImportPasswordsInPasswordManagement().isEnabled().not()) return false

        if (internalAutofillStore.hasEverImportedPasswords || internalAutofillStore.hasDeclinedPasswordManagementImportPromo) return false

        val gpmImport = autofillFeature.self().isEnabled() && autofillFeature.canImportFromGooglePasswordManager().isEnabled()
        val webViewSupportsImportingPasswords = importGooglePasswordsCapabilityChecker.webViewCapableOfImporting()
        canShowImportPasswords = gpmImport && webViewSupportsImportingPasswords

        return canShowImportPasswords
    }

    companion object {
        private const val MAX_PASSWORDS_TO_SHOW_PROMO = 25
        private const val MIN_PASSWORDS_TO_SHOW_PROMO = 1
    }
}
