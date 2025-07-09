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
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealRuntimeConfigurationWriterTest {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val testee = RealRuntimeConfigurationWriter(moshi)
    private val contentScopeJsonAdapter = moshi.adapter(ContentScope::class.java)
    private val availableInputTypesJsonAdapter = moshi.adapter(AvailableInputSuccessResponse::class.java)

    @Test
    fun whenGenerateResponseGetAvailableInputTypesThenReturnAvailableInputTypesJson() {
        val expectedAvailableInputTypes = availableInputTypesJsonAdapter.fromJson(
            """
            {
              "credentials": {
                "password": false,
                "username": false
              },
              "email": true
            }
        """,
        )

        val actualInputTypes = testee.generateResponseGetAvailableInputTypes(
            credentialsAvailable = AvailableInputTypeCredentials(username = false, password = false),
            emailAvailable = true,
        )

        assertAvailableInputTypesJsonCorrect(expectedAvailableInputTypes, actualInputTypes)
    }

    @Test
    fun whenGenerateContentScopeWithSiteSpecificFixesDisabledTheReturnContentScopeString() {
        val expectedContentScope = contentScopeJsonAdapter.fromJson(
            """
            {
              "features": {
                "autofill": {
                  "state": "enabled",
                  "exceptions": [],
                  "features": {
                    
                  }
                }
              },
              "unprotectedTemporary": []
            }
        """,
        )

        val actualContentScope = AutofillSiteSpecificFixesSettings(
            javascriptConfigSiteSpecificFixes = "{settings: {}}",
            canApplySiteSpecificFixes = false,
        ).asJsonConfig()

        assertContentScopeJsonCorrect(expectedContentScope, actualContentScope)
    }

    @Test
    fun whenGenerateContentScopeWithSiteSpecificFixesEnabledTheReturnContentScopeString() {
        val expectedContentScope = contentScopeJsonAdapter.fromJson(
            """
               {
              "features": {
                "autofill": {
                  "state": "enabled",
                  "exceptions": [],
                  "features": {
                    "siteSpecificFixes": {
                        "state": "enabled",
                        "settings": ${populatedJsConfig()}
                    }
                  }
                }
              },
              "unprotectedTemporary": []
            }
        """,
        )

        val actualContentScope = AutofillSiteSpecificFixesSettings(
            javascriptConfigSiteSpecificFixes = populatedJsConfig(),
            canApplySiteSpecificFixes = true,
        ).asJsonConfig()
        assertContentScopeJsonCorrect(expectedContentScope, actualContentScope)
    }

    @Test
    fun whenGenerateUserUnprotectedDomainsThenReturnUserUnprotectedDomainsString() {
        val expectedJson = "userUnprotectedDomains = [];"
        assertEquals(
            expectedJson,
            testee.generateUserUnprotectedDomains(),
        )
    }

    @Test
    fun whenGenerateUserPreferencesThenReturnUserPreferencesString() {
        val expectedJson = """
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
                      "password_generation": true,
                      "credentials_saving": true,
                      "inlineIcon_credentials": true,
                      "emailProtection_incontext_signup": true,
                      "unknown_username_categorization": false,
                      "partial_form_saves": false,
                      "password_variant_categorization" : false
                    }
                  }
                }
              }
            };
        """.trimIndent()
        assertEquals(
            expectedJson,
            testee.generateUserPreferences(
                autofillCredentials = true,
                credentialSaving = true,
                passwordGeneration = true,
                showInlineKeyIcon = true,
                showInContextEmailProtectionSignup = true,
                unknownUsernameCategorization = false,
                canCategorizePasswordVariant = false,
                partialFormSaves = false,
            ),
        )
    }

    data class ContentScope(
        @Json(name = "features") val features: Features,
        @Json(name = "unprotectedTemporary") val unprotectedTemporary: List<Any> = emptyList(),
    )

    data class Features(
        @Json(name = "autofill") val autofill: Autofill,
    )

    data class Autofill(
        @Json(name = "state") val state: String,
        @Json(name = "exceptions") val exceptions: List<Any> = emptyList(),
        @Json(name = "features") val features: AutofillFeatures,
    )

    data class AutofillFeatures(
        @Json(name = "siteSpecificFixes") val siteSpecificFixes: SiteSpecificFixes? = null,
    )

    data class SiteSpecificFixes(
        @Json(name = "state") val state: String,
        @Json(name = "settings") val settings: Map<String, Any> = emptyMap(),
    )

    private fun assertContentScopeJsonCorrect(
        expected: ContentScope?,
        json: String,
    ) {
        assertEquals(expected, contentScopeJsonAdapter.fromJson(json))
    }

    private fun assertAvailableInputTypesJsonCorrect(
        expected: AvailableInputSuccessResponse?,
        json: String,
    ) {
        assertEquals(expected, availableInputTypesJsonAdapter.fromJson(json))
    }

    private fun extractJson(generatedJson: String): String {
        assertTrue(generatedJson.startsWith("contentScope = "))
        assertTrue(generatedJson.endsWith(";"))
        val json = generatedJson.removePrefix("contentScope = ").removeSuffix(";")
        return json
    }

    private fun AutofillSiteSpecificFixesSettings.asJsonConfig(): String {
        return extractJson(testee.generateContentScope(this))
    }

    private fun populatedJsConfig(): String {
        return """
            {
                "some": "javascript", 
                "config": "in here"
            }
        """.trimIndent()
    }
}
