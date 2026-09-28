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

package com.duckduckgo.autofill.impl.passkey

import com.duckduckgo.js.messaging.api.JsMessage
import javax.inject.Inject

sealed class PasskeyUsageResult {
    data object Used : PasskeyUsageResult()
    data object Created : PasskeyUsageResult()
    data class UseFailed(val error: PasskeyUsageError) : PasskeyUsageResult()
    data class CreateFailed(val error: PasskeyUsageError) : PasskeyUsageResult()
}

class PasskeyUsedMessageParser @Inject constructor() {

    fun parse(jsMessage: JsMessage): PasskeyUsageResult? {
        val type = jsMessage.params.optString(PARAM_TYPE)
        return when (jsMessage.method) {
            METHOD_PASSKEY_USED -> when (type) {
                TYPE_GET -> PasskeyUsageResult.Used
                TYPE_CREATE -> PasskeyUsageResult.Created
                else -> null
            }

            METHOD_PASSKEY_FAILED -> {
                val error = PasskeyUsageError.from(jsMessage.params.optString(PARAM_ERROR))
                when (type) {
                    TYPE_GET -> PasskeyUsageResult.UseFailed(error)
                    TYPE_CREATE -> PasskeyUsageResult.CreateFailed(error)
                    else -> null
                }
            }

            else -> null
        }
    }

    companion object {
        // c-s-s message handler declarations
        const val FEATURE_NAME = "webCompat"
        const val METHOD_PASSKEY_USED = "passkeyUsed"
        const val METHOD_PASSKEY_FAILED = "passkeyFailed"
        val METHODS = listOf(METHOD_PASSKEY_USED, METHOD_PASSKEY_FAILED)

        private const val PARAM_TYPE = "type"
        private const val PARAM_ERROR = "error"
        private const val TYPE_GET = "get"
        private const val TYPE_CREATE = "create"
    }
}

enum class PasskeyUsageError {
    AbortError,
    ConstraintError,
    EncodingError,
    InvalidStateError,
    NotAllowedError,
    NotReadableError,
    NotSupportedError,
    SecurityError,
    TypeError,
    UnknownError,
    ;

    companion object {
        fun from(raw: String): PasskeyUsageError = entries.firstOrNull { it.name == raw } ?: UnknownError
    }
}
