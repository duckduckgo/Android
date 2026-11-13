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
import org.json.JSONObject

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
