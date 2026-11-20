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

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.duckduckgo.browsermode.api.BrowserMode
import com.duckduckgo.codegen.annotations.ContributesPluginPoint
import com.duckduckgo.di.scopes.AppScope
import org.json.JSONObject

@ContributesPluginPoint(AppScope::class)
interface ClientTool {
    val name: String
    val version: Int
    val permissionMode: ClientToolPermissionMode

    /** How the Browser Access settings screen lists this tool. */
    val settingsListing: ClientToolSettingsListing

    fun isEnabled(): Boolean

    fun isValidInput(input: JSONObject): Boolean

    suspend fun execute(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolResult

    /**
     * What an [ClientToolPermissionMode.AskEveryTime] prompt shows for this call, such as the URL it acts on.
     * Called before the prompt, so a target that cannot be resolved fails the call without asking.
     */
    suspend fun promptDetail(
        input: JSONObject,
        context: ClientToolCallContext,
    ): ClientToolPromptDetail? = null
}

data class ClientToolSettingsListing(
    val section: ClientToolSettingsSection,
    @StringRes val name: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
)

enum class ClientToolSettingsSection {
    /** Tools that let Duck.ai read browser data. */
    SEE,

    /** Tools that let Duck.ai change something in the browser. */
    DO,
}

sealed interface ClientToolPromptDetail {
    data class Shown(val text: String) : ClientToolPromptDetail

    data class Failure(val code: ClientToolErrorCode) : ClientToolPromptDetail
}

sealed interface ClientToolPermissionMode {
    data object Auto : ClientToolPermissionMode

    /**
     * Asks the user before running, with the tool's own prompt copy.
     * @param name lists the tool, with [promptMessage] under it, in a prompt that asks for several tools at once.
     */
    data class Ask(
        @StringRes val promptTitle: Int,
        @StringRes val promptMessage: Int,
        @StringRes val name: Int,
    ) : ClientToolPermissionMode

    /**
     * Asks before every call, and the answer covers that call only: never the chat grant, `requestAccess` or Always Allow.
     * @param promptMessage takes the call's [ClientToolPromptDetail.Shown] text as its only argument.
     */
    data class AskEveryTime(
        @StringRes val promptTitle: Int,
        @StringRes val promptMessage: Int,
    ) : ClientToolPermissionMode
}

/**
 * @param chatTabId the tab the chat belongs to: the Duck.ai tab itself, or the page tab hosting the contextual sheet.
 * @param chatId the Duck.ai conversation, or null when the page has not started one.
 * @param contextual whether the chat runs in the contextual sheet rather than a Duck.ai tab.
 */
data class ClientToolCallContext(
    val chatTabId: String,
    val browserMode: BrowserMode,
    val chatId: String?,
    val contextual: Boolean = false,
    /** The [ClientToolPromptDetail.Shown] text the user allowed, for an [ClientToolPermissionMode.AskEveryTime] tool. */
    val allowedDetail: String? = null,
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
