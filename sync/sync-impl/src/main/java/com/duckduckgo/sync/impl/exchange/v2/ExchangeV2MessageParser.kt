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
        val type = runCatching { JSONObject(rawJson).optString(ExchangeV2Message.FIELD_TYPE, "") }
            .getOrElse { return ExchangeV2Message.Unknown.fromJson(rawJson, messageType = "") }
        return runCatching {
            when (type) {
                ExchangeV2Message.Hello.TYPE -> ExchangeV2Message.Hello.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeAvailable.TYPE -> ExchangeV2Message.RecoveryCodeAvailable.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeRequest.TYPE -> ExchangeV2Message.RecoveryCodeRequest.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeAwaitingConfirmation.TYPE -> ExchangeV2Message.RecoveryCodeAwaitingConfirmation.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeConfirmed.TYPE -> ExchangeV2Message.RecoveryCodeConfirmed.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeDenied.TYPE -> ExchangeV2Message.RecoveryCodeDenied.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeUnavailable.TYPE -> ExchangeV2Message.RecoveryCodeUnavailable.fromJson(rawJson)
                ExchangeV2Message.RecoveryCodeResponse.TYPE -> ExchangeV2Message.RecoveryCodeResponse.fromJson(rawJson)
                else -> ExchangeV2Message.Unknown.fromJson(rawJson, messageType = type)
            }
        }.getOrElse { ExchangeV2Message.Unknown.fromJson(rawJson, messageType = type) }
    }
}
