/*
 * Copyright (c) 2024 DuckDuckGo
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

import androidx.collection.LruCache
import com.duckduckgo.autofill.api.AutofillFeature
import com.duckduckgo.autofill.impl.time.TimeProvider
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.withContext
import logcat.LogPriority.VERBOSE
import logcat.LogPriority.WARN
import logcat.logcat

interface PartialCredentialSaveStore {
    suspend fun saveUsername(
        url: String,
        username: String,
    )

    suspend fun getUsernameForBackFilling(url: String): String?
    suspend fun wasBackFilledRecently(
        url: String,
        username: String,
    ): Boolean
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class PartialCredentialSaveInMemoryStore @Inject constructor(
    private val urlMatcher: AutofillUrlMatcher,
    private val timeProvider: TimeProvider,
    private val dispatchers: DispatcherProvider,
    private val autofillFeature: AutofillFeature,
) : PartialCredentialSaveStore {

    private val backFillHistory = LruCache<String, PartialSave>(5)

    override suspend fun saveUsername(
        url: String,
        username: String,
    ) {
        withContext(dispatchers.io()) {
            if (!autofillFeature.partialFormSaves().isEnabled()) {
                logcat(WARN) { "Partial form saves are disabled. Not saving username [$username] for $url" }
                return@withContext
            }

            val etldPlusOne = extractEtldPlusOne(url) ?: return@withContext
            logcat(VERBOSE) { "Storing username [$username] as a backFill candidate for $etldPlusOne" }
            backFillHistory.put(etldPlusOne, PartialSave(username = username, creationTimestamp = timeProvider.currentTimeMillis()))
        }
    }

    /**
     * If a potential backFill username can be used for the given URL it will be returned
     */
    override suspend fun getUsernameForBackFilling(url: String): String? {
        return withContext(dispatchers.io()) {
            if (!autofillFeature.partialFormSaves().isEnabled()) {
                logcat(WARN) { "Partial form saves are disabled. Not checking for username for $url" }
                return@withContext null
            }

            val etldPlusOne = extractEtldPlusOne(url) ?: return@withContext null
            val activeBackFill = backFillHistory[etldPlusOne] ?: return@withContext null

            if (activeBackFill.isExpired()) {
                logcat(VERBOSE) { "Found expired username [$activeBackFill.username] for $etldPlusOne. Not using for backFill." }
                return@withContext null
            }

            backFillHistory.put(etldPlusOne, activeBackFill.copy(lastConsumedTimestamp = timeProvider.currentTimeMillis()))
            activeBackFill.username
        }
    }

    override suspend fun wasBackFilledRecently(
        url: String,
        username: String,
    ): Boolean {
        return withContext(dispatchers.io()) {
            if (!autofillFeature.partialFormSaves().isEnabled()) {
                logcat(WARN) { "Partial form saves are disabled. Cannot have been backFilled recently." }
                return@withContext false
            }

            val etldPlusOne = extractEtldPlusOne(url) ?: return@withContext false
            val partialSave = backFillHistory[etldPlusOne] ?: return@withContext false
            if (partialSave.username != username) return@withContext false

            partialSave.consumedRecently(timeProvider)
        }
    }

    private fun extractEtldPlusOne(url: String) = urlMatcher.extractUrlPartsForAutofill(url).eTldPlus1

    private fun PartialSave.isExpired(): Boolean {
        return (timeProvider.currentTimeMillis() - creationTimestamp) > MAX_VALIDITY_MS
    }

    data class PartialSave(
        val username: String,
        val creationTimestamp: Long,
        val lastConsumedTimestamp: Long? = null,
    ) {
        fun consumedRecently(timeProvider: TimeProvider): Boolean {
            return lastConsumedTimestamp?.let { timeProvider.currentTimeMillis() - it < TIME_WINDOW_FOR_BEING_RECENT_MS } ?: false
        }
    }

    companion object {
        val MAX_VALIDITY_MS = TimeUnit.MINUTES.toMillis(3)
        val TIME_WINDOW_FOR_BEING_RECENT_MS = TimeUnit.SECONDS.toMillis(10)
    }
}
