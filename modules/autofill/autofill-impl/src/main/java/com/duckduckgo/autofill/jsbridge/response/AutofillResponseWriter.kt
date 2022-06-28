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

package com.duckduckgo.autofill.jsbridge.response

import com.duckduckgo.autofill.domain.javascript.JavascriptCredentials
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import javax.inject.Inject

interface AutofillResponseWriter {
    fun generateResponseGetAutofillData(credentials: JavascriptCredentials): String
    fun generateEmptyResponseGetAutofillData(): String
    fun generateResponseGetAvailableInputTypes(credentialsAvailable: Boolean, emailAvailable: Boolean): String
    fun generateContentScope(): String
    fun generateUserUnprotectedDomains(): String
    fun generateUserPreferences(autofillCredentials: Boolean, showInlineKeyIcon: Boolean = false): String
}

@ContributesBinding(AppScope::class)
class AutofillJsonResponseWriter @Inject constructor(val moshi: Moshi) : AutofillResponseWriter {

    private val availableInputTypesAdapter = moshi.adapter(AvailableInputSuccessResponse::class.java).indent("  ")
    private val autofillDataAdapterCredentialsAvailable = moshi.adapter(ContainingCredentials::class.java).indent("  ")
    private val autofillDataAdapterCredentialsUnavailable = moshi.adapter(EmptyResponse::class.java).indent("  ")

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

    override fun generateResponseGetAvailableInputTypes(credentialsAvailable: Boolean, emailAvailable: Boolean): String {
        val availableInputTypes = AvailableInputSuccessResponse(credentialsAvailable, emailAvailable)
        return availableInputTypesAdapter.toJson(availableInputTypes)
    }

    /*
    * hardcoded for now, but eventually will be a dump of the most up-to-date privacy remote config, untouched by us
    */
    override fun generateContentScope(): String {
        return """
            contentScope = {
              "features": {
                "autofill": {
                  "state": "enabled",
                  "exceptions": []
                }
              },
              "unprotectedTemporary": []
            };
        """.trimIndent()
    }

    /*
     * userUnprotectedDomains: any sites for which the user has chosen to disable privacy protections (leave empty for now)
     */
    override fun generateUserUnprotectedDomains(): String {
        return """
            userUnprotectedDomains = [];
        """.trimIndent()
    }

    override fun generateUserPreferences(
        autofillCredentials: Boolean,
        showInlineKeyIcon: Boolean
    ): String {
        return """
            userPreferences = {
              "debug": false,
              "platform": {
                "name": "android"
              },
              "features": {
                "autofill": {
                  "settings": {
                    "featureToggles": {
                      "inputType_credentials": $autofillCredentials,
                      "inputType_identities": false,
                      "inputType_creditCards": false,
                      "emailProtection": true,
                      "password_generation": false,
                      "credentials_saving": true,
                      "inlineIcon_credentials": $showInlineKeyIcon
                    }
                  }
                }
              }
            };
        """.trimIndent()
    }

}
