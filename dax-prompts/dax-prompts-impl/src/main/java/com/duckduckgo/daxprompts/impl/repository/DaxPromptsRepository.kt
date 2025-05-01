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

import com.duckduckgo.daxprompts.impl.store.DaxPromptsDataStore
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject

interface DaxPromptsRepository {
    suspend fun setDaxPromptsShowDuckPlayer(show: Boolean)
    suspend fun getDaxPromptsShowDuckPlayer(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDaxPromptsRepositoryRepository @Inject constructor(
    private val daxPromptsDataStore: DaxPromptsDataStore,
) : DaxPromptsRepository {

    private var showDuckPlayer: Boolean? = null

    override suspend fun setDaxPromptsShowDuckPlayer(show: Boolean) {
        showDuckPlayer = show
        daxPromptsDataStore.setDaxPromptsShowDuckPlayer(show)
    }

    override suspend fun getDaxPromptsShowDuckPlayer(): Boolean {
        return showDuckPlayer ?: daxPromptsDataStore.getDaxPromptsShowDuckPlayer()
    }
}
