/*
 * Copyright (c) 2022 DuckDuckGo
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

package com.duckduckgo.autofill.impl.jsbridge.response

data class AutofillDataResponse(
    val type: String = AUTOFILL_DATA_RESPONSE,
    val success: CredentialSuccessResponse,
) {

    data class CredentialSuccessResponse(
        val username: String? = "",
        val password: String? = null,
    )
}

data class AutofillAvailableInputTypesResponse(
    val type: String = AVAILABLE_INPUT_TYPES_RESPONSE,
    val success: AvailableInputSuccessResponse,
) {

    data class AvailableInputSuccessResponse(
        val credentials: Boolean,
    )
}

private const val AUTOFILL_DATA_RESPONSE = "getAutofillDataResponse"
private const val AVAILABLE_INPUT_TYPES_RESPONSE = "getAvailableInputTypesResponse"
