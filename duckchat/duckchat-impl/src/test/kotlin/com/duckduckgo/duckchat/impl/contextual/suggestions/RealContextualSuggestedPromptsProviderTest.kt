/*
 * Copyright (c) 2026 DuckDuckGo
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

package com.duckduckgo.duckchat.impl.contextual.suggestions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.duckduckgo.common.test.CoroutineTestRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RealContextualSuggestedPromptsProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val provider = RealContextualSuggestedPromptsProvider(context, coroutineRule.testDispatcherProvider)

    @Test
    fun whenRealCatalogAndDomainMatchesThenResolvesPageSpecificSuggestions() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://www.youtube.com/watch?v=abc",
                uiLocale = "en-US",
            ),
        )

        assertEquals(listOf("summarize-video", "video-key-points"), result.map { it.id })
    }

    @Test
    fun whenRealCatalogAndJsonLdTypeMatchesThenResolvesPageSpecificSuggestions() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = PageTypeSignals(jsonLdType = listOf("Recipe"), ogType = null, lang = "en"),
                url = "https://example.com/recipe",
                uiLocale = "en-US",
            ),
        )

        assertEquals(listOf("shopping-list", "recipe-nutrition", "scale-recipe"), result.map { it.id })
    }

    @Test
    fun whenRealCatalogResolvesThenSummarizePageIsExcluded() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        assertTrue(result.none { it.id == "summarize-page" })
        assertTrue(result.size <= 4)
    }

    @Test
    fun whenRealCatalogAndDifferentLanguageThenTranslateIsTemplated() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = PageTypeSignals(jsonLdType = emptyList(), ogType = null, lang = "fr"),
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        val translate = result.first { it.id == "translate-page" }
        assertEquals("Translate this page into English.", translate.prompt)
    }

    @Test
    fun whenRealCatalogThenBudgetAndPriorityIdsComeFromCatalog() = runTest {
        assertEquals(4, provider.maxSuggestedPrompts())
        assertEquals(setOf("translate-page"), provider.prioritySuggestionIds())
    }

    @Test
    fun whenCatalogAssetMissingThenBudgetMatchesFallbackAndNoPriorityIds() = runTest {
        provider.catalogAssetPath = "DoesNotExist.json"

        assertEquals(1, provider.maxSuggestedPrompts())
        assertEquals(emptySet<String>(), provider.prioritySuggestionIds())
    }

    @Test
    fun whenCatalogAssetMissingThenReturnsSummarizePageFallback() = runTest {
        provider.catalogAssetPath = "DoesNotExist.json"

        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://www.youtube.com/watch?v=abc",
                uiLocale = "en-US",
            ),
        )

        assertEquals(1, result.size)
        assertEquals("summarize-page", result.first().id)
    }
}
