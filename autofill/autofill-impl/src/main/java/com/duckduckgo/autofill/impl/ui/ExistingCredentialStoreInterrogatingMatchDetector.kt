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

package com.duckduckgo.autofill.impl.ui

import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector
import com.duckduckgo.autofill.api.ExistingCredentialMatchDetector.ContainsCredentialsResult
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.common.utils.DefaultDispatcherProvider
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

@ContributesBinding(AppScope::class)
class ExistingCredentialStoreInterrogatingMatchDetector @Inject constructor(
    private val autofillStore: InternalAutofillStore,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) :
    ExistingCredentialMatchDetector {

    override suspend fun determine(currentUrl: String, username: String?, password: String?): ContainsCredentialsResult {
        return withContext(dispatcherProvider.io()) {
            autofillStore.containsCredentials(currentUrl, username, password)
        }
    }
}
