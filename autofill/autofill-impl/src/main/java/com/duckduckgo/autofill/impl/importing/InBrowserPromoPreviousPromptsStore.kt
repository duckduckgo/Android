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

/**
 * Interface to manage the state of whether the in-browser autofill promo has been displayed for a specific URL.
 */
interface InBrowserPromoPreviousPromptsStore {
    suspend fun recordPromoDisplayed(originalUrl: String)
    suspend fun hasPromoBeenDisplayed(originalUrl: String): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IntInMemoryInBrowserPromoPreviousPromptsStore @Inject constructor(
    private val urlMatcher: AutofillUrlMatcher,
    private val dispatchers: DispatcherProvider,
) : InternalInBrowserPromoStore {

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

    override suspend fun clear() {
        previousETldPlusOneOffered = null
    }
}

/**
 * Additional interface for internal/testing use
 */
interface InternalInBrowserPromoStore : InBrowserPromoPreviousPromptsStore {
    suspend fun clear()
}

@ContributesBinding(AppScope::class)
class DefaultInBrowserPromoPreviousPromptsStore @Inject constructor(
    private val internalStore: InternalInBrowserPromoStore,
) : InBrowserPromoPreviousPromptsStore by internalStore
