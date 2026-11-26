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
import com.duckduckgo.common.utils.CurrentTimeProvider
import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.duckchat.impl.clienttools.ParsedExecuteClientToolRequest.Malformed
import com.duckduckgo.duckchat.impl.clienttools.ParsedExecuteClientToolRequest.Valid
import dev.zacsweers.metro.ContributesBinding
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

interface ClientToolsExecutor {
    /** The `clientTools` value for `getAIChatNativeConfigValues`, or null when the key must be left out. */
    fun clientToolsConfig(
        pageUrl: String?,
        browserMode: BrowserMode,
    ): JSONObject?

    /** Runs an `executeClientTool` request and returns its reply params. */
    suspend fun execute(
        params: JSONObject?,
        pageUrl: String?,
        chatTabId: String,
        browserMode: BrowserMode,
        contextual: Boolean = false,
    ): JSONObject

    /**
     * Settles the calls of the chat in [chatTabId], hosted in the contextual sheet when [contextual], still waiting for a permission decision
     * as `cancelled`, so their tools never run.
     */
    fun cancelPendingCalls(
        chatTabId: String,
        contextual: Boolean,
    )
}

@ContributesBinding(AppScope::class)
class RealClientToolsExecutor @Inject constructor(
    private val tools: PluginPoint<ClientTool>,
    private val permissionGate: ClientToolPermissionGate,
    private val duckAiHostProvider: DuckAiHostProvider,
    private val browserToolsFeature: AiChatBrowserToolsFeature,
    private val pixels: ClientToolPixels,
    private val timeProvider: CurrentTimeProvider,
) : ClientToolsExecutor {

    /** How a tool call ended: [path] is null when it was refused before its permission was looked at, [durationMillis] when it never ran. */
    private data class FinishedCall(
        val result: ClientToolResult,
        val path: ClientToolPermissionPath? = null,
        val durationMillis: Long? = null,
    )

    override fun clientToolsConfig(
        pageUrl: String?,
        browserMode: BrowserMode,
    ): JSONObject? {
        if (caller(pageUrl, browserMode) == null) return null
        val enabledTools = tools.getPlugins().filter { it.isEnabled() }
        if (enabledTools.isEmpty()) return null

        val capabilities = JSONArray()
        enabledTools.forEach { tool ->
            capabilities.put(JSONObject().put(NAME, tool.name).put(VERSION, tool.version))
        }
        if (isRequestAccessEnabled()) {
            capabilities.put(JSONObject().put(NAME, RequestAccess.NAME).put(VERSION, RequestAccess.VERSION))
        }
        pixels.toolsOffered()
        return JSONObject()
            .put(PROTOCOL_VERSION, CLIENT_TOOLS_PROTOCOL_VERSION)
            .put(CAPABILITIES, capabilities)
    }

    override suspend fun execute(
        params: JSONObject?,
        pageUrl: String?,
        chatTabId: String,
        browserMode: BrowserMode,
        contextual: Boolean,
    ): JSONObject {
        val parsed = ParsedExecuteClientToolRequest.parse(params)
        val caller = caller(pageUrl, browserMode) ?: return failureReply(parsed.callId, ClientToolErrorCode.UNAVAILABLE)
        val request = when (parsed) {
            is Malformed -> {
                pixels.protocolError(ClientToolProtocolError.INVALID_REQUEST, capabilityName(params))
                return failureReply(parsed.callId, ClientToolErrorCode.INVALID_ARGUMENTS)
            }
            is Valid -> parsed.request
        }
        val context = ClientToolCallContext(chatTabId, browserMode, request.chatId ?: caller.pageChatId, contextual)
        val result = if (request.name == RequestAccess.NAME) {
            requestAccess(request, context)
        } else {
            val call = run(request, context)
            pixels.callFinished(request.name, call.result, call.path, call.durationMillis)
            call.result
        }
        logcat {
            val chatIdSource = when {
                request.chatId != null -> "params"
                caller.pageChatId != null -> "page"
                else -> "none"
            }
            val outcome = if (result is ClientToolResult.Failure) result.code.wireName else "ok"
            "Client tool ${request.name} replied $outcome, chat ID from $chatIdSource"
        }
        return when (result) {
            is ClientToolResult.Success -> JSONObject().put(CALL_ID, request.callId).put(OK, true).put(VALUE, result.value)
            is ClientToolResult.Failure -> failureReply(request.callId, result.code)
        }
    }

    private suspend fun requestAccess(
        request: ExecuteClientToolRequest,
        context: ClientToolCallContext,
    ): ClientToolResult {
        if (!isRequestAccessEnabled() || request.version != RequestAccess.VERSION.toLong()) {
            return rejectAccess(ClientToolAccessRejection.UNAVAILABLE, ClientToolErrorCode.UNAVAILABLE)
        }
        val invalid = { rejectAccess(ClientToolAccessRejection.INVALID, ClientToolErrorCode.INVALID_ARGUMENTS) }
        val names = RequestAccess.parseCapabilities(request.input) ?: return invalid()
        val chatId = context.chatId ?: return invalid()
        val toolsByName = tools.getPlugins().associateBy { it.name }
        if (names.any { toolsByName[it]?.permissionMode is ClientToolPermissionMode.AskEveryTime }) return invalid()
        val servable = names.mapNotNull { name -> toolsByName[name]?.takeIf { it.isEnabled() } }
        val outcome = permissionGate.requestAccess(servable, chatId, context)
        pixels.accessRequested(names, outcome)
        return when (outcome.answer) {
            ClientToolAccessAnswer.NO_PROMPT, ClientToolAccessAnswer.ALLOW -> {
                val access = JSONObject()
                names.forEach { access.put(it, (outcome.access[it] ?: ClientToolAccessLevel.NOT_GRANTED).wireName) }
                ClientToolResult.Success(JSONObject().put(ACCESS, access))
            }
            ClientToolAccessAnswer.DONT_ALLOW -> ClientToolResult.Failure(ClientToolErrorCode.DENIED)
            ClientToolAccessAnswer.TIMEOUT, ClientToolAccessAnswer.WITHDRAWN -> ClientToolResult.Failure(ClientToolErrorCode.CANCELLED)
        }
    }

    private fun rejectAccess(
        rejection: ClientToolAccessRejection,
        code: ClientToolErrorCode,
    ): ClientToolResult {
        pixels.accessRequestRejected(rejection)
        return ClientToolResult.Failure(code)
    }

    private fun isRequestAccessEnabled(): Boolean =
        browserToolsFeature.self().isEnabled() &&
            browserToolsFeature.requestAccess().isEnabled() &&
            tools.getPlugins().any { it.isEnabled() }

    private suspend fun run(
        request: ExecuteClientToolRequest,
        context: ClientToolCallContext,
    ): FinishedCall {
        val tool = tools.getPlugins().firstOrNull { it.name == request.name }
        // A known tool turned off by remote config is not a protocol error.
        if (tool == null || tool.version.toLong() != request.version) {
            pixels.protocolError(ClientToolProtocolError.UNKNOWN_CAPABILITY, request.name)
            return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE))
        }
        if (!tool.isEnabled()) return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE))
        if (!tool.isValidInput(request.input)) return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS))
        val promptDetail = if (tool.permissionMode is ClientToolPermissionMode.AskEveryTime) {
            when (val detail = failingSafely(tool) { tool.promptDetail(request.input, context) }) {
                is ClientToolPromptDetail.Shown -> detail.text
                is ClientToolPromptDetail.Failure -> return FinishedCall(ClientToolResult.Failure(detail.code))
                null -> return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED))
            }
        } else {
            null
        }
        val path = when (val decision = permissionGate.decide(tool, context, promptDetail)) {
            is ClientToolPermissionDecision.Denied -> return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.DENIED), decision.path)
            ClientToolPermissionDecision.Cancelled ->
                return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.CANCELLED), ClientToolPermissionPath.PROMPT)
            is ClientToolPermissionDecision.Allowed -> decision.path
        }
        // Remote config can turn the tool off while its prompt is open.
        if (!tool.isEnabled()) return FinishedCall(ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE), path)
        val startedAt = timeProvider.elapsedRealtime()
        val result = failingSafely(tool) { tool.execute(request.input, context.copy(allowedDetail = promptDetail)) }
            ?: ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED)
        return FinishedCall(result, path, timeProvider.elapsedRealtime() - startedAt)
    }

    /** The capability name of a call that could not be read, for the protocol error pixel to map to a slug. */
    private fun capabilityName(params: JSONObject?): String? = (params?.opt(CAPABILITY) as? JSONObject)?.opt(NAME) as? String

    /** Runs [block], or gives null when it throws, so a failing tool settles its call as `execution_failed`. */
    private suspend fun <T> failingSafely(
        tool: ClientTool,
        block: suspend () -> T,
    ): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logcat(WARN) { "Client tool ${tool.name} failed: ${e.asLog()}" }
        null
    }

    override fun cancelPendingCalls(
        chatTabId: String,
        contextual: Boolean,
    ) {
        permissionGate.cancelPending(chatTabId, contextual)
    }

    private data class Caller(val pageChatId: String?)

    /** The calling Duck.ai page, or null when the caller may not use client tools. */
    private fun caller(
        pageUrl: String?,
        browserMode: BrowserMode,
    ): Caller? {
        if (browserMode == BrowserMode.FIRE) return null
        val url = pageUrl?.toHttpUrlOrNull() ?: return null
        if (url.host != duckAiHostProvider.getHost()) return null
        return Caller(pageChatId = url.queryParameter(CHAT_ID_QUERY)?.takeIf { it.isNotBlank() })
    }

    private fun failureReply(
        callId: String?,
        code: ClientToolErrorCode,
    ): JSONObject = JSONObject().apply {
        callId?.let { put(CALL_ID, it) }
        put(OK, false)
        put(ERROR, JSONObject().put(CODE, code.wireName))
    }

    private companion object {
        const val CLIENT_TOOLS_PROTOCOL_VERSION = 1
        const val PROTOCOL_VERSION = "protocolVersion"
        const val CAPABILITIES = "capabilities"
        const val CAPABILITY = "capability"
        const val NAME = "name"
        const val VERSION = "version"
        const val CALL_ID = "callId"
        const val OK = "ok"
        const val VALUE = "value"
        const val ERROR = "error"
        const val CODE = "code"
        const val ACCESS = "access"
        const val CHAT_ID_QUERY = "chatID"
    }
}
