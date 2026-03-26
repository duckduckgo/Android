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

package com.duckduckgo.autofill.impl.username

import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AutofillUsernameComparer {
    suspend fun isEqual(
        username1: String?,
        username2: String?,
    ): Boolean

    suspend fun groupUsernamesAndPasswords(logins: List<LoginCredentials>): Map<Pair<String?, String?>, List<LoginCredentials>>
}

@ContributesBinding(AppScope::class)
class RealAutofillUsernameComparer @Inject constructor(
    private val autofillFeature: AutofillFeature,
    private val dispatchers: DispatcherProvider,
) : AutofillUsernameComparer {

    override suspend fun isEqual(username1: String?, username2: String?): Boolean {
        return withContext(dispatchers.io()) {
            if (username1 == null && username2 == null) return@withContext true
            if (username1 == null) return@withContext false
            if (username2 == null) return@withContext false

            username1.equals(username2, ignoreCase = autofillFeature.ignoreCaseOnUsernameComparisons().isEnabled())
        }
    }
    override suspend fun groupUsernamesAndPasswords(logins: List<LoginCredentials>): Map<Pair<String?, String?>, List<LoginCredentials>> {
        return if (autofillFeature.ignoreCaseOnUsernameComparisons().isEnabled()) {
            logins.groupBy { it.username?.lowercase() to it.password }
        } else {
            logins.groupBy { it.username to it.password }
        }
    }
}
