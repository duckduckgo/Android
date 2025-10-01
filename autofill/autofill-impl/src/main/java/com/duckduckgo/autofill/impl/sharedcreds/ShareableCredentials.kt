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

package com.duckduckgo.autofill.impl.sharedcreds

import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.autofill.api.domain.app.LoginCredentials
import com.duckduckgo.autofill.impl.sharedcreds.SharedCredentialsParser.SharedCredentialConfig
import com.duckduckgo.autofill.impl.store.InternalAutofillStore
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher.ExtractedUrlParts
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart.LAZY
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface ShareableCredentials {
    suspend fun shareableCredentials(sourceUrl: String): List<LoginCredentials>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AppleShareableCredentials @Inject constructor(
    private val jsonParser: SharedCredentialsParser,
    private val dispatchers: DispatcherProvider,
    private val shareableCredentialsUrlGenerator: ShareableCredentialsUrlGenerator,
    private val autofillStore: InternalAutofillStore,
    private val autofillUrlMatcher: AutofillUrlMatcher,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ShareableCredentials {

    private val sharedCredentialConfig: Deferred<SharedCredentialConfig> = appCoroutineScope.async(start = LAZY) {
        jsonParser.read()
    }

    override suspend fun shareableCredentials(sourceUrl: String): List<LoginCredentials> {
        return withContext(dispatchers.io()) {
            val config = sharedCredentialConfig.await()
            val shareableUrls = shareableCredentialsUrlGenerator.generateShareableUrls(sourceUrl, config)
            return@withContext matchingCredentials(shareableUrls)
        }
    }

    private suspend fun matchingCredentials(
        shareableUrls: List<ExtractedUrlParts>,
    ): List<LoginCredentials> {
        val logins = mutableListOf<LoginCredentials>()
        shareableUrls.forEach { shareableUrlParts ->
            val eTldPlusOne = shareableUrlParts.eTldPlus1
            logins.addAll(
                if (eTldPlusOne != null) {
                    autofillStore.getCredentials(eTldPlusOne)
                } else {
                    emptyList()
                },
            )
        }

        return logins.distinct()
    }
}
