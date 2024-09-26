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

package com.duckduckgo.autofill.impl.configuration

import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputSuccessResponse
import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.duckduckgo.di.scopes.AppScope
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import dagger.SingleInstanceIn
import javax.inject.Inject

interface RuntimeConfigurationWriter {
    fun generateResponseGetAvailableInputTypes(
        credentialsAvailable: AvailableInputTypeCredentials,
        emailAvailable: Boolean,
    ): String

    fun generateContentScope(): String
    fun generateUserUnprotectedDomains(): String
    fun generateUserPreferences(
        autofillCredentials: Boolean,
        credentialSaving: Boolean,
        passwordGeneration: Boolean,
        showInlineKeyIcon: Boolean,
        showInContextEmailProtectionSignup: Boolean,
        unknownUsernameCategorization: Boolean,
    ): String
}

@ContributesBinding(AppScope::class)
@SingleInstanceIn(AppScope::class)
class RealRuntimeConfigurationWriter @Inject constructor(val moshi: Moshi) : RuntimeConfigurationWriter {
    private val availableInputTypesAdapter = moshi.adapter(AvailableInputSuccessResponse::class.java).indent("  ")

    override fun generateResponseGetAvailableInputTypes(
        credentialsAvailable: AvailableInputTypeCredentials,
        emailAvailable: Boolean,
    ): String {
        val availableInputTypes = AvailableInputSuccessResponse(credentialsAvailable, emailAvailable)
        return availableInputTypesAdapter.toJson(availableInputTypes)
    }

    /*
    * hardcoded for now, but eventually will be a dump of the most up-to-date privacy remote config, untouched by us
    */
    override fun generateContentScope(): String {
        return """
            "contentScope" : {
              "features": {
                "autofill": {
                  "state": "enabled",
                  "exceptions": []
                }
              },
              "unprotectedTemporary": []
            }
        """.trimIndent()
    }

    /*
     * userUnprotectedDomains: any sites for which the user has chosen to disable privacy protections (leave empty for now)
     */
    override fun generateUserUnprotectedDomains(): String {
        return """
            "userUnprotectedDomains" : []
        """.trimIndent()
    }

    override fun generateUserPreferences(
        autofillCredentials: Boolean,
        credentialSaving: Boolean,
        passwordGeneration: Boolean,
        showInlineKeyIcon: Boolean,
        showInContextEmailProtectionSignup: Boolean,
        unknownUsernameCategorization: Boolean,
    ): String {
        return """
            "userPreferences" : {
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
                      "password_generation": $passwordGeneration,
                      "credentials_saving": $credentialSaving,
                      "inlineIcon_credentials": $showInlineKeyIcon,
                      "emailProtection_incontext_signup": $showInContextEmailProtectionSignup,
                      "unknown_username_categorization": $unknownUsernameCategorization
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
