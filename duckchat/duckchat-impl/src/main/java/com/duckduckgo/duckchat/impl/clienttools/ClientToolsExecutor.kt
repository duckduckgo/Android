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
    ): JSONObject
}

@ContributesBinding(AppScope::class)
class RealClientToolsExecutor @Inject constructor(
    private val tools: PluginPoint<ClientTool>,
    private val permissionGate: ClientToolPermissionGate,
    private val duckAiHostProvider: DuckAiHostProvider,
) : ClientToolsExecutor {

    override fun clientToolsConfig(
        pageUrl: String?,
        browserMode: BrowserMode,
    ): JSONObject? {
        if (!isCallerAllowed(pageUrl, browserMode)) return null
        val enabledTools = tools.getPlugins().filter { it.isEnabled() }
        if (enabledTools.isEmpty()) return null

        val capabilities = JSONArray()
        enabledTools.forEach { tool ->
            capabilities.put(JSONObject().put(NAME, tool.name).put(VERSION, tool.version))
        }
        return JSONObject()
            .put(PROTOCOL_VERSION, CLIENT_TOOLS_PROTOCOL_VERSION)
            .put(CAPABILITIES, capabilities)
    }

    override suspend fun execute(
        params: JSONObject?,
        pageUrl: String?,
        chatTabId: String,
        browserMode: BrowserMode,
    ): JSONObject {
        val parsed = ParsedExecuteClientToolRequest.parse(params)
        if (!isCallerAllowed(pageUrl, browserMode)) {
            return failureReply(parsed.callId, ClientToolErrorCode.UNAVAILABLE)
        }
        val request = when (parsed) {
            is Malformed -> return failureReply(parsed.callId, ClientToolErrorCode.INVALID_ARGUMENTS)
            is Valid -> parsed.request
        }
        return when (val result = run(request, ClientToolCallContext(chatTabId, browserMode))) {
            is ClientToolResult.Success -> JSONObject().put(CALL_ID, request.callId).put(OK, true).put(VALUE, result.value)
            is ClientToolResult.Failure -> failureReply(request.callId, result.code)
        }
    }

    private suspend fun run(
        request: ExecuteClientToolRequest,
        context: ClientToolCallContext,
    ): ClientToolResult {
        val tool = tools.getPlugins().firstOrNull { it.name == request.name }
        if (tool == null || tool.version.toLong() != request.version || !tool.isEnabled()) {
            return ClientToolResult.Failure(ClientToolErrorCode.UNAVAILABLE)
        }
        if (!tool.isValidInput(request.input)) {
            return ClientToolResult.Failure(ClientToolErrorCode.INVALID_ARGUMENTS)
        }
        if (permissionGate.decide(tool, context) == ClientToolPermissionDecision.DENIED) {
            return ClientToolResult.Failure(ClientToolErrorCode.DENIED)
        }
        return try {
            tool.execute(request.input, context)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logcat(WARN) { "Client tool ${tool.name} failed: ${e.asLog()}" }
            ClientToolResult.Failure(ClientToolErrorCode.EXECUTION_FAILED)
        }
    }

    private fun isCallerAllowed(
        pageUrl: String?,
        browserMode: BrowserMode,
    ): Boolean = browserMode != BrowserMode.FIRE && pageUrl?.toHttpUrlOrNull()?.host == duckAiHostProvider.getHost()

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
        const val NAME = "name"
        const val VERSION = "version"
        const val CALL_ID = "callId"
        const val OK = "ok"
        const val VALUE = "value"
        const val ERROR = "error"
        const val CODE = "code"
    }
}
