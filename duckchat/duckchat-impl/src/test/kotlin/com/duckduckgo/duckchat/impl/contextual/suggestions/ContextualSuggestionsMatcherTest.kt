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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextualSuggestionsMatcherTest {

    private val catalog = SuggestionCatalog(
        maxSuggestedPrompts = 4,
        defaults = listOf("summarize-page", "translate-page"),
        catalog = mapOf(
            "summarize-page" to entry("Summarize this page", icon = "summary", prompt = "Summarize this page."),
            "translate-page" to entry(
                "Translate this page",
                icon = "translate",
                prompt = "Translate this page into {language}.",
                condition = "differentLanguage",
            ),
            "shopping-list" to entry("Generate a shopping list", prompt = "Create a shopping list."),
            "recipe-nutrition" to entry("Estimate the nutrition", prompt = "Estimate the nutrition."),
            "scale-recipe" to entry("Adjust the servings", prompt = "Scale this recipe."),
            "product-pros-cons" to entry("What are the pros and cons?", prompt = "Pros and cons?"),
            "find-alternatives" to entry("Find me alternatives", prompt = "Alternatives?"),
            "summarize-video" to entry("Summarize this video", icon = "summary", prompt = "Summarize this video."),
            "video-key-points" to entry("What are the key points?", prompt = "Key points?"),
            "explain-repo" to entry("Explain this repo", prompt = "Explain this repo."),
        ),
        byJsonLdType = listOf(
            SuggestionCatalog.JsonLdMapping("Recipe", listOf("shopping-list", "recipe-nutrition", "scale-recipe")),
            SuggestionCatalog.JsonLdMapping("Product", listOf("product-pros-cons", "find-alternatives")),
            SuggestionCatalog.JsonLdMapping("VideoObject", listOf("summarize-video", "video-key-points")),
        ),
        byOgType = mapOf(
            "product" to listOf("product-pros-cons", "find-alternatives"),
            "video" to listOf("summarize-video", "video-key-points"),
        ),
        byDomain = mapOf(
            "youtube.com" to listOf("summarize-video", "video-key-points"),
            "github.com" to listOf("explain-repo"),
        ),
    )

    private fun entry(
        label: String,
        icon: String? = null,
        prompt: String,
        condition: String? = null,
    ) = SuggestionCatalog.Entry(label = label, icon = icon, prompt = prompt, condition = condition)

    private fun input(
        jsonLdType: List<String> = emptyList(),
        ogType: String? = null,
        lang: String = "en",
        url: String? = null,
        uiLocale: String = "en-US",
        noSignals: Boolean = false,
    ) = ResolvePageSuggestionsInput(
        pageTypeSignals = if (noSignals) null else PageTypeSignals(jsonLdType, ogType, lang),
        url = url,
        uiLocale = uiLocale,
    )

    @Test
    fun whenJsonLdTypeMatchesThenReturnsThoseSuggestionsFirst() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), catalog).suggestions

        assertEquals(listOf("shopping-list", "recipe-nutrition", "scale-recipe"), result.map { it.id })
    }

    @Test
    fun whenPageSpecificMatchThenSummarizePageDroppedFromDefaults() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Product")), catalog).suggestions

        assertEquals(listOf("product-pros-cons", "find-alternatives"), result.map { it.id })
        assertFalse(result.any { it.id == "summarize-page" })
    }

    @Test
    fun whenJsonLdTypeMatchIsCaseInsensitiveThenMatches() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("  rEcIpE ")), catalog).suggestions

        assertEquals(listOf("shopping-list", "recipe-nutrition", "scale-recipe"), result.map { it.id })
    }

    @Test
    fun whenMultipleJsonLdTypesPresentThenFirstCatalogMappingWins() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Product", "Recipe")), catalog).suggestions

        assertEquals(listOf("shopping-list", "recipe-nutrition", "scale-recipe"), result.map { it.id })
    }

    @Test
    fun whenNoJsonLdMatchButOgTypeMatchesThenUsesOgType() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Unknown"), ogType = "product"), catalog).suggestions

        assertEquals(listOf("product-pros-cons", "find-alternatives"), result.map { it.id })
    }

    @Test
    fun whenOgTypeHasWhitespaceAndCaseThenNormalisedBeforeLookup() {
        val result = ContextualSuggestionsMatcher.resolve(input(ogType = " VIDEO "), catalog).suggestions

        assertEquals(listOf("summarize-video", "video-key-points"), result.map { it.id })
    }

    @Test
    fun whenNoSignalsButUrlDomainMatchesThenUsesDomain() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(noSignals = true, url = "https://www.youtube.com/watch?v=abc"),
            catalog,
        ).suggestions

        assertEquals(listOf("summarize-video", "video-key-points"), result.map { it.id })
    }

    @Test
    fun whenDomainIsExactHostThenMatches() {
        val result = ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = "https://github.com/duckduckgo"), catalog).suggestions

        assertEquals(listOf("explain-repo"), result.map { it.id })
    }

    @Test
    fun whenSignalsMatchThenDomainIsNotConsulted() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Recipe"), url = "https://www.youtube.com/watch?v=abc"),
            catalog,
        ).suggestions

        assertEquals(listOf("shopping-list", "recipe-nutrition", "scale-recipe"), result.map { it.id })
    }

    @Test
    fun whenNothingMatchesThenReturnsDefaultsIncludingSummarizePage() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), url = "https://example.com"),
            catalog,
        ).suggestions

        assertEquals(listOf("summarize-page"), result.map { it.id })
    }

    @Test
    fun whenNoSignalsAndNoUrlThenReturnsDefaults() {
        val result = ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = null), catalog).suggestions

        assertEquals(listOf("summarize-page"), result.map { it.id })
    }

    @Test
    fun whenCandidateAlsoInDefaultsThenDeduplicated() {
        val recipeThenSummarizeCatalog = catalog.copy(defaults = listOf("shopping-list", "summarize-page"))

        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), recipeThenSummarizeCatalog).suggestions

        assertEquals(1, result.count { it.id == "shopping-list" })
    }

    @Test
    fun whenMoreCandidatesThanMaxThenCappedAtMax() {
        val cappedCatalog = catalog.copy(maxSuggestedPrompts = 2)

        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), cappedCatalog).suggestions

        assertEquals(2, result.size)
        assertEquals(listOf("shopping-list", "recipe-nutrition"), result.map { it.id })
    }

    @Test
    fun whenPageLanguageDiffersFromUiThenTranslateIncluded() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(noSignals = false, jsonLdType = listOf("Unknown"), lang = "fr", uiLocale = "en-US"),
            catalog,
        ).suggestions

        assertTrue(result.any { it.id == "translate-page" })
    }

    @Test
    fun whenPageLanguageMatchesUiThenTranslateExcluded() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), lang = "en", uiLocale = "en-US"),
            catalog,
        ).suggestions

        assertFalse(result.any { it.id == "translate-page" })
    }

    @Test
    fun whenPageLanguageIsRegionalVariantOfUiThenTranslateExcluded() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), lang = "en-GB", uiLocale = "en_US"),
            catalog,
        ).suggestions

        assertFalse(result.any { it.id == "translate-page" })
    }

    @Test
    fun whenPageLanguageMissingThenTranslateExcluded() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), lang = "", uiLocale = "en-US"),
            catalog,
        ).suggestions

        assertFalse(result.any { it.id == "translate-page" })
    }

    @Test
    fun whenContextualMatchFillsCapAndTranslateApplicableThenTranslateDisplacesLastSuggestion() {
        val articleCatalog = catalog.copy(
            catalog = catalog.catalog +
                mapOf(
                    "a1" to entry("A1", prompt = "A1."),
                    "a2" to entry("A2", prompt = "A2."),
                    "a3" to entry("A3", prompt = "A3."),
                    "a4" to entry("A4", prompt = "A4."),
                ),
            byJsonLdType = catalog.byJsonLdType + SuggestionCatalog.JsonLdMapping("Article", listOf("a1", "a2", "a3", "a4")),
        )

        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Article"), lang = "fr", uiLocale = "en-US"),
            articleCatalog,
        ).suggestions

        assertEquals(listOf("a1", "a2", "a3", "translate-page"), result.map { it.id })
    }

    @Test
    fun whenTranslateIncludedThenLanguagePlaceholderReplacedWithDisplayName() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), lang = "de", uiLocale = "en-US"),
            catalog,
        ).suggestions

        val translate = result.first { it.id == "translate-page" }
        assertEquals("Translate this page into English.", translate.prompt)
        assertFalse(translate.prompt.contains("{language}"))
    }

    @Test
    fun whenPromptUsesPositionalFormatArgThenLanguageNameIsSubstituted() {
        val result = ContextualSuggestionsMatcher.applyTemplate("Translate this page into %1\$s.", input(uiLocale = "en-US"))

        assertEquals("Translate this page into English.", result)
    }

    @Test
    fun whenPromptHasNoPlaceholderThenLeftUnchanged() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), catalog).suggestions

        assertEquals("Create a shopping list.", result.first { it.id == "shopping-list" }.prompt)
    }

    @Test
    fun whenEntryResolvedThenLabelAndIconCarriedThrough() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("VideoObject")), catalog).suggestions

        val video = result.first { it.id == "summarize-video" }
        assertEquals("Summarize this video", video.label)
        assertEquals("summary", video.icon)
    }

    @Test
    fun whenHostnameMerelyEndsWithDomainTextThenDoesNotMatch() {
        val result = ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = "https://notgithub.com/duckduckgo"), catalog).suggestions

        assertEquals(listOf("summarize-page"), result.map { it.id })
    }

    @Test
    fun whenEntryHasUnknownConditionThenItIsExcluded() {
        val futureCatalog = catalog.copy(
            catalog = catalog.catalog + mapOf("future" to entry("Future", prompt = "Future.", condition = "notYetInvented")),
            byJsonLdType = listOf(SuggestionCatalog.JsonLdMapping("Recipe", listOf("future", "shopping-list"))),
        )

        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), futureCatalog).suggestions

        assertFalse(result.any { it.id == "future" })
        assertTrue(result.any { it.id == "shopping-list" })
    }

    @Test
    fun whenCandidateIdHasNoCatalogEntryThenItIsSkipped() {
        val danglingCatalog = catalog.copy(
            byJsonLdType = listOf(SuggestionCatalog.JsonLdMapping("Recipe", listOf("missing-id", "shopping-list"))),
        )

        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), danglingCatalog).suggestions

        assertFalse(result.any { it.id == "missing-id" })
        assertTrue(result.any { it.id == "shopping-list" })
    }

    @Test
    fun whenUrlIsMalformedThenFallsBackToDefaults() {
        val result = ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = "not a url ://"), catalog).suggestions

        assertEquals(listOf("summarize-page"), result.map { it.id })
    }

    @Test
    fun whenOgTypeIsBlankThenDomainTierIsStillConsulted() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), ogType = "", url = "https://github.com/duckduckgo"),
            catalog,
        ).suggestions

        assertEquals(listOf("explain-repo"), result.map { it.id })
    }

    @Test
    fun whenMaxSuggestedPromptsIsZeroThenAtLeastOneSuggestionIsReturned() {
        val zeroCapCatalog = catalog.copy(maxSuggestedPrompts = 0)

        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), zeroCapCatalog).suggestions

        assertEquals(listOf("shopping-list"), result.map { it.id })
    }

    @Test
    fun whenNothingMatchesAndLanguageDiffersThenDefaultsKeepFloorThenPriorityOrder() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), lang = "fr", uiLocale = "en-US"),
            catalog,
        ).suggestions

        assertEquals(listOf("summarize-page", "translate-page"), result.map { it.id })
    }

    @Test
    fun whenUiLocaleIsNotEnglishThenLanguageNameIsLocalizedInUiLanguage() {
        val result = ContextualSuggestionsMatcher.resolve(
            input(jsonLdType = listOf("Unknown"), lang = "en", uiLocale = "de-DE"),
            catalog,
        ).suggestions

        val translate = result.first { it.id == "translate-page" }
        assertEquals("Translate this page into Deutsch.", translate.prompt)
    }

    @Test
    fun whenContextualMatchThenResultIsSmart() {
        assertTrue(ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Recipe")), catalog).isSmart)
        assertTrue(ContextualSuggestionsMatcher.resolve(input(ogType = "product"), catalog).isSmart)
        assertTrue(ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = "https://github.com/duckduckgo"), catalog).isSmart)
    }

    @Test
    fun whenOnlyDefaultsMatchThenResultIsNotSmart() {
        assertFalse(ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Unknown"), url = "https://example.com"), catalog).isSmart)
        assertFalse(ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = null), catalog).isSmart)
    }

    @Test
    fun whenJsonLdTypeKnownThenPageTypeClassifiedInSignalOrder() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Unknown", " NewsArticle ", "Recipe")), catalog)

        assertEquals(SuggestionsPageType.ARTICLE, result.pageType)
    }

    @Test
    fun whenNoJsonLdMatchThenPageTypeFallsBackToOgType() {
        val result = ContextualSuggestionsMatcher.resolve(input(jsonLdType = listOf("Unknown"), ogType = "video.movie"), catalog)

        assertEquals(SuggestionsPageType.VIDEO, result.pageType)
    }

    @Test
    fun whenDomainOnlyMatchThenPageTypeIsNone() {
        val result = ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = "https://www.youtube.com/watch?v=abc"), catalog)

        assertTrue(result.isSmart)
        assertEquals(SuggestionsPageType.NONE, result.pageType)
    }

    @Test
    fun whenNoSignalsThenPageTypeIsNone() {
        assertEquals(SuggestionsPageType.NONE, ContextualSuggestionsMatcher.resolve(input(noSignals = true, url = null), catalog).pageType)
    }
}
