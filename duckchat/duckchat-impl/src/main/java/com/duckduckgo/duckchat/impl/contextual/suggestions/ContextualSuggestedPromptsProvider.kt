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
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.AppScope
import com.duckduckgo.duckchat.impl.R
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

interface ContextualSuggestedPromptsProvider {
    suspend fun resolveSuggestions(input: ResolvePageSuggestionsInput): ResolvedPageSuggestions
    suspend fun maxSuggestedPrompts(): Int
    suspend fun prioritySuggestionIds(): Set<String>
}

@ContributesBinding(AppScope::class)
class RealContextualSuggestedPromptsProvider @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
    private val duckChatPixels: DuckChatPixels,
) : ContextualSuggestedPromptsProvider {

    internal var catalogAssetPath: String = CATALOG_ASSET_PATH

    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val bundledCatalog: SuggestionCatalog? by lazy { loadBundledCatalog() }

    override suspend fun resolveSuggestions(
        input: ResolvePageSuggestionsInput,
    ): ResolvedPageSuggestions = withContext(dispatcherProvider.io()) {
        val catalog = bundledCatalog
        if (catalog == null) {
            duckChatPixels.reportContextualSuggestionsCatalogLoadFailed()
            return@withContext ResolvedPageSuggestions(
                suggestions = emptyList(),
                isSmart = false,
                pageType = ContextualSuggestionsMatcher.classifyPageType(input.pageTypeSignals),
            )
        }
        val resolved = ContextualSuggestionsMatcher.resolve(input, catalog)
        resolved.copy(
            suggestions = resolved.suggestions
                .filterNot { it.id == SUGGESTION_ID_SUMMARIZE_PAGE }
                .map { localize(it, input) },
        )
    }

    private fun localize(
        suggestion: ContextualSuggestedPrompt,
        input: ResolvePageSuggestionsInput,
    ): ContextualSuggestedPrompt {
        val (labelRes, promptRes) = LOCALIZED_COPY_RES[suggestion.id] ?: return suggestion
        return suggestion.copy(
            label = context.getString(labelRes),
            prompt = ContextualSuggestionsMatcher.applyTemplate(context.getString(promptRes), input),
        )
    }

    override suspend fun maxSuggestedPrompts(): Int = withContext(dispatcherProvider.io()) {
        bundledCatalog?.maxSuggestedPrompts ?: 1
    }

    override suspend fun prioritySuggestionIds(): Set<String> = withContext(dispatcherProvider.io()) {
        val catalog = bundledCatalog ?: return@withContext emptySet()
        catalog.defaults.filter { catalog.catalog[it]?.condition != null }.toSet()
    }

    private fun loadBundledCatalog(): SuggestionCatalog? {
        return runCatching {
            val json = context.assets.open(catalogAssetPath).bufferedReader().use { it.readText() }
            moshi.adapter(SuggestionCatalog::class.java).fromJson(json)
        }.onFailure {
            logcat(ERROR) { "[Suggestions] Failed to load $catalogAssetPath: $it" }
        }.getOrNull()
    }

    companion object {
        private const val CATALOG_ASSET_PATH = "PageSuggestionsCatalog.json"
        private const val SUGGESTION_ID_SUMMARIZE_PAGE = "summarize-page"

        private val LOCALIZED_COPY_RES = mapOf(
            "translate-page" to (R.string.duckAiSuggestionTranslatePageLabel to R.string.duckAiSuggestionTranslatePagePrompt),
            "key-takeaways" to (R.string.duckAiSuggestionKeyTakeawaysLabel to R.string.duckAiSuggestionKeyTakeawaysPrompt),
            "explain-simply" to (R.string.duckAiSuggestionExplainSimplyLabel to R.string.duckAiSuggestionExplainSimplyPrompt),
            "counterarguments" to (R.string.duckAiSuggestionCounterargumentsLabel to R.string.duckAiSuggestionCounterargumentsPrompt),
            "related-articles" to (R.string.duckAiSuggestionRelatedArticlesLabel to R.string.duckAiSuggestionRelatedArticlesPrompt),
            "shopping-list" to (R.string.duckAiSuggestionShoppingListLabel to R.string.duckAiSuggestionShoppingListPrompt),
            "recipe-nutrition" to (R.string.duckAiSuggestionRecipeNutritionLabel to R.string.duckAiSuggestionRecipeNutritionPrompt),
            "scale-recipe" to (R.string.duckAiSuggestionScaleRecipeLabel to R.string.duckAiSuggestionScaleRecipePrompt),
            "product-pros-cons" to (R.string.duckAiSuggestionProductProsConsLabel to R.string.duckAiSuggestionProductProsConsPrompt),
            "find-alternatives" to (R.string.duckAiSuggestionFindAlternativesLabel to R.string.duckAiSuggestionFindAlternativesPrompt),
            "summarize-video" to (R.string.duckAiSuggestionSummarizeVideoLabel to R.string.duckAiSuggestionSummarizeVideoPrompt),
            "video-key-points" to (R.string.duckAiSuggestionVideoKeyPointsLabel to R.string.duckAiSuggestionVideoKeyPointsPrompt),
            "tailor-resume" to (R.string.duckAiSuggestionTailorResumeLabel to R.string.duckAiSuggestionTailorResumePrompt),
            "interview-prep" to (R.string.duckAiSuggestionInterviewPrepLabel to R.string.duckAiSuggestionInterviewPrepPrompt),
            "cover-letter" to (R.string.duckAiSuggestionCoverLetterLabel to R.string.duckAiSuggestionCoverLetterPrompt),
            "event-details" to (R.string.duckAiSuggestionEventDetailsLabel to R.string.duckAiSuggestionEventDetailsPrompt),
            "worth-watching" to (R.string.duckAiSuggestionWorthWatchingLabel to R.string.duckAiSuggestionWorthWatchingPrompt),
            "similar-titles" to (R.string.duckAiSuggestionSimilarTitlesLabel to R.string.duckAiSuggestionSimilarTitlesPrompt),
            "cast-crew" to (R.string.duckAiSuggestionCastCrewLabel to R.string.duckAiSuggestionCastCrewPrompt),
            "summarize-book" to (R.string.duckAiSuggestionSummarizeBookLabel to R.string.duckAiSuggestionSummarizeBookPrompt),
            "similar-books" to (R.string.duckAiSuggestionSimilarBooksLabel to R.string.duckAiSuggestionSimilarBooksPrompt),
            "explain-paper" to (R.string.duckAiSuggestionExplainPaperLabel to R.string.duckAiSuggestionExplainPaperPrompt),
            "paper-contributions" to (R.string.duckAiSuggestionPaperContributionsLabel to R.string.duckAiSuggestionPaperContributionsPrompt),
            "menu-highlights" to (R.string.duckAiSuggestionMenuHighlightsLabel to R.string.duckAiSuggestionMenuHighlightsPrompt),
            "place-hours" to (R.string.duckAiSuggestionPlaceHoursLabel to R.string.duckAiSuggestionPlaceHoursPrompt),
            "place-reviews" to (R.string.duckAiSuggestionPlaceReviewsLabel to R.string.duckAiSuggestionPlaceReviewsPrompt),
            "summarize-thread" to (R.string.duckAiSuggestionSummarizeThreadLabel to R.string.duckAiSuggestionSummarizeThreadPrompt),
            "explain-repo" to (R.string.duckAiSuggestionExplainRepoLabel to R.string.duckAiSuggestionExplainRepoPrompt),
            "explain-answer" to (R.string.duckAiSuggestionExplainAnswerLabel to R.string.duckAiSuggestionExplainAnswerPrompt),
            "howto-steps" to (R.string.duckAiSuggestionHowtoStepsLabel to R.string.duckAiSuggestionHowtoStepsPrompt),
            "howto-materials" to (R.string.duckAiSuggestionHowtoMaterialsLabel to R.string.duckAiSuggestionHowtoMaterialsPrompt),
            "course-learn" to (R.string.duckAiSuggestionCourseLearnLabel to R.string.duckAiSuggestionCourseLearnPrompt),
            "course-worth" to (R.string.duckAiSuggestionCourseWorthLabel to R.string.duckAiSuggestionCourseWorthPrompt),
            "faq-answer" to (R.string.duckAiSuggestionFaqAnswerLabel to R.string.duckAiSuggestionFaqAnswerPrompt),
            "faq-summary" to (R.string.duckAiSuggestionFaqSummaryLabel to R.string.duckAiSuggestionFaqSummaryPrompt),
            "review-verdict" to (R.string.duckAiSuggestionReviewVerdictLabel to R.string.duckAiSuggestionReviewVerdictPrompt),
            "review-summary" to (R.string.duckAiSuggestionReviewSummaryLabel to R.string.duckAiSuggestionReviewSummaryPrompt),
            "who-is-this" to (R.string.duckAiSuggestionWhoIsThisLabel to R.string.duckAiSuggestionWhoIsThisPrompt),
            "person-background" to (R.string.duckAiSuggestionPersonBackgroundLabel to R.string.duckAiSuggestionPersonBackgroundPrompt),
        )
    }
}
