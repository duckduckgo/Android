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

import com.duckduckgo.autofill.impl.domain.javascript.JavascriptCredentials

data class ContainingCredentials(
    val type: String = "getAutofillDataResponse",
    val success: CredentialSuccessResponse,
) {

    data class CredentialSuccessResponse(
        val credentials: JavascriptCredentials,
        val action: String = "fill",
    )
}

data class AcceptGeneratedPasswordResponse(
    val type: String = "getAutofillDataResponse",
    val success: AcceptGeneratedPassword = AcceptGeneratedPassword(),
) {

    data class AcceptGeneratedPassword(val action: String = "acceptGeneratedPassword")
}

data class RejectGeneratedPasswordResponse(
    val type: String = "getAutofillDataResponse",
    val success: RejectGeneratedPassword = RejectGeneratedPassword(),
) {
    data class RejectGeneratedPassword(val action: String = "rejectGeneratedPassword")
}

data class EmptyResponse(
    val type: String = "getAutofillDataResponse",
    val success: EmptyCredentialResponse,
) {

    data class EmptyCredentialResponse(
        val action: String = "none",
    )
}

data class AvailableInputSuccessResponse(
    val credentials: AvailableInputTypeCredentials,
    val email: Boolean,
    val credentialsImport: Boolean,
)

data class AvailableInputTypeCredentials(
    val username: Boolean,
    val password: Boolean,
)

data class EmailProtectionInContextSignupDismissedAtResponse(
    val type: String = "getIncontextSignupDismissedAt",
    val success: DismissedAt,
) {

    data class DismissedAt(val permanentlyDismissedAt: Long? = null, val isInstalledRecently: Boolean)
}

data class ShowInContextEmailProtectionSignupPromptResponse(
    val type: String = "ShowInContextEmailProtectionSignupPromptResponse",
    val success: SignupResponse,
) {
    data class SignupResponse(
        val isSignedIn: Boolean,
    )
}
