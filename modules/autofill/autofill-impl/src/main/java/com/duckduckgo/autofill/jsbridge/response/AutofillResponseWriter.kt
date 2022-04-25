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

import com.duckduckgo.autofill.jsbridge.response.AutofillAvailableInputTypesResponse.AvailableInputSuccessResponse
import com.duckduckgo.autofill.jsbridge.response.AutofillDataResponse.CredentialSuccessResponse
import com.duckduckgo.autofill.store.Credentials
import com.squareup.moshi.Moshi

class AutofillResponseWriter(val moshi: Moshi) {

    private val availableInputTypesAdapter = moshi.adapter(AutofillAvailableInputTypesResponse::class.java).indent("  ")
    private val autofillDataAdapter = moshi.adapter(AutofillDataResponse::class.java).indent("  ")

    fun generateResponseGetAutofillData(credentials: Credentials): String {
        val credentialsResponse = CredentialSuccessResponse(credentials.username, credentials.password)
        val topLevelResponse = AutofillDataResponse(success = credentialsResponse)
        return autofillDataAdapter.toJson(topLevelResponse)
    }

    fun generateResponseGetAvailableInputTypes(credentialsAvailable: Boolean): String {
        val success = AvailableInputSuccessResponse(credentialsAvailable)
        val topLevelResponse = AutofillAvailableInputTypesResponse(success = success)
        return availableInputTypesAdapter.toJson(topLevelResponse)
    }

    /**
     * contentScope: dump of the most up-to-date privacy remote config, untouched by Android code
     * userUnprotectedDomains: any sites for which the user has chosen to disable privacy protections (leave empty for now)
     */
    fun generateResponseGetRuntimeConfiguration(): String {
        return """
            {
              "type": "getRuntimeConfigurationResponse",
              "success": {
                "contentScope": {
                  "features": {
                    "autofill": {
                      "state": "enabled",
                      "exceptions": []
                    }
                  },
                  "unprotectedTemporary": []
                },
                "userUnprotectedDomains": [],
                "userPreferences": {
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
                }
              }
            }
        """.trimIndent()
    }
}
