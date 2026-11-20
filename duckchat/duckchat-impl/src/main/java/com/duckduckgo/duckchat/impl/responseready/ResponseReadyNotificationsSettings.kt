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

import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import org.json.JSONObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class ResponseReadyNotificationsSettings(
    val pollInterval: Duration,
    val timeout: Duration,
    val errorSettle: Duration,
)

private const val POLL_INTERVAL_KEY = "pollIntervalSeconds"
private const val DEFAULT_POLL_INTERVAL_SECONDS = 4
private const val TIMEOUT_KEY = "timeoutSeconds"
private const val DEFAULT_TIMEOUT_SECONDS = 180
private const val ERROR_SETTLE_KEY = "errorSettleSeconds"
private const val DEFAULT_ERROR_SETTLE_SECONDS = 5

internal fun DuckChatFeature.responseReadySettings(): ResponseReadyNotificationsSettings {
    val settings = responseReadyNotifications().getSettings()
    return ResponseReadyNotificationsSettings(
        pollInterval = readSeconds(settings, POLL_INTERVAL_KEY, DEFAULT_POLL_INTERVAL_SECONDS),
        timeout = readSeconds(settings, TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS),
        errorSettle = readSeconds(settings, ERROR_SETTLE_KEY, DEFAULT_ERROR_SETTLE_SECONDS),
    )
}

private fun readSeconds(settings: String?, key: String, default: Int): Duration {
    val value = settings?.let { runCatching { JSONObject(it).optInt(key) }.getOrNull() }
    return (value?.takeIf { it > 0 } ?: default).seconds
}
