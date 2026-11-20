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
 * True when this Duck.ai URL shows a conversation: one of the user's chats or a chat shared with them.
 */
fun Uri.isDuckAiConversation(duckChat: DuckChat): Boolean {
    if (toChatIdOrNull(duckChat) != null) return true
    if (!duckChat.isDuckChatUrl(this)) return false
    val segments = pathSegments
    return segments.size == 2 && segments[0] == SHARE_PATH && segments[1].isNotBlank()
}

private const val SHARE_PATH = "share"
