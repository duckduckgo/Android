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

package com.duckduckgo.duckchat.impl.messaging.sync

import com.duckduckgo.duckchat.impl.DuckChatConstants.JsResponseKeys
import com.duckduckgo.js.messaging.api.JsCallbackData
import com.duckduckgo.js.messaging.api.JsMessage
import com.duckduckgo.js.messaging.api.JsMessaging
import logcat.LogPriority
import logcat.logcat
import org.json.JSONObject

/**
 * Helper class for sending JS responses from sync handlers.
 */
class SyncJsResponder(
    private val jsMessaging: JsMessaging,
    private val jsMessage: JsMessage,
    private val featureName: String,
) {
    fun sendSuccess(payload: JSONObject) {
        val jsonPayload = JSONObject().apply {
            put(JsResponseKeys.OK, true)
            put(JsResponseKeys.PAYLOAD, payload)
        }
        runCatching {
            jsMessaging.onResponse(JsCallbackData(jsonPayload, featureName, jsMessage.method, jsMessage.id!!))
        }.onFailure { e ->
            logcat(LogPriority.ERROR) { "DuckChat-Sync: failed to send success response: ${e.message}" }
        }
    }

    fun sendError(error: String) {
        val errorPayload = JSONObject().apply {
            put(JsResponseKeys.OK, false)
            put(JsResponseKeys.REASON, error)
        }
        runCatching {
            jsMessaging.onResponse(JsCallbackData(errorPayload, featureName, jsMessage.method, jsMessage.id!!))
            logcat { "DuckChat-Sync: error: $error" }
        }.onFailure { e ->
            logcat(LogPriority.ERROR) { "DuckChat-Sync: failed to send error response: ${e.message}" }
        }
    }
}
