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

package com.duckduckgo.duckchat.impl.history

import androidx.lifecycle.ViewModel
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.di.scopes.FragmentScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@ContributesViewModel(FragmentScope::class)
class RenameChatViewModel @Inject constructor(
    private val chatHistoryRepository: ChatHistoryRepository,
    @AppCoroutineScope private val appScope: CoroutineScope,
) : ViewModel() {

    private val resultChannel = Channel<RenameResult>(capacity = Channel.BUFFERED, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val results: Flow<RenameResult> = resultChannel.receiveAsFlow()

    fun onSaveClicked(chatId: String, newTitle: String) {
        appScope.launch {
            runCatching { chatHistoryRepository.renameChat(chatId, newTitle.trim()) }
                .onSuccess { resultChannel.trySend(RenameResult.Success) }
                .onFailure { error -> resultChannel.trySend(RenameResult.Error(error)) }
        }
    }

    sealed interface RenameResult {
        data object Success : RenameResult
        data class Error(val throwable: Throwable) : RenameResult
    }
}
