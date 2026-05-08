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

package com.duckduckgo.duckchat.impl.feature

import org.json.JSONObject

private const val MAX_URL_SUGGESTIONS_KEY = "maxUrlSuggestions"
private const val DEFAULT_MAX_URL_SUGGESTIONS = 3
private const val MAX_HISTORY_COUNT_KEY = "maxHistoryCount"
private const val DEFAULT_MAX_HISTORY_COUNT = 10

fun DuckAiChatHistoryFeature.maxUrlSuggestions(): Int = readIntSetting(MAX_URL_SUGGESTIONS_KEY, DEFAULT_MAX_URL_SUGGESTIONS)

fun DuckAiChatHistoryFeature.maxHistoryCount(): Int = readIntSetting(MAX_HISTORY_COUNT_KEY, DEFAULT_MAX_HISTORY_COUNT)

private fun DuckAiChatHistoryFeature.readIntSetting(key: String, default: Int): Int =
    runCatching {
        self().getSettings()?.let { JSONObject(it).optInt(key, default) }
    }.getOrNull() ?: default
