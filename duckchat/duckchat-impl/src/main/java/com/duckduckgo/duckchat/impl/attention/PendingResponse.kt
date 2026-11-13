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

package com.duckduckgo.duckchat.impl.attention

/**
 * A response the user is waiting for, with the tab and chat it belongs to, and whether the user is looking at it.
 * Requires integration with [DuckAiChatVisibility.onScreen] through [updateOnScreen].
 * It is on screen when [DuckAiChatVisibility.onScreen] shows its chat, or its tab while the chat has no ID yet.
 */
internal open class PendingResponse(
    tabId: String,
    chatId: String? = null,
    chatSwitchedAway: Boolean = false,
) {
    /** The tab the chat was last seen in. Changes when the chat comes on screen in another tab. */
    var tabId = tabId
        private set

    /** Null for a new chat until it comes on screen with an ID. */
    var chatId = chatId
        private set

    var onScreen = false
        private set

    /** True once [tabId] shows another chat, so its statuses no longer describe this response.
     * Cleared when the chat is back on screen, either in the same or different tab.
     */
    var chatSwitchedAway = chatSwitchedAway
        private set

    fun isOnScreenIn(tabId: String): Boolean = onScreen && this.tabId == tabId

    fun updateOnScreen(screen: OnScreen?): ScreenChange? {
        val wasOnScreen = onScreen
        val match = screen?.takeIf { matches(it) }
        onScreen = match != null
        if (match != null) {
            tabId = match.tabId
            chatId = match.chatId
            chatSwitchedAway = false
        } else if (screen?.tabId == tabId) {
            chatSwitchedAway = true
        }
        return when {
            onScreen && !wasOnScreen -> ScreenChange.ENTERED
            wasOnScreen && !onScreen -> ScreenChange.EXITED
            else -> null
        }
    }

    // A new chat has no ID until the page creates it, so match by tab until then.
    private fun matches(screen: OnScreen): Boolean =
        if (chatId != null) chatId == screen.chatId else tabId == screen.tabId && !chatSwitchedAway

    enum class ScreenChange { ENTERED, EXITED }
}

/**
 * Returns the response that a status from [tabId] belongs to. That is the response on screen in that tab, or else an off-screen one
 * whose tab hasn't switched chats. The off-screen case covers a response finishing while the user is away, or streaming before the
 * tab reports it visible.
 */
internal fun <T : PendingResponse> Iterable<T>.describedByStatusFrom(tabId: String): T? {
    val candidates = filter { it.tabId == tabId && !it.chatSwitchedAway }
    return candidates.firstOrNull { it.onScreen } ?: candidates.firstOrNull()
}
