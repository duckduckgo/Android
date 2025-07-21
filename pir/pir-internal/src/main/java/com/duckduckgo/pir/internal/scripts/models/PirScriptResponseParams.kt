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

data class PirResult(
    val result: PirScriptResultResponse?,
)

data class PirScriptResultResponse(
    val success: PirSuccessResponse? = null,
    val error: PirErrorReponse? = null,
)

data class PirErrorReponse(
    val actionID: String,
    val message: String,
)

sealed class PirSuccessResponse(
    open val actionID: String,
    open val actionType: String,
) {
    data class NavigateResponse(
        override val actionID: String,
        override val actionType: String,
        val response: ResponseData,
    ) : PirSuccessResponse(actionID, actionType) {
        data class ResponseData(
            val url: String,
        )
    }

    data class ExtractedResponse(
        override val actionID: String,
        override val actionType: String,
        val response: List<ScriptExtractedProfile>,
        val meta: AdditionalData? = null,
    ) : PirSuccessResponse(actionID, actionType) {
        data class AdditionalData(
            val userData: ProfileQuery,
        )

        data class ScriptExtractedProfile(
            val name: String? = null,
            val alternativeNames: List<String> = emptyList(),
            val age: String? = null,
            val addresses: List<ScriptAddressCityState> = emptyList(),
            val phoneNumbers: List<String> = emptyList(),
            val relatives: List<String> = emptyList(),
            val profileUrl: String?,
            val identifier: String?,
            val reportId: String? = null,
            val email: String? = null,
            val removedDate: String? = null,
            val fullName: String? = null,
        )

        data class ScriptAddressCityState(
            val city: String,
            val state: String,
            val fullAddress: String? = null,
        )
    }

    data class GetCaptchaInfoResponse(
        override val actionID: String,
        override val actionType: String,
        val meta: AdditionalData? = null,
        val response: ResponseData? = null,
    ) : PirSuccessResponse(actionID, actionType) {
        data class ResponseData(
            val siteKey: String,
            val url: String,
            val type: String,
        )
    }

    data class SolveCaptchaResponse(
        override val actionID: String,
        override val actionType: String,
        val meta: AdditionalData? = null,
        val response: ResponseData? = null,
    ) : PirSuccessResponse(actionID, actionType) {
        data class ResponseData(
            val callback: CallbackData,
        )

        data class CallbackData(
            val eval: String,
        )
    }

    data class FillFormResponse(
        override val actionID: String,
        override val actionType: String,
        val meta: AdditionalData? = null,
    ) : PirSuccessResponse(actionID, actionType)

    data class ClickResponse(
        override val actionID: String,
        override val actionType: String,
        val meta: AdditionalData? = null,
    ) : PirSuccessResponse(actionID, actionType)

    data class ExpectationResponse(
        override val actionID: String,
        override val actionType: String,
        val meta: AdditionalData? = null,
    ) : PirSuccessResponse(actionID, actionType)

    data class AdditionalData(
        val additionalData: String,
    )
}
