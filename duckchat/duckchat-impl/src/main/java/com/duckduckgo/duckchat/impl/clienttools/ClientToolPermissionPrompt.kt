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

package com.duckduckgo.duckchat.impl.clienttools

import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.di.scopes.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

interface ClientToolPermissionPrompt {
    /** Shows the prompt and returns the user's answer. Cancelling the caller withdraws the prompt. */
    suspend fun request(request: ClientToolPermissionRequest): ClientToolPermissionAnswer
}

/** What the prompt asks for, in the chat of [chatTabId]. */
sealed interface ClientToolPermissionRequest {
    val chatTabId: String
    val browserMode: BrowserMode

    /** Asks to run one tool now. */
    data class RunTool(
        override val chatTabId: String,
        override val browserMode: BrowserMode,
        val tool: AskingTool,
    ) : ClientToolPermissionRequest

    /**
     * Asks to use several tools for the rest of the chat.
     * @param toolsRemovable whether the user may uncheck tools before allowing the rest.
     */
    data class GrantAccess(
        override val chatTabId: String,
        override val browserMode: BrowserMode,
        val tools: List<AskingTool>,
        val toolsRemovable: Boolean,
    ) : ClientToolPermissionRequest
}

data class AskingTool(
    val name: String,
    val copy: ClientToolPermissionMode.Ask,
)

sealed interface ClientToolPermissionAnswer {
    /**
     * Allows the asked tools for the chat, except [removedTools]: the ones the user unchecked in a [ClientToolPermissionRequest.GrantAccess]
     * prompt. Those stay unanswered, so the next call or request for them asks again.
     */
    data class AllowForChat(val removedTools: Set<String> = emptySet()) : ClientToolPermissionAnswer

    data object DontAllow : ClientToolPermissionAnswer
}

/** A prompt waiting for the user. Only the first [answer] counts, and answers after it is withdrawn are ignored. */
class PendingClientToolPermission(
    val request: ClientToolPermissionRequest,
    private val reply: CompletableDeferred<ClientToolPermissionAnswer>,
) {
    val isOpen: Boolean get() = !reply.isCompleted

    fun answer(answer: ClientToolPermissionAnswer) {
        reply.complete(answer)
    }
}

/** Publishes the open prompt for [ClientToolPermissionDialogPresenter] to show; the gate keeps it to one at a time. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class PublishingClientToolPermissionPrompt @Inject constructor() : ClientToolPermissionPrompt {

    private val _pending = MutableStateFlow<PendingClientToolPermission?>(null)
    val pending: StateFlow<PendingClientToolPermission?> = _pending.asStateFlow()

    override suspend fun request(request: ClientToolPermissionRequest): ClientToolPermissionAnswer {
        val reply = CompletableDeferred<ClientToolPermissionAnswer>()
        val prompt = PendingClientToolPermission(request, reply)
        _pending.value = prompt
        try {
            return reply.await()
        } finally {
            reply.cancel()
            _pending.compareAndSet(prompt, null)
        }
    }
}
