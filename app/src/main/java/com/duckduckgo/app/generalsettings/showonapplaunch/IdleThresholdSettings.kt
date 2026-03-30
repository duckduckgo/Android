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

package com.duckduckgo.app.generalsettings.showonapplaunch

import org.json.JSONObject

data class IdleThresholdSettings(
    val defaultIdleThresholdSeconds: Long,
    val idleThresholdOptions: List<Long>,
)

const val DEFAULT_IDLE_THRESHOLD_SECONDS = 300L

val DEFAULT_IDLE_THRESHOLD_OPTIONS = listOf(1L, 60L, 300L, 600L, 3600L, 43200L, 86400L)

fun parseIdleThresholdSettings(json: String?): IdleThresholdSettings? {
    json ?: return null
    return runCatching {
        val obj = JSONObject(json)
        val default = obj.getLong("defaultIdleThresholdSeconds")
        val optionsArray = obj.optJSONArray("idleThresholdOptions")
        val options = if (optionsArray != null) {
            (0 until optionsArray.length()).map { optionsArray.getLong(it) }
        } else {
            DEFAULT_IDLE_THRESHOLD_OPTIONS
        }
        IdleThresholdSettings(default, options)
    }.getOrNull()
}
