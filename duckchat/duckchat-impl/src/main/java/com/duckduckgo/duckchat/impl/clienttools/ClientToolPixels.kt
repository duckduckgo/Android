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

import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin
import com.duckduckgo.common.utils.plugins.pixel.PixelParamRemovalPlugin.PixelParameter
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.clienttools.settings.BrowserAccessChoice
import dev.zacsweers.metro.ContributesIntoSet
import javax.inject.Inject

/**
 * The browser tools Android ships, with the slug pixels send for each. Pixels only ever send a slug from this table, never the name Duck.ai sent.
 * @param resultsKey the array a successful call returns, for the tools whose result count is reported.
 */
enum class ClientToolPixelSlug(
    val toolName: String,
    val pixelValue: String,
    val resultsKey: String? = null,
) {
    LIST_OPEN_TABS("listOpenTabs", "list_open_tabs", "tabs"),
    READ_TAB_CONTENT("readTabContent", "read_tab_content"),
    SEARCH_HISTORY("searchHistory", "search_history", "results"),
    FIND_IN_PAGE("findInPage", "find_in_page"),
    SWITCH_TO_TAB("switchToTab", "switch_to_tab"),
    OPEN_TAB("openTab", "open_tab"),
    BOOKMARK_TAB("bookmarkTab", "bookmark_tab"),
    CLOSE_TABS("closeTabs", "close_tabs"),
    SEARCH_DOWNLOADS("searchDownloads", "search_downloads", "results"),
    SEARCH_BOOKMARKS("searchBookmarks", "search_bookmarks", "results"),
    ;

    companion object {
        fun of(toolName: String?): ClientToolPixelSlug? = entries.firstOrNull { it.toolName == toolName }
    }
}

/** How a single-tool permission prompt closed. */
enum class ClientToolPromptAnswer(val pixelValue: String) {
    ALLOW_ONCE("allow_once"),
    ALLOW_FOR_CHAT("allow_for_chat"),

    /** Refuses this call and remembers nothing. */
    DONT_ALLOW("dont_allow"),
    TIMEOUT("timeout"),

    /** Closed unanswered because the call was cancelled or its chat went away. */
    WITHDRAWN("withdrawn"),
}

enum class ClientToolProtocolError(val pixelValue: String) {
    INVALID_REQUEST("invalid_request"),
    UNKNOWN_CAPABILITY("unknown_capability"),
}

/** A `requestAccess` call refused before anything was asked. */
enum class ClientToolAccessRejection(val pixelValue: String) {
    INVALID("invalid"),
    UNAVAILABLE("unavailable"),
}

/** Browser tools telemetry. Every value is a slug or a bucket; no URL, title, query, tab ID or chat ID is ever sent. */
class ClientToolPixels @Inject constructor(
    private val pixel: Pixel,
) {

    fun toolsOffered() {
        pixel.fire(ENABLED_DAILY, type = Pixel.PixelType.Daily())
    }

    /** @param path null when the call was refused before its permission was looked at. */
    fun callFinished(
        toolName: String,
        result: ClientToolResult,
        path: ClientToolPermissionPath?,
        durationMillis: Long?,
    ) {
        val slug = ClientToolPixelSlug.of(toolName) ?: return
        val parameters = mutableMapOf(
            TOOL to slug.pixelValue,
            "result" to when (result) {
                is ClientToolResult.Success -> "success"
                is ClientToolResult.Failure -> result.code.wireName
            },
            "permission_path" to (path?.pixelValue ?: "none"),
        )
        durationMillis?.let { parameters["duration"] = durationBucket(it) }
        if (result is ClientToolResult.Success) {
            slug.resultsKey?.let { key -> result.value.optJSONArray(key)?.length()?.let { parameters["result_count"] = resultCountBucket(it) } }
        }
        pixel.fire(CALL, parameters)
        if (result is ClientToolResult.Success) {
            pixel.fire(USED_DAILY, type = Pixel.PixelType.Daily())
            pixel.fire("m_aichat_tools_used_${slug.pixelValue}_daily", type = Pixel.PixelType.Daily())
        }
    }

    /** @param numberInChat which single-tool prompt this was in its chat, or null when the call carries no chat or batch access is off. */
    fun promptClosed(
        toolName: String,
        answer: ClientToolPromptAnswer,
        numberInChat: Int?,
        openMillis: Long,
    ) {
        val slug = ClientToolPixelSlug.of(toolName) ?: return
        val parameters = mutableMapOf(
            TOOL to slug.pixelValue,
            "answer" to answer.pixelValue,
            "time_to_answer" to timeToAnswerBucket(openMillis),
        )
        numberInChat?.let { parameters["prompt_number_in_chat"] = if (it >= 4) "4_plus" else it.toString() }
        pixel.fire(PROMPT_ANSWERED, parameters)
    }

    /** One pixel for the request, then one per requested tool this app knows. */
    fun accessRequested(
        requested: List<String>,
        outcome: ClientToolAccessOutcome,
    ) {
        val decision = when (outcome.answer) {
            ClientToolAccessAnswer.NO_PROMPT -> "no_prompt"
            ClientToolAccessAnswer.ALLOW -> "allow"
            ClientToolAccessAnswer.DONT_ALLOW -> "dont_allow"
            ClientToolAccessAnswer.TIMEOUT -> "timeout"
            ClientToolAccessAnswer.WITHDRAWN -> "withdrawn"
        }
        pixel.fire(
            ACCESS_REQUESTED,
            mapOf(
                "decision" to decision,
                "requested" to requested.size.toString(),
                "asked" to outcome.asked.size.toString(),
                "removed" to outcome.removed.size.toString(),
            ),
        )
        requested.forEach { name ->
            val slug = ClientToolPixelSlug.of(name) ?: return@forEach
            pixel.fire(ACCESS_RESULT, mapOf(TOOL to slug.pixelValue, "access" to accessResult(name, outcome)))
        }
    }

    fun accessRequestRejected(rejection: ClientToolAccessRejection) {
        pixel.fire(ACCESS_REQUESTED, mapOf("decision" to rejection.pixelValue))
    }

    fun protocolError(
        error: ClientToolProtocolError,
        toolName: String?,
    ) {
        pixel.fire(PROTOCOL_ERROR, mapOf("reason" to error.pixelValue, TOOL to (ClientToolPixelSlug.of(toolName)?.pixelValue ?: "unknown")))
    }

    fun permissionChanged(
        toolName: String,
        from: BrowserAccessChoice,
        to: BrowserAccessChoice,
    ) {
        val slug = ClientToolPixelSlug.of(toolName) ?: return
        pixel.fire(
            PERMISSION_CHANGED,
            mapOf(TOOL to slug.pixelValue, "from" to from.pixelValue, "to" to to.pixelValue, "source" to "settings"),
        )
    }

    fun permissionsReset() {
        pixel.fire(PERMISSIONS_RESET)
    }

    private fun accessResult(
        toolName: String,
        outcome: ClientToolAccessOutcome,
    ): String = when {
        toolName in outcome.removed -> "removed"
        toolName in outcome.asked -> if (outcome.answer == ClientToolAccessAnswer.ALLOW) "granted" else "denied"
        else -> when (outcome.access[toolName]) {
            ClientToolAccessLevel.DEFAULT, ClientToolAccessLevel.ALWAYS, ClientToolAccessLevel.CHAT -> "already_allowed"
            ClientToolAccessLevel.NEVER -> "never"
            ClientToolAccessLevel.NOT_GRANTED, null -> "unavailable"
        }
    }

    private val BrowserAccessChoice.pixelValue: String
        get() = when (this) {
            BrowserAccessChoice.ALWAYS_ALLOW -> "allow"
            BrowserAccessChoice.ALWAYS_ASK -> "ask"
            BrowserAccessChoice.NEVER_ALLOW -> "deny"
        }

    private fun durationBucket(millis: Long): String = when {
        millis < 100 -> "lt_100ms"
        millis < 500 -> "lt_500ms"
        millis < 2_000 -> "lt_2s"
        millis < 10_000 -> "lt_10s"
        else -> "gte_10s"
    }

    private fun timeToAnswerBucket(millis: Long): String = when {
        millis < 2_000 -> "lt_2s"
        millis < 10_000 -> "lt_10s"
        millis < 60_000 -> "lt_60s"
        else -> "gte_60s"
    }

    private fun resultCountBucket(count: Int): String = when {
        count <= 0 -> "0"
        count <= 5 -> "1-5"
        count <= 20 -> "6-20"
        else -> "21_plus"
    }

    companion object {
        const val PREFIX = "m_aichat_tools_"
        const val ENABLED_DAILY = "m_aichat_tools_enabled_daily"
        const val USED_DAILY = "m_aichat_tools_used_daily"
        const val CALL = "m_aichat_tools_call"
        const val PROMPT_ANSWERED = "m_aichat_tools_prompt_answered"
        const val ACCESS_REQUESTED = "m_aichat_tools_access_requested"
        const val ACCESS_RESULT = "m_aichat_tools_access_result"
        const val PROTOCOL_ERROR = "m_aichat_tools_protocol_error"
        const val PERMISSION_CHANGED = "m_aichat_tools_permission_changed"
        const val PERMISSIONS_RESET = "m_aichat_tools_permissions_reset"
        private const val TOOL = "tool"
    }
}

/** Browser tools pixels go out without ATB, as on the other platforms. */
@ContributesIntoSet(AppScope::class)
class ClientToolPixelParamRemovalPlugin @Inject constructor() : PixelParamRemovalPlugin {
    override fun names(): List<Pair<String, Set<PixelParameter>>> = listOf(ClientToolPixels.PREFIX to PixelParameter.removeAtb())
}
