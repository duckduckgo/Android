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

package com.duckduckgo.autofill.impl.importing.takeout.webflow

import com.duckduckgo.autofill.impl.importing.takeout.webflow.TakeoutMessageResult.TakeoutActionError
import com.duckduckgo.autofill.impl.importing.takeout.webflow.TakeoutMessageResult.TakeoutActionSuccess
import com.duckduckgo.autofill.impl.importing.takeout.webflow.TakeoutMessageResult.UnknownMessageFormat
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat
import javax.inject.Inject

/**
 * Parser for web messages from takeout.google.com during bookmark import flow
 */
interface TakeoutWebMessageParser {

    /**
     * Parses a JSON web message from takeout.google.com
     * @param jsonMessage The raw JSON message string
     * @return TakeoutMessageResult containing the parsed action data
     */
    suspend fun parseMessage(jsonMessage: String): TakeoutMessageResult
}

@ContributesBinding(AppScope::class)
class RealTakeoutWebMessageParser @Inject constructor(
    private val dispatchers: DispatcherProvider,
    private val moshi: Moshi,
) : TakeoutWebMessageParser {

    private val adapter by lazy {
        moshi.adapter(TakeoutWebMessage::class.java)
    }

    override suspend fun parseMessage(jsonMessage: String): TakeoutMessageResult {
        return runCatching {
            withContext(dispatchers.io()) {
                val message = adapter.fromJson(jsonMessage) ?: return@withContext UnknownMessageFormat
                val resultData = message.data?.result
                if (resultData.isInvalid()) return@withContext UnknownMessageFormat

                return@withContext if (resultData?.success != null && resultData.success.actionID != null) {
                    TakeoutActionSuccess(actionID = resultData.success.actionID)
                } else {
                    TakeoutActionError(actionID = resultData?.error?.actionID)
                }
            }
        }.getOrElse {
            logcat(WARN) { "Error parsing takeout web message: ${it.asLog()}" }
            UnknownMessageFormat
        }
    }

    private fun RawResultData?.isInvalid(): Boolean {
        if (this?.success == null && this?.error == null) {
            logcat(WARN) { "Error parsing takeout web message: unknown format: $this" }
            return true
        }
        return false
    }
}

/**
 * Public API models for takeout web message parsing results
 */
sealed interface TakeoutMessageResult {
    data class TakeoutActionSuccess(
        val actionID: String,
    ) : TakeoutMessageResult

    data class TakeoutActionError(
        val actionID: String?,
    ) : TakeoutMessageResult

    data object UnknownMessageFormat : TakeoutMessageResult
}

/**
 * Internal JSON models for parsing web messages from takeout.google.com
 * All fields are nullable to gracefully handle structure changes
 */
private data class TakeoutWebMessage(
    @Json(name = "name") val name: String? = null,
    @Json(name = "data") val data: TakeoutMessageData? = null,
)

private data class TakeoutMessageData(
    @Json(name = "result") val result: RawResultData? = null,
)

private data class RawResultData(
    @Json(name = "success") val success: JsonActionSuccess? = null,
    @Json(name = "error") val error: JsonActionError? = null,
)

private data class JsonActionSuccess(
    @Json(name = "actionID") val actionID: String? = null,
)

private data class JsonActionError(
    @Json(name = "actionID") val actionID: String? = null,
)
