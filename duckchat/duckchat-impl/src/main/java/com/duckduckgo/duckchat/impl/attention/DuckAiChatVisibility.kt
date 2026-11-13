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

import androidx.core.net.toUri
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.api.toChatIdOrNull

/**
 * Knows which Duck.ai chat is on screen, if any, from the app and tab events it is given.
 */
class DuckAiChatVisibility(private val duckChat: DuckChat) {

    private var app = AppState.CLOSED
    private var selectedTab: TabState? = null

    val isAppOpen: Boolean get() = app != AppState.CLOSED

    /**
     * The selected tab, whatever it shows. Null until [ChatVisibilityEvent.LaunchLandingResolved].
     * If launch did not land on a Duck.ai chat, null until the next tab change.
     */
    val selectedTabId: String? get() = selectedTab?.tabId

    /**
     * Applies [event]. Returns true when selected tab changed, or the tab moved between Duck.ai and another page
     * or to another Duck.ai chat. Opening or closing the app alone, or moving between two pages that aren't Duck.ai, returns false.
     */
    fun update(event: ChatVisibilityEvent): Boolean {
        val previousTab = selectedTab
        when (event) {
            ChatVisibilityEvent.AppOpened -> if (app == AppState.CLOSED) app = AppState.LAUNCHING
            ChatVisibilityEvent.AppClosed -> app = AppState.CLOSED
            is ChatVisibilityEvent.LaunchLandingResolved -> if (app != AppState.CLOSED) {
                app = AppState.OPEN
                selectedTab = event.tabId?.let { tabState(it, event.url) }
            }
            is ChatVisibilityEvent.DuckAiPageVisible -> if (app == AppState.OPEN) selectedTab = tabState(event.tabId, event.url)
            is ChatVisibilityEvent.SelectedTabChanged -> if (app == AppState.OPEN) selectedTab = event.tabId?.let { tabState(it, event.url) }
        }
        return selectedTab != previousTab
    }

    /** Returns the tab and chat IDs, if a chat is on screen, or null when the app is closed/launching or the selected tab shows something else. */
    fun onScreen(): OnScreen? {
        val tab = selectedTab?.takeIf { app == AppState.OPEN && it.isDuckAi } ?: return null
        return OnScreen(tab.tabId, tab.chatId)
    }

    private fun tabState(tabId: String, url: String?): TabState {
        val uri = url?.takeIf { it.isNotBlank() }?.toUri()
        val isDuckAi = uri != null && duckChat.isDuckChatUrl(uri)
        return TabState(tabId, isDuckAi, uri?.toChatIdOrNull(duckChat))
    }

    // LAUNCHING when we're waiting for initial tab to be resolved by ChatVisibilityEvent.LaunchLandingResolved
    private enum class AppState { CLOSED, LAUNCHING, OPEN }

    private data class TabState(
        val tabId: String,
        val isDuckAi: Boolean,
        val chatId: String?,
    )
}

sealed interface ChatVisibilityEvent {
    data object AppOpened : ChatVisibilityEvent
    data object AppClosed : ChatVisibilityEvent

    /**
     * The launch has decided what to show. [tabId] and [url] are set only when it lands on Duck.ai.
     */
    data class LaunchLandingResolved(val tabId: String?, val url: String?) : ChatVisibilityEvent

    /** A Duck.ai page finished loading in the selected tab. Ignored until [LaunchLandingResolved]. */
    data class DuckAiPageVisible(val tabId: String, val url: String) : ChatVisibilityEvent

    /** The selected tab or its URL changed. Ignored until [LaunchLandingResolved]. */
    data class SelectedTabChanged(val tabId: String?, val url: String?) : ChatVisibilityEvent
}

/**
 * The [tabId] and [chatId] of the Duck.ai chat the user is looking at.
 * [chatId] is null for a new chat until the page creates it and puts the ID in the tab URL.
 */
data class OnScreen(val tabId: String, val chatId: String?)
