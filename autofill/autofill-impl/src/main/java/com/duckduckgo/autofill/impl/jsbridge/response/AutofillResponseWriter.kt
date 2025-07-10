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

import com.duckduckgo.autofill.impl.configuration.AutofillAvailableInputTypesProvider.AvailableInputTypes
import com.duckduckgo.autofill.impl.domain.javascript.JavascriptCredentials
import com.duckduckgo.autofill.impl.jsbridge.response.EmailProtectionInContextSignupDismissedAtResponse.DismissedAt
import com.duckduckgo.autofill.impl.jsbridge.response.NewAutofillDataAvailableResponse.NewAutofillDataAvailable
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject

interface AutofillResponseWriter {
    fun generateResponseGetAutofillData(credentials: JavascriptCredentials): String
    fun generateEmptyResponseGetAutofillData(): String
    fun generateResponseForAcceptingGeneratedPassword(): String
    fun generateResponseForRejectingGeneratedPassword(): String
    fun generateResponseForEmailProtectionInContextSignup(installedRecently: Boolean, permanentlyDismissedAtTimestamp: Long?): String
    fun generateResponseForEmailProtectionEndOfFlow(isSignedIn: Boolean): String
    fun generateResponseNewAutofillDataAvailable(inputTypes: AvailableInputTypes): String
}

@ContributesBinding(AppScope::class)
class AutofillJsonResponseWriter @Inject constructor(val moshi: Moshi) : AutofillResponseWriter {

    private val autofillDataAdapterCredentialsAvailable = moshi.adapter(ContainingCredentials::class.java).indent("  ")
    private val autofillDataAdapterCredentialsUnavailable = moshi.adapter(EmptyResponse::class.java).indent("  ")
    private val autofillDataAdapterRefreshData = moshi.adapter(NewAutofillDataAvailableResponse::class.java).indent("  ")
    private val autofillDataAdapterAcceptGeneratedPassword = moshi.adapter(AcceptGeneratedPasswordResponse::class.java).indent("  ")
    private val autofillDataAdapterRejectGeneratedPassword = moshi.adapter(RejectGeneratedPasswordResponse::class.java).indent("  ")
    private val emailProtectionDataAdapterInContextSignup = moshi.adapter(EmailProtectionInContextSignupDismissedAtResponse::class.java).indent("  ")
    private val emailDataAdapterInContextEndOfFlow = moshi.adapter(ShowInContextEmailProtectionSignupPromptResponse::class.java).indent("  ")

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

    override fun generateResponseForEmailProtectionInContextSignup(installedRecently: Boolean, permanentlyDismissedAtTimestamp: Long?): String {
        val response = DismissedAt(isInstalledRecently = installedRecently, permanentlyDismissedAt = permanentlyDismissedAtTimestamp)
        val topLevelResponse = EmailProtectionInContextSignupDismissedAtResponse(success = response)
        return emailProtectionDataAdapterInContextSignup.toJson(topLevelResponse)
    }

    override fun generateResponseForEmailProtectionEndOfFlow(isSignedIn: Boolean): String {
        val response = ShowInContextEmailProtectionSignupPromptResponse.SignupResponse(isSignedIn = isSignedIn)
        val topLevelResponse = ShowInContextEmailProtectionSignupPromptResponse(success = response)
        return emailDataAdapterInContextEndOfFlow.toJson(topLevelResponse)
    }

    override fun generateResponseNewAutofillDataAvailable(inputTypes: AvailableInputTypes): String {
        val credentialTypes = AvailableInputTypeCredentials(username = inputTypes.username, password = inputTypes.password)
        val inputTypesResponse = AvailableInputSuccessResponse(credentialTypes, inputTypes.email, inputTypes.credentialsImport)
        val response = NewAutofillDataAvailable(availableInputTypes = inputTypesResponse)
        val topLevelResponse = NewAutofillDataAvailableResponse(success = response)
        return autofillDataAdapterRefreshData.toJson(topLevelResponse)
    }
}
