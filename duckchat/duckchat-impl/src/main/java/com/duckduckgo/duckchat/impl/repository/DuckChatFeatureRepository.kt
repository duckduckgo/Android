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

package com.duckduckgo.duckchat.impl.repository

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.store.DuckChatDataStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

interface DuckChatFeatureRepository {
    suspend fun setShowInBrowserMenu(showDuckChat: Boolean)
    fun observeShowInBrowserMenu(): Flow<Boolean>
    fun shouldShowInBrowserMenu(): Boolean
    suspend fun registerOpened()
    suspend fun wasOpenedBefore(): Boolean
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckChatFeatureRepository @Inject constructor(
    private val duckChatDataStore: DuckChatDataStore,
) : DuckChatFeatureRepository {

    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) {
        duckChatDataStore.setShowInBrowserMenu(showDuckChat)
    }

    override fun observeShowInBrowserMenu(): Flow<Boolean> {
        return duckChatDataStore.observeShowInBrowserMenu()
    }

    override fun shouldShowInBrowserMenu(): Boolean {
        return duckChatDataStore.getShowInBrowserMenu()
    }

    override suspend fun registerOpened() {
        duckChatDataStore.registerOpened()
    }

    override suspend fun wasOpenedBefore(): Boolean {
        return duckChatDataStore.wasOpenedBefore()
    }
}
