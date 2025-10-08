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

package com.duckduckgo.app.autocomplete.impl

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface AutoCompleteRepository {

    suspend fun countHistoryInAutoCompleteIAMShown(): Int
    suspend fun dismissHistoryInAutoCompleteIAM()
    suspend fun wasHistoryInAutoCompleteIAMDismissed(): Boolean
    suspend fun submitUserSeenHistoryIAM()
}

@ContributesBinding(AppScope::class)
class RealAutoCompleteRepository @Inject constructor(
    private val dataStore: AutoCompleteDataStore,
    private val dispatcherProvider: DispatcherProvider,
) : AutoCompleteRepository {

    override suspend fun countHistoryInAutoCompleteIAMShown(): Int {
        return withContext(dispatcherProvider.io()) {
            dataStore.countHistoryInAutoCompleteIAMShown()
        }
    }

    override suspend fun dismissHistoryInAutoCompleteIAM() {
        withContext(dispatcherProvider.io()) {
            dataStore.setHistoryInAutoCompleteIAMDismissed()
        }
    }

    override suspend fun wasHistoryInAutoCompleteIAMDismissed(): Boolean {
        return withContext(dispatcherProvider.io()) {
            dataStore.wasHistoryInAutoCompleteIAMDismissed()
        }
    }

    override suspend fun submitUserSeenHistoryIAM() {
        withContext(dispatcherProvider.io()) {
            dataStore.incrementCountHistoryInAutoCompleteIAMShown()
        }
    }
}
