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

package com.duckduckgo.duckchat.impl.inputscreen.ui.suggestions

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import dagger.SingleInstanceIn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

interface ChatHistoryStore {
    val hasChatHistory: Flow<Boolean>
}

@SingleInstanceIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ChatHistoryStore::class)
class RealChatHistoryStore @Inject constructor() : ChatHistoryStore {
    private val _hasChatHistory = MutableSharedFlow<Boolean>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val hasChatHistory = _hasChatHistory.distinctUntilChanged()

    suspend fun setHasChatHistory(hasHistory: Boolean) {
        _hasChatHistory.emit(hasHistory)
    }
}
