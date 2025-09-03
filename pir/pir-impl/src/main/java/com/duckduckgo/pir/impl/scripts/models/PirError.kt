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

package com.duckduckgo.pir.impl.scripts.models

import com.duckduckgo.pir.impl.scripts.models.PirError.JsError
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError.NoActionFound
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError.ParsingErrorObjectFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError.Unknown

data class PirScriptError(
    val error: String,
)

sealed class PirError {
    sealed class JsError : PirError() {
        data object NoActionFound : JsError()
        data object ParsingErrorObjectFailed : JsError()
        data class Unknown(
            val error: String,
        ) : JsError()
    }

    data class ActionFailed(
        val actionID: String,
        val message: String,
    ) : PirError()

    data class CaptchaServiceError(
        val error: String,
    ) : PirError()

    data class EmailError(
        val error: String,
    ) : PirError()
}

fun JsError.asErrorString(): String {
    return when (this) {
        is NoActionFound -> "No action found"
        is ParsingErrorObjectFailed -> "Error in parsing object"
        is Unknown -> this.error
    }
}
