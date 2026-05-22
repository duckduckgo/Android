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

package com.duckduckgo.sync.impl.exchange.v2

import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import org.json.JSONObject
import javax.inject.Inject

interface ExchangeV2MessageParser {
    fun parse(rawJson: String): ExchangeV2Message
}

@ContributesBinding(AppScope::class)
class JsonExchangeV2MessageParser @Inject constructor() : ExchangeV2MessageParser {

    override fun parse(rawJson: String): ExchangeV2Message {
        val json = runCatching { JSONObject(rawJson) }.getOrNull()
            ?: return ExchangeV2Message.Unknown(rawJson = rawJson, messageType = "")
        return when (val type = json.optString(FIELD_TYPE, "")) {
            ExchangeV2Message.Hello.TYPE -> ExchangeV2Message.Hello(
                rawJson = rawJson,
                channelId = json.optString(FIELD_CHANNEL_ID, ""),
                publicKey = json.optString(FIELD_PUBLIC_KEY, ""),
                version = json.optString(FIELD_VERSION, DEFAULT_VERSION),
            )
            ExchangeV2Message.RecoveryCodeAvailable.TYPE -> ExchangeV2Message.RecoveryCodeAvailable(
                rawJson = rawJson,
                userId = json.optString(FIELD_USER_ID, ""),
                name = json.optString(FIELD_NAME, ""),
                kind = json.optString(FIELD_KIND, ""),
            )
            ExchangeV2Message.RecoveryCodeRequest.TYPE -> ExchangeV2Message.RecoveryCodeRequest(
                rawJson = rawJson,
                name = json.optString(FIELD_NAME, ""),
                kind = json.optString(FIELD_KIND, ""),
            )
            ExchangeV2Message.RecoveryCodeAwaitingConfirmation.TYPE -> ExchangeV2Message.RecoveryCodeAwaitingConfirmation(rawJson)
            ExchangeV2Message.RecoveryCodeConfirmed.TYPE -> ExchangeV2Message.RecoveryCodeConfirmed(rawJson)
            ExchangeV2Message.RecoveryCodeDenied.TYPE -> ExchangeV2Message.RecoveryCodeDenied(rawJson)
            ExchangeV2Message.RecoveryCodeUnavailable.TYPE -> ExchangeV2Message.RecoveryCodeUnavailable(rawJson)
            ExchangeV2Message.RecoveryCodeResponse.TYPE -> ExchangeV2Message.RecoveryCodeResponse(
                rawJson = rawJson,
                recoveryCode = json.optString(FIELD_RECOVERY_CODE, ""),
            )
            else -> ExchangeV2Message.Unknown(rawJson = rawJson, messageType = type)
        }
    }

    companion object {
        private const val FIELD_TYPE = "type"
        private const val FIELD_USER_ID = "user_id"
        private const val FIELD_NAME = "name"
        private const val FIELD_KIND = "kind"
        private const val FIELD_CHANNEL_ID = "channel_id"
        private const val FIELD_PUBLIC_KEY = "public_key"
        private const val FIELD_VERSION = "version"
        private const val FIELD_RECOVERY_CODE = "recovery_code"
        private const val DEFAULT_VERSION = "2"
    }
}
