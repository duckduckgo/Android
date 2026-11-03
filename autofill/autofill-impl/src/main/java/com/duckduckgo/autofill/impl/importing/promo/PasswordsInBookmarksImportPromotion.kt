/*
 * Copyright (c) 2026 DuckDuckGo
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

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.importing.capability.PasswordImportAvailability
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PasswordsInBookmarksImportPromotion {
    suspend fun canShowPromotion(importedBookmarksCount: Int): Boolean
}

@ContributesBinding(AppScope::class, binding = binding<PasswordsInBookmarksImportPromotion>())
class RealPasswordsInBookmarksImportPromotion @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val autofillFeature: AutofillFeature,
    private val passwordImportAvailability: PasswordImportAvailability,
    private val dispatchers: DispatcherProvider,
) : PasswordsInBookmarksImportPromotion {

    override suspend fun canShowPromotion(importedBookmarksCount: Int): Boolean {
        if (importedBookmarksCount == 0) return false

        return coroutineScope {
            val preconditions = listOf(
                async { isPromotionEnabled() },
                async { passwordImportAvailability.canImport() },
                async { hasFewSavedPasswords() },
            ).awaitAll()
            preconditions.all { it }
        }
    }

    private suspend fun isPromotionEnabled(): Boolean = withContext(dispatchers.io()) {
        autofillFeature.self().isEnabled() &&
            autofillFeature.showCrossFeaturePromo().isEnabled() &&
            autofillFeature.canImportFromGooglePasswordManager().isEnabled()
    }

    private suspend fun hasFewSavedPasswords(): Boolean {
        val savedPasswordsCount = autofillStore.getCredentialCount().firstOrNull()?.getOrNull() ?: return false
        return savedPasswordsCount < MAX_SAVED_PASSWORDS
    }

    private companion object {
        const val MAX_SAVED_PASSWORDS = 9
    }
}
