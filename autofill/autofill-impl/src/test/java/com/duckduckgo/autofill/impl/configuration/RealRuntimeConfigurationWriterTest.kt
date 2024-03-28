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

import com.duckduckgo.autofill.impl.jsbridge.response.AvailableInputTypeCredentials
import com.squareup.moshi.Moshi.Builder
import org.junit.Assert.assertEquals
import org.junit.Test

class RealRuntimeConfigurationWriterTest {
    private val testee = RealRuntimeConfigurationWriter(Builder().build())

    @Test
    fun whenGenerateResponseGetAvailableInputTypesThenReturnAvailableInputTypesJson() {
        val expectedJson = """
            {
              "credentials": {
                "password": false,
                "username": false
              },
              "email": true
            }
        """.trimIndent()

        assertEquals(
            expectedJson,
            testee.generateResponseGetAvailableInputTypes(
                credentialsAvailable = AvailableInputTypeCredentials(username = false, password = false),
                emailAvailable = true,
            ),
        )
    }

    @Test
    fun whenGenerateContentScopeTheReturnContentScopeString() {
        val expectedJson = """
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
        assertEquals(
            expectedJson,
            testee.generateContentScope(),
        )
    }

    @Test
    fun whenGenerateUserUnprotectedDomainsThenReturnUserUnprotectedDomainsString() {
        val expectedJson = """
            "userUnprotectedDomains" : []
        """.trimIndent()
        assertEquals(
            expectedJson,
            testee.generateUserUnprotectedDomains(),
        )
    }

    @Test
    fun whenGenerateUserPreferencesThenReturnUserPreferencesString() {
        val expectedJson = """
            "userPreferences" : {
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
                      "password_generation": true,
                      "credentials_saving": true,
                      "inlineIcon_credentials": true,
                      "emailProtection_incontext_signup": true
                    }
                  }
                }
              }
            }
        """.trimIndent()
        assertEquals(
            expectedJson,
            testee.generateUserPreferences(
                autofillCredentials = true,
                credentialSaving = true,
                passwordGeneration = true,
                showInlineKeyIcon = true,
                showInContextEmailProtectionSignup = true,
            ),
        )
    }
}
