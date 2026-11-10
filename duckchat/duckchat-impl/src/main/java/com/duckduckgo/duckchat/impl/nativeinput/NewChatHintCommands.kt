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
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

sealed interface NewChatHintCommand {
    val tabId: String

    data class OpenDocuments(override val tabId: String, val mimeTypes: List<String>) : NewChatHintCommand
    data class OpenPhoto(override val tabId: String) : NewChatHintCommand
    data class CreateImage(override val tabId: String) : NewChatHintCommand
    data class OpenCamera(override val tabId: String) : NewChatHintCommand
}

@SingleIn(AppScope::class)
class NewChatHintCommands @Inject constructor() {
    private val commands = MutableSharedFlow<NewChatHintCommand>()

    fun commandsFor(tabId: () -> String?): Flow<NewChatHintCommand> = commands.filter { it.tabId == tabId() }

    private val pendingUntilAttached = ConcurrentHashMap<String, NewChatHintCommand>()

    suspend fun send(command: NewChatHintCommand) {
        commands.emit(command)
    }

    /**
     * Keeps a command for its tab until the view that handles it attaches and calls [takePending], for callers
     * that run before that view exists. The latest command per tab wins.
     */
    fun sendWhenAttached(command: NewChatHintCommand) {
        pendingUntilAttached[command.tabId] = command
    }

    fun takePending(tabId: String?): NewChatHintCommand? = tabId?.let { pendingUntilAttached.remove(it) }
}
