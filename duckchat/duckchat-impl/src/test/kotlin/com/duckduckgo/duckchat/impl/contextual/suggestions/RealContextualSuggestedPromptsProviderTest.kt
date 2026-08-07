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
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

@RunWith(AndroidJUnit4::class)
class RealContextualSuggestedPromptsProviderTest {

    @get:Rule
    val coroutineRule = CoroutineTestRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val duckChatPixels: DuckChatPixels = mock()

    private val provider = RealContextualSuggestedPromptsProvider(context, coroutineRule.testDispatcherProvider, duckChatPixels)

    @Test
    fun whenRealCatalogAndDomainMatchesThenResolvesPageSpecificSuggestions() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://www.youtube.com/watch?v=abc",
                uiLocale = "en-US",
            ),
        ).suggestions

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
        ).suggestions

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
        ).suggestions

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
        ).suggestions

        val translate = result.first { it.id == "translate-page" }
        assertEquals("Translate this page into English.", translate.prompt)
    }

    @Test
    fun whenRealCatalogThenBudgetAndPriorityIdsComeFromCatalog() = runTest {
        assertEquals(4, provider.maxSuggestedPrompts())
        assertEquals(setOf("translate-page"), provider.prioritySuggestionIds())
    }

    @Test
    fun whenCatalogAssetMissingThenMinimalBudgetAndNoPriorityIds() = runTest {
        provider.catalogAssetPath = "DoesNotExist.json"

        assertEquals(1, provider.maxSuggestedPrompts())
        assertEquals(emptySet<String>(), provider.prioritySuggestionIds())
    }

    @Test
    fun whenSuggestionHasLocalizedCopyThenCatalogCopyIsReplaced() = runTest {
        provider.catalogAssetPath = "TestSuggestionsCatalog.json"

        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = PageTypeSignals(jsonLdType = emptyList(), ogType = null, lang = "fr"),
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        val translate = result.first { it.id == "translate-page" }
        assertEquals(context.getString(R.string.duckAiSuggestionTranslatePageLabel), translate.label)
        assertEquals("Translate this page into English.", translate.prompt)
    }

    @Test
    fun whenSuggestionHasNoLocalizedCopyThenCatalogCopyIsKept() = runTest {
        provider.catalogAssetPath = "TestSuggestionsCatalog.json"

        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = PageTypeSignals(jsonLdType = emptyList(), ogType = null, lang = "fr"),
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        val unknown = result.first { it.id == "unknown-id" }
        assertEquals("Unknown", unknown.label)
        assertEquals("Unknown.", unknown.prompt)
    }

    @Test
    fun whenCatalogAssetMissingThenReturnsNoSuggestions() = runTest {
        provider.catalogAssetPath = "DoesNotExist.json"

        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://www.youtube.com/watch?v=abc",
                uiLocale = "en-US",
            ),
        ).suggestions

        assertTrue(result.isEmpty())
    }

    @Test
    fun whenCatalogAssetMissingThenCatalogLoadFailedPixelFiredAndPageTypeStillClassified() = runTest {
        provider.catalogAssetPath = "DoesNotExist.json"

        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = PageTypeSignals(jsonLdType = listOf("Recipe"), ogType = null, lang = "en"),
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        verify(duckChatPixels).reportContextualSuggestionsCatalogLoadFailed()
        assertFalse(result.isSmart)
        assertEquals(SuggestionsPageType.RECIPE, result.pageType)
    }

    @Test
    fun whenRealCatalogResolvesThenNoCatalogLoadFailedPixelFired() = runTest {
        provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        verify(duckChatPixels, never()).reportContextualSuggestionsCatalogLoadFailed()
    }

    @Test
    fun whenRealCatalogAndJsonLdTypeMatchesThenSmartWithClassifiedPageType() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = PageTypeSignals(jsonLdType = listOf("Recipe"), ogType = null, lang = "en"),
                url = "https://example.com/recipe",
                uiLocale = "en-US",
            ),
        )

        assertTrue(result.isSmart)
        assertEquals(SuggestionsPageType.RECIPE, result.pageType)
    }

    @Test
    fun whenRealCatalogAndOnlyDomainMatchesThenSmartWithNonePageType() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://www.youtube.com/watch?v=abc",
                uiLocale = "en-US",
            ),
        )

        assertTrue(result.isSmart)
        assertEquals(SuggestionsPageType.NONE, result.pageType)
    }

    @Test
    fun whenRealCatalogAndNothingMatchesThenNotSmartWithNonePageType() = runTest {
        val result = provider.resolveSuggestions(
            ResolvePageSuggestionsInput(
                pageTypeSignals = null,
                url = "https://example.com",
                uiLocale = "en-US",
            ),
        )

        assertFalse(result.isSmart)
        assertEquals(SuggestionsPageType.NONE, result.pageType)
    }
}
