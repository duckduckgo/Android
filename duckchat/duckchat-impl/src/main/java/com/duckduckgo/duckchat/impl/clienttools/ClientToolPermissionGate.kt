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
import com.duckduckgo.common.utils.CurrentTimeProvider
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
    ): ClientToolAccessOutcome

    /** Settles every call of the chat in [chatTabId], hosted in the contextual sheet when [contextual], still waiting for a decision as cancelled. */
    fun cancelPending(
        chatTabId: String,
        contextual: Boolean,
    )
}

sealed interface ClientToolPermissionDecision {
    data class Allowed(val path: ClientToolPermissionPath) : ClientToolPermissionDecision

    data class Denied(val path: ClientToolPermissionPath) : ClientToolPermissionDecision

    /** The prompt went unanswered: it timed out, or the call was cancelled. */
    data object Cancelled : ClientToolPermissionDecision
}

/** What let a call run or stopped it. */
enum class ClientToolPermissionPath(val pixelValue: String) {
    /** The tool doesn't ask. */
    AUTO("auto"),
    ALWAYS("always"),

    /** Allowed for this chat earlier. */
    CHAT("chat"),

    /** The user was asked for this call. */
    PROMPT("prompt"),
    NEVER("never"),
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

/** How a `requestAccess` prompt ended. */
enum class ClientToolAccessAnswer {
    /** Every tool was already decided, so nothing was asked. */
    NO_PROMPT,
    ALLOW,
    DONT_ALLOW,
    TIMEOUT,

    /** Closed unanswered because the call was cancelled or its chat went away. */
    WITHDRAWN,
}

/**
 * @param access each tool's level once the request is over, in the order they were requested.
 * @param asked the tools the prompt asked for, and [removed] the ones the user took out of it before allowing the rest.
 */
data class ClientToolAccessOutcome(
    val answer: ClientToolAccessAnswer,
    val access: Map<String, ClientToolAccessLevel>,
    val asked: Set<String> = emptySet(),
    val removed: Set<String> = emptySet(),
)

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealClientToolPermissionGate @Inject constructor(
    private val appBuildConfig: AppBuildConfig,
    private val browserToolsFeature: AiChatBrowserToolsFeature,
    private val store: ClientToolPermissionStore,
    private val chatGrants: ClientToolChatGrants,
    private val prompt: ClientToolPermissionPrompt,
    private val pixels: ClientToolPixels,
    private val timeProvider: CurrentTimeProvider,
) : ClientToolPermissionGate {

    private sealed interface Standing {
        data class Allowed(val level: ClientToolAccessLevel) : Standing

        data object Denied : Standing

        data class NeedsPrompt(val tool: AskingTool) : Standing

        data class NeedsPromptEveryTime(val copy: ClientToolPermissionMode.AskEveryTime) : Standing
    }

    // The contextual sheet's chat shares its tab ID with the page under it, so the host tells the two chats apart.
    private data class ChatHost(
        val chatTabId: String,
        val contextual: Boolean,
    )

    // One lock per chat, so a prompt waiting in a chat the user can't see doesn't hold up the others.
    private class ChatQueue {
        val promptLock = Mutex()
        val waiting = mutableSetOf<CompletableDeferred<Unit>>()
    }

    private val queues = mutableMapOf<ChatHost, ChatQueue>()

    private sealed interface Turn<out T> {
        data class Decided<T>(val value: T) : Turn<T>

        data object TimedOut : Turn<Nothing>

        data object Withdrawn : Turn<Nothing>
    }

    private class ShownPrompt(
        val numberInChat: Int?,
        val shownAt: Long,
    ) {
        var answer: ClientToolPromptAnswer? = null
    }

    override suspend fun decide(
        tool: ClientTool,
        context: ClientToolCallContext,
        promptDetail: String?,
    ): ClientToolPermissionDecision {
        when (val standing = standing(tool, context)) {
            is Standing.Allowed -> return standing.decision()
            Standing.Denied -> return DENIED_BY_NEVER
            is Standing.NeedsPrompt, is Standing.NeedsPromptEveryTime -> Unit
        }
        var shown: ShownPrompt? = null
        fun show() = ShownPrompt(promptNumber(context), timeProvider.elapsedRealtime()).also { shown = it }
        // Re-checked once the lock is ours: the prompt this call queued behind may have settled it.
        val turn = awaitTurn(context.chatHost()) {
            when (val now = standing(tool, context)) {
                is Standing.Allowed -> now.decision()
                Standing.Denied -> DENIED_BY_NEVER
                is Standing.NeedsPrompt -> ask(now.tool, context, show())
                is Standing.NeedsPromptEveryTime -> askOnce(now.copy, promptDetail.orEmpty(), context, show())
            }
        }
        shown?.let { prompt ->
            val answer = when (turn) {
                is Turn.Decided -> prompt.answer
                Turn.TimedOut -> ClientToolPromptAnswer.TIMEOUT
                Turn.Withdrawn -> ClientToolPromptAnswer.WITHDRAWN
            }
            answer?.let { pixels.promptClosed(tool.name, it, prompt.numberInChat, timeProvider.elapsedRealtime() - prompt.shownAt) }
        }
        return when (turn) {
            is Turn.Decided -> turn.value
            Turn.TimedOut, Turn.Withdrawn -> ClientToolPermissionDecision.Cancelled
        }
    }

    override suspend fun requestAccess(
        tools: List<ClientTool>,
        chatId: String,
        context: ClientToolCallContext,
    ): ClientToolAccessOutcome {
        val accessContext = context.copy(chatId = chatId)
        var asked = tools.filter { standing(it, accessContext) is Standing.NeedsPrompt }.map { it.name }
        if (asked.isEmpty()) return ClientToolAccessOutcome(ClientToolAccessAnswer.NO_PROMPT, accessNow(tools, accessContext))
        var removed = emptySet<String>()
        val turn = awaitTurn(context.chatHost()) {
            val toAsk = tools.mapNotNull { (standing(it, accessContext) as? Standing.NeedsPrompt)?.tool }
            asked = toAsk.map { it.name }
            if (toAsk.isEmpty()) return@awaitTurn ClientToolAccessAnswer.NO_PROMPT
            val request = ClientToolPermissionRequest.GrantAccess(
                context.chatTabId,
                context.browserMode,
                toAsk,
                toolsRemovable = browserToolsFeature.requestAccessRemoveTools().isEnabled(),
                contextual = context.contextual,
            )
            when (val answer = prompt.request(request)) {
                is ClientToolPermissionAnswer.AllowForChat -> {
                    removed = answer.removedTools intersect asked.toSet()
                    chatGrants.grant(chatId, asked - removed)
                    ClientToolAccessAnswer.ALLOW
                }
                ClientToolPermissionAnswer.AllowOnce, ClientToolPermissionAnswer.DontAllow -> ClientToolAccessAnswer.DONT_ALLOW
            }
        }
        val answer = when (turn) {
            is Turn.Decided -> turn.value
            Turn.TimedOut -> ClientToolAccessAnswer.TIMEOUT
            Turn.Withdrawn -> ClientToolAccessAnswer.WITHDRAWN
        }
        val shown = if (answer == ClientToolAccessAnswer.NO_PROMPT) emptySet() else asked.toSet()
        return ClientToolAccessOutcome(answer, accessNow(tools, accessContext), shown, removed)
    }

    override fun cancelPending(
        chatTabId: String,
        contextual: Boolean,
    ) {
        val waiting = synchronized(queues) {
            queues[ChatHost(chatTabId, contextual)]?.waiting?.let { set -> set.toList().also { set.clear() } }
        }
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
    ): Map<String, ClientToolAccessLevel> = tools.associate { tool ->
        val level = when (val standing = standing(tool, context)) {
            is Standing.Allowed -> standing.level
            Standing.Denied -> ClientToolAccessLevel.NEVER
            is Standing.NeedsPrompt, is Standing.NeedsPromptEveryTime -> ClientToolAccessLevel.NOT_GRANTED
        }
        tool.name to level
    }

    private fun Standing.Allowed.decision() = ClientToolPermissionDecision.Allowed(
        when (level) {
            ClientToolAccessLevel.ALWAYS -> ClientToolPermissionPath.ALWAYS
            ClientToolAccessLevel.CHAT -> ClientToolPermissionPath.CHAT
            ClientToolAccessLevel.DEFAULT, ClientToolAccessLevel.NEVER, ClientToolAccessLevel.NOT_GRANTED -> ClientToolPermissionPath.AUTO
        },
    )

    // Prompts are numbered only with batch access on, the one case where every call names its chat.
    private fun promptNumber(context: ClientToolCallContext): Int? =
        context.chatId?.takeIf { browserToolsFeature.requestAccess().isEnabled() }?.let(chatGrants::countPrompt)

    private fun skipsPromptForDevelopment(): Boolean =
        appBuildConfig.isInternalBuild() && browserToolsFeature.devSkipPermissionPrompt().isEnabled()

    private suspend fun ask(
        tool: AskingTool,
        context: ClientToolCallContext,
        shown: ShownPrompt,
    ): ClientToolPermissionDecision =
        when (prompt.request(ClientToolPermissionRequest.RunTool(context.chatTabId, context.browserMode, tool, context.contextual))) {
            is ClientToolPermissionAnswer.AllowForChat -> {
                shown.answer = ClientToolPromptAnswer.ALLOW_FOR_CHAT
                // Without a chat ID there is no chat to remember the answer for, so it covers this call only.
                context.chatId?.let { chatGrants.grant(it, listOf(tool.name)) }
                ClientToolPermissionDecision.Allowed(ClientToolPermissionPath.PROMPT)
            }
            ClientToolPermissionAnswer.AllowOnce, ClientToolPermissionAnswer.DontAllow -> {
                shown.answer = ClientToolPromptAnswer.DONT_ALLOW
                ClientToolPermissionDecision.Denied(ClientToolPermissionPath.PROMPT)
            }
        }

    private suspend fun askOnce(
        copy: ClientToolPermissionMode.AskEveryTime,
        detail: String,
        context: ClientToolCallContext,
        shown: ShownPrompt,
    ): ClientToolPermissionDecision =
        when (prompt.request(ClientToolPermissionRequest.RunToolOnce(context.chatTabId, context.browserMode, copy, detail, context.contextual))) {
            ClientToolPermissionAnswer.AllowOnce -> {
                shown.answer = ClientToolPromptAnswer.ALLOW_ONCE
                ClientToolPermissionDecision.Allowed(ClientToolPermissionPath.PROMPT)
            }
            is ClientToolPermissionAnswer.AllowForChat, ClientToolPermissionAnswer.DontAllow -> {
                shown.answer = ClientToolPromptAnswer.DONT_ALLOW
                ClientToolPermissionDecision.Denied(ClientToolPermissionPath.PROMPT)
            }
        }

    /** Runs [decide] holding the chat's prompt lock, so each chat has one prompt open at a time, unless the deadline passes or the chat goes away first. */
    private suspend fun <T> awaitTurn(
        host: ChatHost,
        decide: suspend () -> T,
    ): Turn<T> {
        val (queue, chatGone) = startWaiting(host)
        try {
            return withTimeoutOrNull(DECISION_DEADLINE_MILLIS) {
                coroutineScope {
                    val decision = async { queue.promptLock.withLock { decide() } }
                    select<Turn<T>> {
                        decision.onAwait { Turn.Decided(it) }
                        chatGone.onAwait {
                            decision.cancel()
                            Turn.Withdrawn
                        }
                    }
                }
            } ?: Turn.TimedOut
        } finally {
            stopWaiting(host, chatGone)
        }
    }

    private fun ClientToolCallContext.chatHost() = ChatHost(chatTabId, contextual)

    private fun startWaiting(host: ChatHost): Pair<ChatQueue, CompletableDeferred<Unit>> {
        val chatGone = CompletableDeferred<Unit>()
        val queue = synchronized(queues) { queues.getOrPut(host) { ChatQueue() }.also { it.waiting.add(chatGone) } }
        return queue to chatGone
    }

    private fun stopWaiting(
        host: ChatHost,
        chatGone: CompletableDeferred<Unit>,
    ) {
        synchronized(queues) {
            val queue = queues[host] ?: return
            queue.waiting.remove(chatGone)
            if (queue.waiting.isEmpty()) queues.remove(host)
        }
    }

    private companion object {
        // Measured from the call's arrival, so time spent queued behind another prompt counts.
        const val DECISION_DEADLINE_MILLIS = 120_000L
        val DENIED_BY_NEVER = ClientToolPermissionDecision.Denied(ClientToolPermissionPath.NEVER)
    }
}
