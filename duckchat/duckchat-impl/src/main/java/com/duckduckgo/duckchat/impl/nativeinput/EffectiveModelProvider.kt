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

import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider
import com.duckduckgo.duckchat.impl.models.DuckAiModelManager
import com.duckduckgo.duckchat.store.impl.DuckAiChat
import com.duckduckgo.duckchat.store.impl.DuckAiChatStore
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The model whose capabilities the native input controls should reflect for the active tab. Shared so
 * the model picker and the options menu resolve it identically instead of one asking the other.
 */
sealed interface EffectiveModel {

    /**
     * The active tab's chat has not been read back yet, so no model can be attributed to it. Controls
     * must not act on a model during this window: it would be the previously selected tab's.
     */
    data object Unresolved : EffectiveModel

    data class Resolved(val modelId: String?) : EffectiveModel
}

interface EffectiveModelProvider {

    val effectiveModel: Flow<EffectiveModel>

    /**
     * Records the model picked during [chatId]'s FE model-change window. It wins over that chat's stored
     * model while the window is open, because the FE syncs the new model back to us asynchronously.
     */
    fun onRecoveryModelPicked(chatId: String?, modelId: String)

    /** Drops [chatId]'s pick when its window closes, leaving any other chat's window untouched. */
    fun clearRecoveryModelPick(chatId: String?)
}

// ActivityScope, not AppScope: the unqualified DuckAiChatStore is bound per activity so it can resolve
// the browser mode. One instance per activity is what the plugins sharing a widget need anyway.
@SingleInstanceIn(ActivityScope::class)
@ContributesBinding(ActivityScope::class)
class RealEffectiveModelProvider @Inject constructor(
    private val modelManager: DuckAiModelManager,
    private val nativeInputStateProvider: NativeInputStateProvider,
    private val duckAiChatStore: DuckAiChatStore,
) : EffectiveModelProvider {

    private data class ActiveChat(
        val chatId: String?,
        val modelChangeMode: Boolean,
        val chatModel: String?,
        val resolved: Boolean,
    )

    // Keyed by chat: two tabs can sit in a model-change window at once, and each keeps its own pick.
    private val recoveryPicks = MutableStateFlow<Map<String?, String>>(emptyMap())

    // Observed rather than read once per chatId, so a model the FE writes back later still lands.
    private val chats: Flow<List<DuckAiChat>?> = duckAiChatStore.getChatsFlow()
        .map<List<DuckAiChat>, List<DuckAiChat>?> { it }
        .onStart { emit(null) }

    private val activeChat: Flow<ActiveChat> = combine(
        nativeInputStateProvider.state.map { it.chatId to it.modelChangeMode }.distinctUntilChanged(),
        chats,
    ) { (chatId, modelChangeMode), chats ->
        when {
            chatId == null -> ActiveChat(null, modelChangeMode, null, resolved = true)
            chats == null -> ActiveChat(chatId, modelChangeMode, null, resolved = false)
            else -> ActiveChat(chatId, modelChangeMode, chats.firstOrNull { it.chatId == chatId }?.model, resolved = true)
        }
    }

    override val effectiveModel: Flow<EffectiveModel> = combine(
        modelManager.modelState,
        activeChat,
        recoveryPicks,
    ) { modelState, chat, picks ->
        if (!chat.resolved) return@combine EffectiveModel.Unresolved
        val modelIds = modelState.models.mapTo(HashSet()) { it.id }
        val recovered = picks[chat.chatId]
            ?.takeIf { chat.modelChangeMode }
            ?.takeIf { it in modelIds }
        EffectiveModel.Resolved(recovered ?: chat.chatModel?.takeIf { it in modelIds } ?: modelState.selectedModelId)
    }.distinctUntilChanged()

    override fun onRecoveryModelPicked(chatId: String?, modelId: String) {
        recoveryPicks.update { it + (chatId to modelId) }
    }

    override fun clearRecoveryModelPick(chatId: String?) {
        recoveryPicks.update { it - chatId }
    }
}
