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

import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaServiceError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaServiceMaxAttempts
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.CaptchaSolutionFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.ClientError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.EmailError
import com.duckduckgo.pir.impl.scripts.models.PirError.ActionError.JsActionFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError.ActionError
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError.NoActionFound
import com.duckduckgo.pir.impl.scripts.models.PirError.JsError.ParsingErrorObjectFailed
import com.duckduckgo.pir.impl.scripts.models.PirError.UnableToLoadBrokerUrl
import com.duckduckgo.pir.impl.scripts.models.PirError.Unknown

data class PirScriptError(
    val error: String,
)

sealed class PirError {
    data object UnableToLoadBrokerUrl : PirError()

    /**
     * Unexpected error and unrecoverable error from the js layer.
     */
    sealed class JsError : PirError() {
        data object NoActionFound : JsError()
        data object ParsingErrorObjectFailed : JsError()
        data class ActionError(
            val error: String,
        ) : JsError()
    }

    sealed class ActionError(open val actionID: String) : PirError() {
        /**
         * Action execution resolved to a failure (not an error) for a specific action.
         */
        data class JsActionFailed(
            override val actionID: String,
            val message: String,
        ) : ActionError(actionID)

        data class ClientError(
            override val actionID: String,
            val message: String,
        ) : ActionError(actionID)

        /**
         * Email service returned an error.
         */
        data class EmailError(
            override val actionID: String,
            val errorCode: Int,
            val error: String,
        ) : ActionError(actionID)

        /**
         * Captcha service failed to solve the captcha
         */
        data class CaptchaSolutionFailed(
            override val actionID: String,
            val message: String,
        ) : ActionError(actionID)

        /**
         * Captcha service returned an error.
         */
        data class CaptchaServiceError(
            override val actionID: String,
            val errorCode: Int,
            val errorDetails: String,
            val maxAttemptReached: Boolean = false,
        ) : ActionError(actionID)

        /**
         * Captcha service solution check attempts has been maxed.
         */
        data class CaptchaServiceMaxAttempts(
            override val actionID: String,
        ) : ActionError(actionID)
    }

    /**
     * Catch all for any unknown error.
     */
    data class Unknown(
        val error: String,
    ) : PirError()
}

fun PirError.getCategory(): String {
    return when (this) {
        is JsActionFailed -> ERROR_CATEGORY_VALIDATION
        NoActionFound -> ERROR_CATEGORY_VALIDATION
        ParsingErrorObjectFailed -> ERROR_CATEGORY_VALIDATION
        is ActionError -> ERROR_CATEGORY_VALIDATION
        is CaptchaServiceMaxAttempts -> ERROR_CATEGORY_VALIDATION
        is CaptchaSolutionFailed -> ERROR_CATEGORY_VALIDATION
        is CaptchaServiceError -> mapErrorCode(this.errorCode)
        is EmailError -> mapErrorCode(this.errorCode)
        UnableToLoadBrokerUrl -> ERROR_CATEGORY_NETWORK_ERROR
        is ClientError -> ERROR_CATEGORY_CLIENT
        is Unknown -> ERROR_CATEGORY_UNCLASSIFIED
    }
}

private fun mapErrorCode(errorCode: Int): String {
    return if (errorCode == 0) {
        ERROR_CATEGORY_CLIENT
    } else if (errorCode >= 500) {
        "$ERROR_CATEGORY_SERVER-$errorCode"
    } else {
        "$ERROR_CATEGORY_CLIENT-$errorCode"
    }
}

private const val ERROR_CATEGORY_NETWORK_ERROR = "network-error"
private const val ERROR_CATEGORY_VALIDATION = "validation-error"
private const val ERROR_CATEGORY_CLIENT = "client-error"
private const val ERROR_CATEGORY_SERVER = "server-error"
private const val ERROR_CATEGORY_UNCLASSIFIED = ""

fun PirError.getDetails(): String {
    return when (this) {
        UnableToLoadBrokerUrl -> "Unable to load broker url"
        NoActionFound -> "No action found"
        ParsingErrorObjectFailed -> "Error in parsing object"
        is CaptchaServiceMaxAttempts -> "Maximum attempts for captcha solution reached"
        is CaptchaSolutionFailed -> "Failed to solve captcha"
        is ActionError -> this.error
        is JsActionFailed -> this.message
        is CaptchaServiceError -> this.errorDetails
        is EmailError -> this.error
        is Unknown -> this.error
        is ClientError -> this.message
    }
}
