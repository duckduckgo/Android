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

package com.duckduckgo.autofill.impl.partialsave

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillNotSupported
import com.duckduckgo.autofill.impl.partialsave.UsernameBackFiller.BackFillResult.BackFillSupported
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import javax.inject.Inject
import timber.log.Timber

interface UsernameBackFiller {
    suspend fun isBackFillingUsernameSupported(
        usernameFromJavascript: String?,
        currentUrl: String,
    ): BackFillResult

    sealed interface BackFillResult {
        data class BackFillSupported(val username: String) : BackFillResult
        data object BackFillNotSupported : BackFillResult
    }
}

@ContributesBinding(AppScope::class)
class UsernameBackFillerImpl @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val partialCredentialSaveStore: PartialCredentialSaveStore,
) : UsernameBackFiller {

    override suspend fun isBackFillingUsernameSupported(
        usernameFromJavascript: String?,
        currentUrl: String,
    ): BackFillResult {
        if (!autofillFeature.partialFormSaves().isEnabled()) {
            return BackFillNotSupported
        }

        if (!usernameFromJavascript.isNullOrBlank()) {
            Timber.v("BackFilling username from partial save not supported because username provided already, for %s", currentUrl)
            return BackFillNotSupported
        } else {
            val username = partialCredentialSaveStore.getUsernameForBackFilling(currentUrl) ?: return BackFillNotSupported.also {
                Timber.v("BackFilling username from partial save not supported, for %s", currentUrl)
            }

            Timber.v("BackFilling username [%s] from partial save is supported, for %s", username, currentUrl)
            return BackFillSupported(username)
        }
    }
}
