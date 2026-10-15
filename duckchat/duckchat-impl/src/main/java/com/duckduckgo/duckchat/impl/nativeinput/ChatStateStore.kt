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
import com.duckduckgo.duckchat.impl.ChatState
import dagger.SingleInstanceIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Duck.ai response state (streaming, hidden input, ...) per native input surface, keyed the same way as
 * [com.duckduckgo.duckchat.api.nativeinput.NativeInputStateProvider] so each tab only reacts to its own chat.
 */
@SingleInstanceIn(AppScope::class)
class ChatStateStore @Inject constructor() {

    private val flows = ConcurrentHashMap<String, MutableStateFlow<ChatState>>()

    fun stateFor(key: String): StateFlow<ChatState> = flowFor(key)

    fun update(key: String, state: ChatState) {
        flowFor(key).value = state
    }

    fun clear(key: String) {
        flows.remove(key)
    }

    fun clearAll() {
        flows.clear()
    }

    private fun flowFor(key: String): MutableStateFlow<ChatState> =
        flows.computeIfAbsent(key) { MutableStateFlow(ChatState.HIDE) }
}
