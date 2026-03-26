/*
 * Copyright (c) 2025 DuckDuckGo
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

package com.duckduckgo.remote.messaging.impl

import com.duckduckgo.remote.messaging.api.Content
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.aJsonMessage
import com.duckduckgo.remote.messaging.fixtures.JsonRemoteMessageOM.smallJsonContent
import com.duckduckgo.remote.messaging.fixtures.messageActionPlugins
import com.duckduckgo.remote.messaging.impl.mappers.mapToRemoteMessage
import com.duckduckgo.remote.messaging.impl.models.JsonContentTranslations
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

/**
 * Tests for language fallback functionality in remote messaging translations.
 * * This verifies the approved proposal behavior:
 * 1. Check for full locale first (e.g., fr-CA)
 * 2. If not found, fall back to language-only match (e.g., fr)
 * 3. If neither found, show default (English)
 */
class LanguageFallbackTest {

    @Test
    fun whenExactLocaleMatchExists_thenUseExactLocale() {
        // Case A: Device fr-CA with fr-CA translation available
        val jsonMessage = aJsonMessage(
            id = "test1",
            content = smallJsonContent(
                titleText = "Default Title",
                descriptionText = "Default Description",
            ),
            translations = mapOf(
                "fr-CA" to JsonContentTranslations(
                    titleText = "Titre pour fr-CA",
                    descriptionText = "Description pour fr-CA",
                ),
                "fr" to JsonContentTranslations(
                    titleText = "Titre générique en français",
                    descriptionText = "Description générique en français",
                ),
            ),
        )

        val result = listOf(jsonMessage).mapToRemoteMessage(Locale.CANADA_FRENCH, messageActionPlugins)

        val content = result.first().content as Content.Small
        assertEquals("Titre pour fr-CA", content.titleText)
        assertEquals("Description pour fr-CA", content.descriptionText)
    }

    @Test
    fun whenExactLocaleNotFound_thenFallbackToLanguage() {
        // Case B: Device fr-BE with only fr-CA and fr translations available
        val jsonMessage = aJsonMessage(
            id = "test2",
            content = smallJsonContent(
                titleText = "Default Title",
                descriptionText = "Default Description",
            ),
            translations = mapOf(
                "fr-CA" to JsonContentTranslations(
                    titleText = "Titre pour fr-CA",
                    descriptionText = "Description pour fr-CA",
                ),
                "fr" to JsonContentTranslations(
                    titleText = "Titre générique en français",
                    descriptionText = "Description générique en français",
                ),
            ),
        )

        // Create a French locale for Belgium (fr-BE)
        val frenchBelgium = Locale("fr", "BE")
        val result = listOf(jsonMessage).mapToRemoteMessage(frenchBelgium, messageActionPlugins)

        val content = result.first().content as Content.Small
        assertEquals("Titre générique en français", content.titleText)
        assertEquals("Description générique en français", content.descriptionText)
    }

    @Test
    fun whenNoLocaleOrLanguageMatch_thenUseDefault() {
        // Case C: Device fr-BE with only fr-CA translation (no language fallback)
        val jsonMessage = aJsonMessage(
            id = "test3",
            content = smallJsonContent(
                titleText = "Default Title",
                descriptionText = "Default Description",
            ),
            translations = mapOf(
                "fr-CA" to JsonContentTranslations(
                    titleText = "Titre pour fr-CA",
                    descriptionText = "Description pour fr-CA",
                ),
                // No "fr" fallback available
            ),
        )

        val frenchBelgium = Locale("fr", "BE")
        val result = listOf(jsonMessage).mapToRemoteMessage(frenchBelgium, messageActionPlugins)

        val content = result.first().content as Content.Small
        // Should use default English text since no fr fallback exists
        assertEquals("Default Title", content.titleText)
        assertEquals("Default Description", content.descriptionText)
    }

    @Test
    fun whenOnlyLanguageFallbackExists_thenUseLanguageFallback() {
        // Case D: Device fr-CA with only fr translation available (no exact match)
        val jsonMessage = aJsonMessage(
            id = "test4",
            content = smallJsonContent(
                titleText = "Default Title",
                descriptionText = "Default Description",
            ),
            translations = mapOf(
                "fr" to JsonContentTranslations(
                    titleText = "Titre générique en français",
                    descriptionText = "Description générique en français",
                ),
                // No fr-CA exact match
            ),
        )

        val result = listOf(jsonMessage).mapToRemoteMessage(Locale.CANADA_FRENCH, messageActionPlugins)

        val content = result.first().content as Content.Small
        assertEquals("Titre générique en français", content.titleText)
        assertEquals("Description générique en français", content.descriptionText)
    }

    @Test
    fun whenMultipleLanguageVariantsExist_thenPrioritizeExactMatch() {
        // Case E: Multiple Spanish variants with exact match priority
        val jsonMessage = aJsonMessage(
            id = "test5",
            content = smallJsonContent(
                titleText = "Default Title",
                descriptionText = "Default Description",
            ),
            translations = mapOf(
                "es-ES" to JsonContentTranslations(
                    titleText = "Título para España",
                    descriptionText = "Descripción para España",
                ),
                "es-MX" to JsonContentTranslations(
                    titleText = "Título para México",
                    descriptionText = "Descripción para México",
                ),
                "es" to JsonContentTranslations(
                    titleText = "Título genérico en español",
                    descriptionText = "Descripción genérica en español",
                ),
            ),
        )

        // Test with Spanish Mexico locale
        val spanishMexico = Locale("es", "MX")
        val result = listOf(jsonMessage).mapToRemoteMessage(spanishMexico, messageActionPlugins)

        val content = result.first().content as Content.Small
        assertEquals("Título para México", content.titleText)
        assertEquals("Descripción para México", content.descriptionText)
    }

    @Test
    fun whenNoTranslationsExist_thenUseDefault() {
        // Case F: No translations map provided
        val jsonMessage = aJsonMessage(
            id = "test6",
            content = smallJsonContent(
                titleText = "Default Title",
                descriptionText = "Default Description",
            ),
            translations = emptyMap(),
        )

        val result = listOf(jsonMessage).mapToRemoteMessage(Locale.FRANCE, messageActionPlugins)

        val content = result.first().content as Content.Small
        assertEquals("Default Title", content.titleText)
        assertEquals("Default Description", content.descriptionText)
    }
}
