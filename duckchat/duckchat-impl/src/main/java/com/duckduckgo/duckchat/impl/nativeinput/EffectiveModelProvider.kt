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

package com.duckduckgo.duckchat.impl.nativeinput

import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

/**
 * The model whose capabilities the native input controls should reflect for the active tab. Shared so
 * the model picker and the options menu resolve it identically instead of one asking the other.
 */
interface EffectiveModelProvider {

    val effectiveModelId: Flow<String?>

    /**
     * Records the model picked during the FE model-change recovery flow. It wins over the chat's stored
     * model while that window is open, because the FE syncs the new model back to us asynchronously.
     */
    fun onRecoveryModelPicked(modelId: String)
}

@OptIn(ExperimentalCoroutinesApi::class)
@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealEffectiveModelProvider @Inject constructor(
    private val modelManager: DuckAiModelManager,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val duckAiChatStore: DuckAiChatStore,
) : EffectiveModelProvider {

    private data class ActiveChat(
        val chatId: String?,
        val modelChangeMode: Boolean,
        val chatModel: String?,
    )

    private val recoveryModelId = MutableStateFlow<String?>(null)

    // mapLatest: a chatId flip cancels an in-flight lookup, so a slow read can't resolve into a stale chat.
    private val activeChat: Flow<ActiveChat> = nativeInputStateProvider.state
        .map { it.chatId to it.modelChangeMode }
        .distinctUntilChanged()
        .mapLatest { (chatId, modelChangeMode) ->
            ActiveChat(
                chatId = chatId,
                modelChangeMode = modelChangeMode,
                chatModel = chatId?.let { duckAiChatStore.getChatById(it)?.model },
            )
        }

    override val effectiveModelId: Flow<String?> = combine(
        modelManager.modelState,
        activeChat,
        recoveryModelId,
    ) { modelState, chat, recovery ->
        val modelIds = modelState.models.mapTo(HashSet()) { it.id }
        // Honoured only while the model-change window is open; it closes per tab, so a stale pick cannot leak.
        val recovered = recovery
            ?.takeIf { chat.modelChangeMode }
            ?.takeIf { it in modelIds }
        recovered ?: chat.chatModel?.takeIf { it in modelIds } ?: modelState.selectedModelId
    }.distinctUntilChanged()

    override fun onRecoveryModelPicked(modelId: String) {
        recoveryModelId.value = modelId
    }
}
