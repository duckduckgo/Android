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
import com.duckduckgo.autofill.jsbridge.response.AutofillAvailableInputTypesResponse.AvailableInputSuccessResponse
import com.duckduckgo.autofill.jsbridge.response.AutofillDataResponse.CredentialSuccessResponse
import com.squareup.moshi.Moshi
import timber.log.Timber

class AutofillResponseWriter(val moshi: Moshi) {

    private val availableInputTypesAdapter = moshi.adapter(AutofillAvailableInputTypesResponse::class.java).indent("  ")
    private val availableInputTypesAdapterx = moshi.adapter(AvailableInputSuccessResponse::class.java).indent("  ")
    private val autofillDataAdapter = moshi.adapter(AutofillDataResponse::class.java).indent("  ")

    fun generateResponseGetAutofillData(credentials: JavascriptCredentials): String {
        val credentialsResponse = CredentialSuccessResponse(credentials)
        val topLevelResponse = AutofillDataResponse(success = credentialsResponse)
        return autofillDataAdapter.toJson(topLevelResponse).also {
            Timber.i("xxx\n%s", it)
        }
    }

    fun generateResponseGetAvailableInputTypes(credentialsAvailable: Boolean): String {
        val availableInputTypes = AvailableInputSuccessResponse(credentialsAvailable)
        // val topLevelResponse = AutofillAvailableInputTypesResponse(availableInputTypes)
        return availableInputTypesAdapterx.toJson(availableInputTypes)
    }

    /*
    * contentScope: dump of the most up-to-date privacy remote config, untouched by Android code
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

    fun generateUserPreferences(): String {
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
                      "inputType_credentials": true,
                      "inputType_identities": false,
                      "inputType_creditCards": false,
                      "emailProtection": true,
                      "password_generation": false,
                      "credentials_saving": true
                    }
                  }
                }
              }
            };
        """.trimIndent()
    }

}
