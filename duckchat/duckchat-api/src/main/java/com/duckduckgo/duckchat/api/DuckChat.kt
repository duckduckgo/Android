/*
 * Copyright (c) 2025 DuckDuckGo
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

import android.net.Uri

/**
 * DuckChat interface provides a set of methods for interacting and controlling DuckChat.
 */
interface DuckChat {
    /**
     * Checks whether DuckChat is enabled based on remote config flag.
     * Uses a cached value - does not perform disk I/O.
     *
     * @return true if DuckChat is enabled, false otherwise.
     */
    fun isEnabled(): Boolean

    /**
     * Checks whether DuckChat should be shown in browser menu based on user settings.
     * Uses cached values - does not perform disk I/O.
     *
     * @return true if DuckChat should be shown, false otherwise.
     */
    fun showInBrowserMenu(): Boolean

    /**
     * Opens the DuckChat WebView with optional pre-filled [query]. The [launchSource] parameter is used for metrics.
     */
    fun openDuckChat(query: String? = null, launchSource: DuckChatLaunchSource)

    /**
     * Auto-prompts the DuckChat WebView with the provided [query]. The [launchSource] parameter is used for metrics.
     */
    fun openDuckChatWithAutoPrompt(query: String, launchSource: DuckChatLaunchSource)

    /**
     * Determines whether a given [Uri] is a DuckChat URL.
     *
     * @return true if it is a DuckChat URL, false otherwise.
     */
    fun isDuckChatUrl(uri: Uri): Boolean
}

/**
 * Origin of the action to launch Duck Chat.
 */
enum class DuckChatLaunchSource(val value: String) {
    /**
     * Opened from anywhere in the WebView, including promos, SERP custom buttons, or direct links.
     */
    WebView("web_view"),

    /**
     * Opened from browser's overflow menu.
     */
    BrowserMenu("browser_menu"),

    /**
     * Opened from new tab screen's overflow menu.
     */
    NewTabMenu("new_tab_menu"),
}
