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

package com.duckduckgo.pir.internal.scripts.models

sealed class PirError {
    data object MalformedURL : PirError()
    data object NoActionFound : PirError()
    data class ActionFailed(
        val actionID: String,
        val message: String,
    ) : PirError()

    data object ParsingErrorObjectFailed : PirError()
    data object UnknownMethodName : PirError()
    data object UserScriptMessageBrokerNotSet : PirError()
    data class Unknown(
        val error: String,
    ) : PirError()

    data object UnrecoverableError : PirError()
    data object NoOptOutStep : PirError()
    data class CaptchaServiceError(
        val error: String,
    ) : PirError()

    data class EmailError(
        val error: String?,
    ) : PirError()

    data object Cancelled : PirError()
    data object SolvingCaptchaWithCallbackError : PirError()
    data object CantCalculatePreferredRunDate : PirError()
    data class HttpError(
        val code: Int,
    ) : PirError()

    data object DataNotInDatabase : PirError()
}
