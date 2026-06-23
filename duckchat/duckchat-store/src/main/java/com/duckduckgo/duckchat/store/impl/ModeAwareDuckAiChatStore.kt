/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.store.impl

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.browsermode.api.BrowserModeStateHolder
import com.duckduckgo.browsermode.api.FireMode
import com.duckduckgo.browsermode.api.RegularMode
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesTo
import dagger.Module
import dagger.Provides
import dagger.SingleInstanceIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import javax.inject.Inject

/**
 * The unqualified [DuckAiChatStore] binding. Delegates every call to the store of the current
 * [BrowserMode] (read from [BrowserModeStateHolder]) so existing consumers become mode-aware with no change.
 *
 * Reading the global current mode is correct for these native UI surfaces (history, suggestions, the input
 * widgets): the user is in exactly one mode at a time and a mode switch recreates the hosting activity, so
 * `currentMode.value` reflects the surface's mode. The Duck.ai JS-bridge storage handler relies on the same
 * one-mode-per-activity invariant, but being activity-scoped it reads the frozen [BrowserMode] injected into
 * its activity graph rather than the global state holder.
 *
 * Consumers that must NOT follow the current mode (e.g. sync, which must never read Fire chats) inject the
 * `@RegularMode` / `@FireMode` qualified store directly instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ModeAwareDuckAiChatStore @Inject constructor(
    @RegularMode private val regular: DuckAiChatStore,
    @FireMode private val fire: DuckAiChatStore,
    private val browserModeStateHolder: BrowserModeStateHolder,
) : DuckAiChatStore {

    private val current: DuckAiChatStore
        get() = forMode(browserModeStateHolder.currentMode.value)

    private fun forMode(mode: BrowserMode): DuckAiChatStore = when (mode) {
        BrowserMode.REGULAR -> regular
        BrowserMode.FIRE -> fire
    }

    override suspend fun hasMigrated(): Boolean = current.hasMigrated()

    override suspend fun getChats(): List<DuckAiChat> = current.getChats()

    override suspend fun getChatById(chatId: String): DuckAiChat? = current.getChatById(chatId)

    override fun getChatsFlow(): Flow<List<DuckAiChat>> =
        browserModeStateHolder.currentMode.flatMapLatest { mode -> forMode(mode).getChatsFlow() }

    override suspend fun deleteChat(chatId: String): Boolean = current.deleteChat(chatId)

    override suspend fun deleteAllChats() = current.deleteAllChats()

    override suspend fun renameChat(chatId: String, newTitle: String): Boolean = current.renameChat(chatId, newTitle)

    override suspend fun pinChat(chatId: String) = current.pinChat(chatId)

    override suspend fun unpinChat(chatId: String) = current.unpinChat(chatId)

    override suspend fun getChatContent(chatId: String): String? = current.getChatContent(chatId)

    override suspend fun readFileRef(uuid: String): FileRefContent? = current.readFileRef(uuid)
}

/**
 * Provides the per-mode [RealDuckAiChatStore] instances bound to the qualified [DuckAiChatStore]. Migration
 * prefs are shared across modes (one-time, app-level event).
 */
@ContributesTo(AppScope::class)
@Module
object DuckAiChatStoreModule {

    @Provides
    @SingleInstanceIn(AppScope::class)
    @RegularMode
    fun provideRegularChatStore(
        @RegularMode storage: DuckAiBridgeStorage,
        dispatchers: DispatcherProvider,
        migrationPrefs: DuckAiMigrationPrefs,
    ): DuckAiChatStore = RealDuckAiChatStore(storage, dispatchers, migrationPrefs)

    @Provides
    @SingleInstanceIn(AppScope::class)
    @FireMode
    fun provideFireChatStore(
        @FireMode storage: DuckAiBridgeStorage,
        dispatchers: DispatcherProvider,
        migrationPrefs: DuckAiMigrationPrefs,
    ): DuckAiChatStore = RealDuckAiChatStore(storage, dispatchers, migrationPrefs)
}
