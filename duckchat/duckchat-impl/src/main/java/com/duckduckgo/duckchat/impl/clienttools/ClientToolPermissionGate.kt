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

    /**
     * Asks once for every tool in [tools] the user has not allowed yet, and grants the ones they allow for [chatId].
     * Never runs a tool.
     */
    suspend fun requestAccess(
        tools: List<ClientTool>,
        chatId: String,
        context: ClientToolCallContext,
    ): ClientToolAccessDecision

    /** Settles every call of [chatTabId] still waiting for a decision as cancelled. */
    fun cancelPending(chatTabId: String)
}

enum class ClientToolPermissionDecision {
    ALLOWED,
    DENIED,
    CANCELLED,
}

sealed interface ClientToolAccessDecision {
    /** The requested tools the chat may now use, in the order they were requested. */
    data class Granted(val toolNames: List<String>) : ClientToolAccessDecision

    data object Denied : ClientToolAccessDecision

    data object Cancelled : ClientToolAccessDecision
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealClientToolPermissionGate @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val browserToolsFeature: AiChatBrowserToolsFeature,
    private val store: ClientToolPermissionStore,
    private val chatGrants: ClientToolChatGrants,
    private val prompt: ClientToolPermissionPrompt,
) : ClientToolPermissionGate {

    private sealed interface Standing {
        data object Allowed : Standing

        data object Denied : Standing

        data class NeedsPrompt(val tool: AskingTool) : Standing
    }

    private val promptLock = Mutex()
    private val waitingByChat = mutableMapOf<String, MutableSet<CompletableDeferred<Unit>>>()

    override suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision {
        when (standing(tool, context)) {
            Standing.Allowed -> return ClientToolPermissionDecision.ALLOWED
            Standing.Denied -> return ClientToolPermissionDecision.DENIED
            is Standing.NeedsPrompt -> Unit
        }
        // Re-checked once the lock is ours: the prompt this call queued behind may have settled it.
        return awaitTurn(context.chatTabId, ClientToolPermissionDecision.CANCELLED) {
            when (val now = standing(tool, context)) {
                Standing.Allowed -> ClientToolPermissionDecision.ALLOWED
                Standing.Denied -> ClientToolPermissionDecision.DENIED
                is Standing.NeedsPrompt -> ask(now.tool, context)
            }
        }
    }

    override suspend fun requestAccess(
        tools: List<ClientTool>,
        chatId: String,
        context: ClientToolCallContext,
    ): ClientToolAccessDecision {
        val accessContext = context.copy(chatId = chatId)
        if (tools.none { standing(it, accessContext) is Standing.NeedsPrompt }) return grantedNow(tools, accessContext)
        return awaitTurn(context.chatTabId, ClientToolAccessDecision.Cancelled) {
            val toAsk = tools.mapNotNull { (standing(it, accessContext) as? Standing.NeedsPrompt)?.tool }
            if (toAsk.isEmpty()) return@awaitTurn grantedNow(tools, accessContext)
            val request = ClientToolPermissionRequest.GrantAccess(
                context.chatTabId,
                context.browserMode,
                toAsk,
                toolsRemovable = browserToolsFeature.requestAccessRemoveTools().isEnabled(),
            )
            when (val answer = prompt.request(request)) {
                is ClientToolPermissionAnswer.AllowForChat -> {
                    chatGrants.grant(chatId, toAsk.map { it.name } - answer.removedTools)
                    grantedNow(tools, accessContext)
                }
                ClientToolPermissionAnswer.DontAllow -> ClientToolAccessDecision.Denied
                ClientToolPermissionAnswer.Dismissed -> ClientToolAccessDecision.Cancelled
            }
        }
    }

    override fun cancelPending(chatTabId: String) {
        val waiting = synchronized(waitingByChat) { waitingByChat.remove(chatTabId) }
        waiting?.forEach { it.complete(Unit) }
    }

    private suspend fun standing(
        tool: ClientTool,
        context: ClientToolCallContext,
    ): Standing = when (store.get(context.origin, tool.name)) {
        ClientToolPermission.ALWAYS_ALLOW -> Standing.Allowed
        ClientToolPermission.NEVER_ALLOW -> Standing.Denied
        null -> when (val mode = tool.permissionMode) {
            ClientToolPermissionMode.Auto -> Standing.Allowed
            is ClientToolPermissionMode.Ask -> when {
                skipsPromptForDevelopment() -> Standing.Allowed
                context.chatId != null && chatGrants.isGranted(context.chatId, tool.name) -> Standing.Allowed
                else -> Standing.NeedsPrompt(AskingTool(tool.name, mode))
            }
        }
    }

    private suspend fun grantedNow(
        tools: List<ClientTool>,
        context: ClientToolCallContext,
    ): ClientToolAccessDecision = ClientToolAccessDecision.Granted(tools.filter { standing(it, context) == Standing.Allowed }.map { it.name })

    private fun skipsPromptForDevelopment(): Boolean =
        appBuildConfig.isInternalBuild() && browserToolsFeature.devSkipPermissionPrompt().isEnabled()

    private suspend fun ask(
        tool: AskingTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision =
        when (prompt.request(ClientToolPermissionRequest.RunTool(context.chatTabId, context.browserMode, tool))) {
            is ClientToolPermissionAnswer.AllowForChat -> {
                // Without a chat ID there is no chat to remember the answer for, so it covers this call only.
                context.chatId?.let { chatGrants.grant(it, listOf(tool.name)) }
                ClientToolPermissionDecision.ALLOWED
            }
            ClientToolPermissionAnswer.DontAllow -> ClientToolPermissionDecision.DENIED
            ClientToolPermissionAnswer.Dismissed -> ClientToolPermissionDecision.CANCELLED
        }

    /**
     * Runs [decide] holding the prompt lock, so one prompt is open at a time.
     * Gives [cancelled] when the deadline passes or the chat goes away first.
     */
    private suspend fun <T> awaitTurn(
        chatTabId: String,
        cancelled: T,
        decide: suspend () -> T,
    ): T {
        val chatGone = startWaiting(chatTabId)
        try {
            return withTimeoutOrNull(DECISION_DEADLINE_MILLIS) {
                coroutineScope {
                    val decision = async { promptLock.withLock { decide() } }
                    select {
                        decision.onAwait { it }
                        chatGone.onAwait {
                            decision.cancel()
                            cancelled
                        }
                    }
                }
            } ?: cancelled
        } finally {
            stopWaiting(chatTabId, chatGone)
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
