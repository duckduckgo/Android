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

import com.duckduckgo.common.utils.plugins.PluginPoint
import com.duckduckgo.duckchat.api.DuckAiHostProvider
import com.duckduckgo.site.permissions.api.ClientToolPermission
import com.duckduckgo.site.permissions.api.ClientToolPermissionStore
import kotlinx.coroutines.CompletableDeferred
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

class FakeClientTool(
    override val name: String = "fakeTool",
    override val version: Int = 1,
    override val permissionMode: ClientToolPermissionMode = ClientToolPermissionMode.AUTO,
    var enabled: Boolean = true,
    var validInput: (JSONObject) -> Boolean = { true },
    var result: suspend (JSONObject) -> ClientToolResult = { ClientToolResult.Success(JSONObject().put("echo", it)) },
) : ClientTool {
    val calls = mutableListOf<Pair<JSONObject, ClientToolCallContext>>()

    override fun isEnabled(): Boolean = enabled

    override fun isValidInput(input: JSONObject): Boolean = validInput(input)

    override suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult {
        calls += input to context
        return result(input)
    }
}

class FakeClientToolPluginPoint(vararg tools: ClientTool) : PluginPoint<ClientTool> {
    private val tools = tools.toList()

    override fun getPlugins(): Collection<ClientTool> = tools
}

class FakeDuckAiHostProvider(private val host: String = "duck.ai") : DuckAiHostProvider {
    override fun getHost(): String = host
}

class FakeClientToolPermissionStore : ClientToolPermissionStore {
    val choices = mutableMapOf<Pair<String, String>, ClientToolPermission>()

    override suspend fun get(
        origin: String,
        toolName: String,
    ): ClientToolPermission? = choices[origin to toolName]

    override suspend fun set(
        origin: String,
        toolName: String,
        permission: ClientToolPermission,
    ) {
        choices[origin to toolName] = permission
    }

    override suspend fun clear(toolName: String) {
        choices.keys.removeAll { it.second == toolName }
    }

    override suspend fun clearAll() {
        choices.clear()
    }
}

/** Holds each prompt open until the test answers it, recording how many were open at once. */
class FakeClientToolPermissionPrompt : ClientToolPermissionPrompt {
    val prompts = mutableListOf<OpenPrompt>()
    var maxOpenAtOnce = 0
        private set
    private var open = 0

    val current: OpenPrompt get() = prompts.last()

    override suspend fun request(request: ClientToolPermissionRequest): ClientToolPermissionAnswer {
        val prompt = OpenPrompt(request)
        prompts += prompt
        open++
        maxOpenAtOnce = maxOf(maxOpenAtOnce, open)
        try {
            return prompt.answer.await()
        } catch (e: CancellationException) {
            prompt.withdrawn = true
            throw e
        } finally {
            open--
        }
    }

    class OpenPrompt(val request: ClientToolPermissionRequest) {
        val answer = CompletableDeferred<ClientToolPermissionAnswer>()
        var withdrawn = false

        fun answer(answer: ClientToolPermissionAnswer) {
            this.answer.complete(answer)
        }
    }
}
