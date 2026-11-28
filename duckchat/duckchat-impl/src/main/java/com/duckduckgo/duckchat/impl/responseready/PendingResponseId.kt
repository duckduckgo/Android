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

package com.duckduckgo.duckchat.impl.responseready

import org.json.JSONObject

internal fun findPendingResponseId(chatJson: String?): String? {
    if (chatJson == null) return null
    return runCatching {
        val messages = JSONObject(chatJson).optJSONArray("messages") ?: return null
        val last = messages.optJSONObject(messages.length() - 1) ?: return null
        if (last.optString("role") != "user") return null
        val canonical = last.optJSONObject("meta")?.optJSONObject("recovery")?.stringOrNull("messageId")
        canonical?.takeIf { it.isNotBlank() } ?: last.stringOrNull("messageId")?.takeIf { it.isNotBlank() }
    }.getOrNull()
}

private fun JSONObject.stringOrNull(key: String): String? = opt(key) as? String
