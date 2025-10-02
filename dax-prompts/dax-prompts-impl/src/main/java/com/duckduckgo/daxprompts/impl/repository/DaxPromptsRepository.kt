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
import kotlinx.coroutines.withContext
import java.util.Date
import javax.inject.Inject

interface DaxPromptsRepository {
    suspend fun setDaxPromptsBrowserComparisonShown()
    suspend fun getDaxPromptsBrowserComparisonShown(): Boolean
    suspend fun getDaxPromptsBrowserComparisonShownInTheLast24Hours(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDaxPromptsRepository @Inject constructor(
    private val daxPromptsDataStore: DaxPromptsDataStore,
    private val dispatchers: DispatcherProvider,
) : DaxPromptsRepository {
    private var browserComparisonShown: Boolean? = null

    override suspend fun setDaxPromptsBrowserComparisonShown() {
        browserComparisonShown = true
        withContext(dispatchers.io()) {
            daxPromptsDataStore.setShownDaxPromptsBrowserComparison()
        }
    }

    override suspend fun getDaxPromptsBrowserComparisonShown(): Boolean {
        return browserComparisonShown ?: daxPromptsDataStore.getDaxPromptsBrowserComparisonShown()
    }

    override suspend fun getDaxPromptsBrowserComparisonShownInTheLast24Hours(): Boolean {
        return withContext(dispatchers.io()) {
            val shownTimestamp = daxPromptsDataStore.getDaxPromptsBrowserComparisonTimeStamp() ?: return@withContext false
            val currentTime = Date().time
            return@withContext (currentTime - shownTimestamp) <= TWENTY_FOUR_HOURS_IN_MILLIS
        }
    }

    companion object {
        private const val TWENTY_FOUR_HOURS_IN_MILLIS = 24 * 60 * 60 * 1000
    }
}
