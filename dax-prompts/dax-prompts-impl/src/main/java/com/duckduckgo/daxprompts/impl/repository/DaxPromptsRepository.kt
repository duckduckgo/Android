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

package com.duckduckgo.daxprompts.impl.repository

import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.daxprompts.impl.store.DaxPromptsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.withContext

interface DaxPromptsRepository {
    suspend fun setDaxPromptsShowBrowserComparison(show: Boolean)
    suspend fun getDaxPromptsShowBrowserComparison(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDaxPromptsRepository @Inject constructor(
    private val daxPromptsDataStore: DaxPromptsDataStore,
    private val dispatchers: DispatcherProvider,
) : DaxPromptsRepository {
    private var showBrowserComparison: Boolean? = null

    override suspend fun setDaxPromptsShowBrowserComparison(show: Boolean) {
        showBrowserComparison = show
        withContext(dispatchers.io()) {
            daxPromptsDataStore.setDaxPromptsShowBrowserComparison(show)
        }
    }

    override suspend fun getDaxPromptsShowBrowserComparison(): Boolean {
        return showBrowserComparison ?: daxPromptsDataStore.getDaxPromptsShowBrowserComparison()
    }
}
