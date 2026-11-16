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

import com.duckduckgo.appbuildconfig.api.AppBuildConfig
import com.duckduckgo.appbuildconfig.api.isInternalBuild
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.site.permissions.api.ClientToolPermission
import com.duckduckgo.site.permissions.api.ClientToolPermissionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

interface ClientToolPermissionGate {
    suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision

    /** Settles every call of [chatTabId] still waiting for a decision as [ClientToolPermissionDecision.CANCELLED]. */
    fun cancelPending(chatTabId: String)
}

enum class ClientToolPermissionDecision {
    ALLOWED,
    DENIED,
    CANCELLED,
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealClientToolPermissionGate @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val browserToolsFeature: AiChatBrowserToolsFeature,
    private val store: ClientToolPermissionStore,
    private val prompt: ClientToolPermissionPrompt,
) : ClientToolPermissionGate {

    private val promptLock = Mutex()
    private val waitingByChat = mutableMapOf<String, MutableSet<CompletableDeferred<Unit>>>()

    override suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision {
        val mode = tool.permissionMode
        if (mode !is ClientToolPermissionMode.Ask || skipsPromptForDevelopment()) {
            return ClientToolPermissionDecision.ALLOWED
        }
        val chatGone = startWaiting(context.chatTabId)
        try {
            return withTimeoutOrNull(DECISION_DEADLINE_MILLIS) {
                coroutineScope {
                    val decision = async {
                        storedDecision(tool, context) ?: promptLock.withLock { storedDecision(tool, context) ?: ask(tool, mode, context) }
                    }
                    select {
                        decision.onAwait { it }
                        chatGone.onAwait {
                            decision.cancel()
                            ClientToolPermissionDecision.CANCELLED
                        }
                    }
                }
            } ?: ClientToolPermissionDecision.CANCELLED
        } finally {
            stopWaiting(context.chatTabId, chatGone)
        }
    }

    override fun cancelPending(chatTabId: String) {
        val waiting = synchronized(waitingByChat) { waitingByChat.remove(chatTabId) }
        waiting?.forEach { it.complete(Unit) }
    }

    private fun skipsPromptForDevelopment(): Boolean =
        appBuildConfig.isInternalBuild() && browserToolsFeature.devSkipPermissionPrompt().isEnabled()

    private suspend fun storedDecision(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision? = when (store.get(context.origin, tool.name)) {
        ClientToolPermission.ALWAYS_ALLOW -> ClientToolPermissionDecision.ALLOWED
        ClientToolPermission.NEVER_ALLOW -> ClientToolPermissionDecision.DENIED
        null -> null
    }

    private suspend fun ask(
        tool: ClientTool,
        mode: ClientToolPermissionMode.Ask,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision {
        val request = ClientToolPermissionRequest(context.origin, tool.name, context.chatTabId, context.browserMode, mode)
        return when (prompt.request(request)) {
            ClientToolPermissionAnswer.ALLOW_ONCE -> ClientToolPermissionDecision.ALLOWED
            ClientToolPermissionAnswer.ALWAYS -> {
                store.set(context.origin, tool.name, ClientToolPermission.ALWAYS_ALLOW)
                ClientToolPermissionDecision.ALLOWED
            }
            ClientToolPermissionAnswer.NEVER -> {
                store.set(context.origin, tool.name, ClientToolPermission.NEVER_ALLOW)
                ClientToolPermissionDecision.DENIED
            }
            ClientToolPermissionAnswer.DISMISSED -> ClientToolPermissionDecision.CANCELLED
        }
    }

    private fun startWaiting(chatTabId: String): CompletableDeferred<Unit> {
        val chatGone = CompletableDeferred<Unit>()
        synchronized(waitingByChat) { waitingByChat.getOrPut(chatTabId) { mutableSetOf() }.add(chatGone) }
        return chatGone
    }

    private fun stopWaiting(
        chatTabId: String,
        chatGone: CompletableDeferred<Unit>,
    ) {
        synchronized(waitingByChat) {
            val waiting = waitingByChat[chatTabId] ?: return
            waiting.remove(chatGone)
            if (waiting.isEmpty()) waitingByChat.remove(chatTabId)
        }
    }

    private companion object {
        // Measured from the call's arrival, so time spent queued behind another prompt counts.
        const val DECISION_DEADLINE_MILLIS = 120_000L
    }
}
