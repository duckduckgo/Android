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

package com.duckduckgo.duckchat.api

/**
 * Result returned by [DuckChatInputCommandPlugin.execute].
 */
sealed class DuckChatContextualResult {
    /** Submit [prompt] and [context] through the contextual Duck.ai WebView (expands the sheet). */
    data class Submit(val prompt: String, val context: String = "") : DuckChatContextualResult()

    /** Show [message] as an inline error; the sheet stays visible in input mode. */
    data class ShowError(val message: String) : DuckChatContextualResult()
}

/**
 * Plugin interface for handling @-prefixed commands typed into the Duck.ai contextual input field.
 *
 * Example: if the user types "@history what was I reading about AI last week" and there is a plugin
 * with [command] == "history", that plugin's [execute] will be called with
 * "what was I reading about AI last week".
 *
 * The command string should be lowercase and contain no @ prefix.
 */
interface DuckChatInputCommandPlugin {
    /** Command keyword without the @ prefix, e.g. "history". */
    val command: String

    /** Called when the user submits input matching this command. [query] is the text after the command. */
    suspend fun execute(query: String): DuckChatContextualResult
}
