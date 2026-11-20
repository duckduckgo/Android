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
import com.duckduckgo.codegen.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope
import org.json.JSONObject

@ContributesPluginPoint(AppScope::class)
interface ClientTool {
    val name: String
    val version: Int
    val permissionMode: ClientToolPermissionMode

    fun isEnabled(): Boolean

    fun isValidInput(input: JSONObject): Boolean

    suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult
}

enum class ClientToolPermissionMode {
    ASK,
    AUTO,
}

/**
 * @param chatTabId the tab the chat belongs to: the Duck.ai tab itself, or the page tab hosting the contextual sheet.
 */
data class ClientToolCallContext(
    val chatTabId: String,
    val browserMode: BrowserMode,
)

sealed interface ClientToolResult {
    data class Success(val value: JSONObject) : ClientToolResult

    data class Failure(val code: ClientToolErrorCode) : ClientToolResult
}

enum class ClientToolErrorCode(val wireName: String) {
    DENIED("denied"),
    CANCELLED("cancelled"),
    UNAVAILABLE("unavailable"),
    NOT_FOUND("not_found"),
    INVALID_ARGUMENTS("invalid_arguments"),
    EXECUTION_FAILED("execution_failed"),
}
