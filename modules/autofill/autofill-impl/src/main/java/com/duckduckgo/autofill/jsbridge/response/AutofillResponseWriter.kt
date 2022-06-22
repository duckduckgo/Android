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
import com.squareup.moshi.Moshi

class AutofillResponseWriter(val moshi: Moshi) {

    private val availableInputTypesAdapter = moshi.adapter(AvailableInputSuccessResponse::class.java).indent("  ")
    private val autofillDataAdapterCredentialsAvailable = moshi.adapter(ContainingCredentials::class.java).indent("  ")
    private val autofillDataAdapterCredentialsUnavailable = moshi.adapter(EmptyResponse::class.java).indent("  ")

    fun generateResponseGetAutofillData(credentials: JavascriptCredentials): String {
        val credentialsResponse = ContainingCredentials.CredentialSuccessResponse(credentials)
        val topLevelResponse = ContainingCredentials(success = credentialsResponse)
        return autofillDataAdapterCredentialsAvailable.toJson(topLevelResponse)
    }

    fun generateEmptyResponseGetAutofillData(): String {
        val credentialsResponse = EmptyResponse.EmptyCredentialResponse()
        val topLevelResponse = EmptyResponse(success = credentialsResponse)
        return autofillDataAdapterCredentialsUnavailable.toJson(topLevelResponse)
    }

    fun generateResponseGetAvailableInputTypes(credentialsAvailable: Boolean, emailAvailable: Boolean): String {
        val availableInputTypes = AvailableInputSuccessResponse(credentialsAvailable, emailAvailable)
        return availableInputTypesAdapter.toJson(availableInputTypes)
    }

    /*
    * hardcoded for now, but eventually will be a dump of the most up-to-date privacy remote config, untouched by us
    */
    fun generateContentScope(): String {
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
    fun generateUserUnprotectedDomains(): String {
        return """
            userUnprotectedDomains = [];
        """.trimIndent()
    }

    fun generateUserPreferences(
        autofillCredentials: Boolean,
        showInlineKeyIcon: Boolean = false
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
