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

import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class InMemoryInBrowserPromoPreviousPromptsStore @Inject constructor(
    private val urlMatcher: AutofillUrlMatcher,
    private val dispatchers: DispatcherProvider,
) : InBrowserPromoPreviousPromptsStore {

    private var previousETldPlusOneOffered: String? = null

    override suspend fun recordPromoDisplayed(originalUrl: String) {
        withContext(dispatchers.io()) {
            val eTldPlusOne = urlMatcher.extractUrlPartsForAutofill(originalUrl).eTldPlus1 ?: return@withContext
            previousETldPlusOneOffered = eTldPlusOne
        }
    }

    override suspend fun hasPromoBeenDisplayed(originalUrl: String): Boolean {
        if (previousETldPlusOneOffered == null) return false

        return withContext(dispatchers.io()) {
            val eTldPlusOne = urlMatcher.extractUrlPartsForAutofill(originalUrl).eTldPlus1 ?: return@withContext false
            eTldPlusOne.equals(previousETldPlusOneOffered, ignoreCase = true)
        }
    }

    override suspend fun clearPreviousPrompts() {
        previousETldPlusOneOffered = null
    }
}

interface InBrowserPromoPreviousPromptsStore {

    suspend fun recordPromoDisplayed(originalUrl: String)
    suspend fun hasPromoBeenDisplayed(originalUrl: String): Boolean
    suspend fun clearPreviousPrompts()
}
