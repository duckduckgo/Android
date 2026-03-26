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

package com.duckduckgo.autofill.impl.jsbridge.request

import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.PASSWORD
import com.duckduckgo.autofill.impl.jsbridge.request.SupportedAutofillInputSubType.USERNAME
import com.squareup.moshi.Json

data class AutofillDataRequest(
    val mainType: SupportedAutofillInputMainType,
    val subType: SupportedAutofillInputSubType,
    val trigger: SupportedAutofillTriggerType,
    val generatedPassword: GeneratedPasswordRequest?,
) {

    fun isGeneratedPasswordAvailable(): Boolean {
        return mainType == SupportedAutofillInputMainType.CREDENTIALS &&
            subType == PASSWORD &&
            generatedPassword?.value != null
    }

    fun isAutofillCredentialsRequest(): Boolean {
        return mainType == SupportedAutofillInputMainType.CREDENTIALS &&
            (subType == USERNAME || subType == PASSWORD)
    }
}

data class GeneratedPasswordRequest(
    val value: String?,
    val username: String?,
)

enum class SupportedAutofillInputType {
    @Json(name = "credentials.password")
    AUTOFILL_PASSWORD,

    @Json(name = "credentials.username")
    AUTOFILL_CREDENTIALS,
}

enum class SupportedAutofillInputMainType {
    @Json(name = "credentials")
    CREDENTIALS,

    @Json(name = "identities")
    IDENTITIES,

    @Json(name = "creditCards")
    CREDIT_CARDS,
}

enum class SupportedAutofillInputSubType {
    @Json(name = "username")
    USERNAME,

    @Json(name = "password")
    PASSWORD,
}

enum class SupportedAutofillTriggerType {
    @Json(name = "userInitiated")
    USER_INITIATED,

    @Json(name = "autoprompt")
    AUTOPROMPT,

    @Json(name = "credentialsImport")
    CREDENTIALS_IMPORT,
}

enum class FormSubmissionTriggerType {
    @Json(name = "formSubmission")
    FORM_SUBMISSION,

    @Json(name = "partialSave")
    PARTIAL_SAVE,

    UNKNOWN,
}
