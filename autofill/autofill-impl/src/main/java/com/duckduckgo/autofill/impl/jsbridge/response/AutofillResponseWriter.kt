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
import com.duckduckgo.di.scopes.FragmentScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject

interface AutofillResponseWriter {
    fun generateResponseGetAutofillData(credentials: JavascriptCredentials): String
    fun generateEmptyResponseGetAutofillData(): String
    fun generateResponseForAcceptingGeneratedPassword(): String
    fun generateResponseForRejectingGeneratedPassword(): String
}

@ContributesBinding(FragmentScope::class)
class AutofillJsonResponseWriter @Inject constructor(val moshi: Moshi) : AutofillResponseWriter {

    private val autofillDataAdapterCredentialsAvailable = moshi.adapter(ContainingCredentials::class.java).indent("  ")
    private val autofillDataAdapterCredentialsUnavailable = moshi.adapter(EmptyResponse::class.java).indent("  ")
    private val autofillDataAdapterAcceptGeneratedPassword = moshi.adapter(AcceptGeneratedPasswordResponse::class.java).indent("  ")
    private val autofillDataAdapterRejectGeneratedPassword = moshi.adapter(RejectGeneratedPasswordResponse::class.java).indent("  ")

    override fun generateResponseGetAutofillData(credentials: JavascriptCredentials): String {
        val credentialsResponse = ContainingCredentials.CredentialSuccessResponse(credentials)
        val topLevelResponse = ContainingCredentials(success = credentialsResponse)
        return autofillDataAdapterCredentialsAvailable.toJson(topLevelResponse)
    }

    override fun generateEmptyResponseGetAutofillData(): String {
        val credentialsResponse = EmptyResponse.EmptyCredentialResponse()
        val topLevelResponse = EmptyResponse(success = credentialsResponse)
        return autofillDataAdapterCredentialsUnavailable.toJson(topLevelResponse)
    }

    override fun generateResponseForAcceptingGeneratedPassword(): String {
        val response = AcceptGeneratedPasswordResponse.AcceptGeneratedPassword()
        val topLevelResponse = AcceptGeneratedPasswordResponse(success = response)
        return autofillDataAdapterAcceptGeneratedPassword.toJson(topLevelResponse)
    }

    override fun generateResponseForRejectingGeneratedPassword(): String {
        val response = RejectGeneratedPasswordResponse.RejectGeneratedPassword()
        val topLevelResponse = RejectGeneratedPasswordResponse(success = response)
        return autofillDataAdapterRejectGeneratedPassword.toJson(topLevelResponse)
    }
}
