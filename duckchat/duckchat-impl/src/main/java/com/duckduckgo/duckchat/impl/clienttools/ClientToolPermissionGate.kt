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
    /** @param promptDetail what an [ClientToolPermissionMode.AskEveryTime] prompt shows for this call. */
    suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
        promptDetail: String? = null,
    ): ClientToolPermissionDecision

    /**
     * Asks once for every tool in [tools] the user has not allowed yet, and grants the ones they allow for [chatId].
     * Never runs a tool, and never asks for an [ClientToolPermissionMode.AskEveryTime] tool.
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

/** How a `requestAccess` reply reports each requested tool. */
enum class ClientToolAccessLevel(val wireName: String) {
    /** Allowed without asking. */
    DEFAULT("default"),

    /** Always Allow in settings. */
    ALWAYS("always"),

    /** Allowed for this chat, in this prompt or earlier. */
    CHAT("chat"),

    /** Never Allow in settings, so it was left out of the prompt. */
    NEVER("never"),

    /** Removed from the prompt, or can't be served now. Nothing is remembered. */
    NOT_GRANTED("notGranted"),
}

sealed interface ClientToolAccessDecision {
    /** Each requested tool's level, in the order they were requested. */
    data class Granted(val access: Map<String, ClientToolAccessLevel>) : ClientToolAccessDecision

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
        data class Allowed(val level: ClientToolAccessLevel) : Standing

        data object Denied : Standing

        data class NeedsPrompt(val tool: AskingTool) : Standing

        data class NeedsPromptEveryTime(val copy: ClientToolPermissionMode.AskEveryTime) : Standing
    }

    private val promptLock = Mutex()
    private val waitingByChat = mutableMapOf<String, MutableSet<CompletableDeferred<Unit>>>()

    override suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
        promptDetail: String?,
    ): ClientToolPermissionDecision {
        when (standing(tool, context)) {
            is Standing.Allowed -> return ClientToolPermissionDecision.ALLOWED
            Standing.Denied -> return ClientToolPermissionDecision.DENIED
            is Standing.NeedsPrompt, is Standing.NeedsPromptEveryTime -> Unit
        }
        // Re-checked once the lock is ours: the prompt this call queued behind may have settled it.
        return awaitTurn(context.chatTabId, ClientToolPermissionDecision.CANCELLED) {
            when (val now = standing(tool, context)) {
                is Standing.Allowed -> ClientToolPermissionDecision.ALLOWED
                Standing.Denied -> ClientToolPermissionDecision.DENIED
                is Standing.NeedsPrompt -> ask(now.tool, context)
                is Standing.NeedsPromptEveryTime -> askOnce(now.copy, promptDetail.orEmpty(), context)
            }
        }
    }

    override suspend fun requestAccess(
        tools: List<ClientTool>,
        chatId: String,
        context: ClientToolCallContext,
    ): ClientToolAccessDecision {
        val accessContext = context.copy(chatId = chatId)
        if (tools.none { standing(it, accessContext) is Standing.NeedsPrompt }) return accessNow(tools, accessContext)
        return awaitTurn(context.chatTabId, ClientToolAccessDecision.Cancelled) {
            val toAsk = tools.mapNotNull { (standing(it, accessContext) as? Standing.NeedsPrompt)?.tool }
            if (toAsk.isEmpty()) return@awaitTurn accessNow(tools, accessContext)
            val request = ClientToolPermissionRequest.GrantAccess(
                context.chatTabId,
                context.browserMode,
                toAsk,
                toolsRemovable = browserToolsFeature.requestAccessRemoveTools().isEnabled(),
                contextual = context.contextual,
            )
            when (val answer = prompt.request(request)) {
                is ClientToolPermissionAnswer.AllowForChat -> {
                    chatGrants.grant(chatId, toAsk.map { it.name } - answer.removedTools)
                    accessNow(tools, accessContext)
                }
                ClientToolPermissionAnswer.AllowOnce, ClientToolPermissionAnswer.DontAllow -> ClientToolAccessDecision.Denied
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
    ): Standing {
        val stored = store.get(tool.name)
        return when (val mode = tool.permissionMode) {
            // Always Allow never covers these: the user sees what each call acts on before it runs.
            is ClientToolPermissionMode.AskEveryTime ->
                if (stored == ClientToolPermission.NEVER_ALLOW) Standing.Denied else Standing.NeedsPromptEveryTime(mode)
            ClientToolPermissionMode.Auto -> when (stored) {
                ClientToolPermission.NEVER_ALLOW -> Standing.Denied
                ClientToolPermission.ALWAYS_ALLOW -> Standing.Allowed(ClientToolAccessLevel.ALWAYS)
                null -> Standing.Allowed(ClientToolAccessLevel.DEFAULT)
            }
            is ClientToolPermissionMode.Ask -> when {
                stored == ClientToolPermission.ALWAYS_ALLOW -> Standing.Allowed(ClientToolAccessLevel.ALWAYS)
                stored == ClientToolPermission.NEVER_ALLOW -> Standing.Denied
                skipsPromptForDevelopment() -> Standing.Allowed(ClientToolAccessLevel.DEFAULT)
                context.chatId != null && chatGrants.isGranted(context.chatId, tool.name) -> Standing.Allowed(ClientToolAccessLevel.CHAT)
                else -> Standing.NeedsPrompt(AskingTool(tool.name, mode))
            }
        }
    }

    private suspend fun accessNow(
        tools: List<ClientTool>,
        context: ClientToolCallContext,
    ): ClientToolAccessDecision = ClientToolAccessDecision.Granted(
        tools.associate { tool ->
            val level = when (val standing = standing(tool, context)) {
                is Standing.Allowed -> standing.level
                Standing.Denied -> ClientToolAccessLevel.NEVER
                is Standing.NeedsPrompt, is Standing.NeedsPromptEveryTime -> ClientToolAccessLevel.NOT_GRANTED
            }
            tool.name to level
        },
    )

    private fun skipsPromptForDevelopment(): Boolean =
        appBuildConfig.isInternalBuild() && browserToolsFeature.devSkipPermissionPrompt().isEnabled()

    private suspend fun ask(
        tool: AskingTool,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision =
        when (prompt.request(ClientToolPermissionRequest.RunTool(context.chatTabId, context.browserMode, tool, context.contextual))) {
            is ClientToolPermissionAnswer.AllowForChat -> {
                // Without a chat ID there is no chat to remember the answer for, so it covers this call only.
                context.chatId?.let { chatGrants.grant(it, listOf(tool.name)) }
                ClientToolPermissionDecision.ALLOWED
            }
            ClientToolPermissionAnswer.AllowOnce, ClientToolPermissionAnswer.DontAllow -> ClientToolPermissionDecision.DENIED
        }

    private suspend fun askOnce(
        copy: ClientToolPermissionMode.AskEveryTime,
        detail: String,
        context: ClientToolCallContext,
    ): ClientToolPermissionDecision =
        when (prompt.request(ClientToolPermissionRequest.RunToolOnce(context.chatTabId, context.browserMode, copy, detail, context.contextual))) {
            ClientToolPermissionAnswer.AllowOnce -> ClientToolPermissionDecision.ALLOWED
            is ClientToolPermissionAnswer.AllowForChat, ClientToolPermissionAnswer.DontAllow -> ClientToolPermissionDecision.DENIED
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
