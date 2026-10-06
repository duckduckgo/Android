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

import android.net.Uri

/**
 * Returns the chatID from this [Uri] when it is a Duck.ai chat URL with a non-blank chatID;
 * null otherwise (including for non-Duck.ai URLs).
 */
fun Uri.toChatIdOrNull(duckChat: DuckChat): String? {
    if (!duckChat.isDuckChatUrl(this)) return null
    return getQueryParameter("chatID")?.takeIf { it.isNotBlank() }
}

/**
 * Whether this [Uri] is a Duck.ai URL for a new, empty chat: carrying no prompt (`q`), no alternate
 * mode such as voice (`mode`), and no conversation started yet (`chatID`). False for non-Duck.ai URLs.
 *
 * Every Duck.ai link carries plumbing parameters of its own, so emptiness is decided on the absence
 * of those three rather than on an empty query string.
 */
fun Uri.isNewDuckChatUrl(duckChat: DuckChat): Boolean {
    if (!duckChat.isDuckChatUrl(this)) return false
    if (!getQueryParameter("q").isNullOrBlank()) return false
    if (!getQueryParameter("mode").isNullOrBlank()) return false
    return toChatIdOrNull(duckChat) == null
}
