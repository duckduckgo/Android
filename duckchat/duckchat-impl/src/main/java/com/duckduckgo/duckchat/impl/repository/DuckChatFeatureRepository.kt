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
    suspend fun setDuckChatUserEnabled(enabled: Boolean)
    suspend fun setShowInBrowserMenu(showDuckChat: Boolean)
    suspend fun setShowInAddressBar(showDuckChat: Boolean)

    fun observeDuckChatUserEnabled(): Flow<Boolean>
    fun observeShowInBrowserMenu(): Flow<Boolean>
    fun observeShowInAddressBar(): Flow<Boolean>

    suspend fun isDuckChatUserEnabled(): Boolean
    suspend fun shouldShowInBrowserMenu(): Boolean
    suspend fun shouldShowInAddressBar(): Boolean

    suspend fun registerOpened()
    suspend fun wasOpenedBefore(): Boolean
    suspend fun lastSessionTimestamp(): Long
    suspend fun sessionDeltaTimestamp(): Long
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealDuckChatFeatureRepository @Inject constructor(
    private val duckChatDataStore: DuckChatDataStore,
) : DuckChatFeatureRepository {
    override suspend fun setDuckChatUserEnabled(enabled: Boolean) {
        duckChatDataStore.setDuckChatUserEnabled(enabled)
    }

    override suspend fun setShowInBrowserMenu(showDuckChat: Boolean) {
        duckChatDataStore.setShowInBrowserMenu(showDuckChat)
    }

    override suspend fun setShowInAddressBar(showDuckChat: Boolean) {
        duckChatDataStore.setShowInAddressBar(showDuckChat)
    }

    override fun observeDuckChatUserEnabled(): Flow<Boolean> {
        return duckChatDataStore.observeDuckChatUserEnabled()
    }

    override fun observeShowInBrowserMenu(): Flow<Boolean> {
        return duckChatDataStore.observeShowInBrowserMenu()
    }

    override fun observeShowInAddressBar(): Flow<Boolean> {
        return duckChatDataStore.observeShowInAddressBar()
    }

    override suspend fun isDuckChatUserEnabled(): Boolean {
        return duckChatDataStore.isDuckChatUserEnabled()
    }

    override suspend fun shouldShowInBrowserMenu(): Boolean {
        return duckChatDataStore.getShowInBrowserMenu()
    }

    override suspend fun shouldShowInAddressBar(): Boolean {
        return duckChatDataStore.getShowInAddressBar()
    }

    override suspend fun registerOpened() {
        duckChatDataStore.registerOpened()
    }

    override suspend fun wasOpenedBefore(): Boolean {
        return duckChatDataStore.wasOpenedBefore()
    }

    override suspend fun lastSessionTimestamp(): Long {
        return duckChatDataStore.lastSessionTimestamp()
    }

    override suspend fun sessionDeltaTimestamp(): Long {
        return duckChatDataStore.sessionDeltaTimestamp()
    }
}
