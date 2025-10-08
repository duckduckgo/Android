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

package com.duckduckgo.autofill.impl.store

import com.duckduckgo.autofill.impl.securestorage.SecureStorage
import com.duckduckgo.autofill.impl.urlmatcher.AutofillUrlMatcher
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface NeverSavedSiteRepository {
    suspend fun addToNeverSaveList(url: String)
    suspend fun clearNeverSaveList()
    suspend fun neverSaveListCount(): Flow<Int>
    suspend fun isInNeverSaveList(url: String): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealNeverSavedSiteRepository @Inject constructor(
    private val autofillUrlMatcher: AutofillUrlMatcher,
    private val dispatchers: DispatcherProvider,
    private val secureStorage: SecureStorage,
) : NeverSavedSiteRepository {

    override suspend fun addToNeverSaveList(url: String) = withContext(dispatchers.io()) {
        val domainToAdd = url.extractEffectiveTldPlusOne()
        secureStorage.addToNeverSaveList(domainToAdd)
    }

    override suspend fun clearNeverSaveList() = withContext(dispatchers.io()) {
        secureStorage.clearNeverSaveList()
    }

    override suspend fun neverSaveListCount(): Flow<Int> {
        return secureStorage.neverSaveListCount()
    }

    override suspend fun isInNeverSaveList(url: String): Boolean {
        return withContext(dispatchers.io()) {
            val domainToAdd = url.extractEffectiveTldPlusOne()
            secureStorage.isInNeverSaveList(domainToAdd)
        }
    }

    private fun String.extractEffectiveTldPlusOne(): String {
        val urlParts = autofillUrlMatcher.extractUrlPartsForAutofill(this)
        return urlParts.eTldPlus1 ?: this
    }
}
