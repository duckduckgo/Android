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
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.withContext
import logcat.LogPriority.ERROR
import logcat.logcat
import javax.inject.Inject

interface ContextualSuggestedPromptsProvider {
    suspend fun resolveSuggestions(input: ResolvePageSuggestionsInput): List<ContextualSuggestedPrompt>
}

@ContributesBinding(AppScope::class)
class RealContextualSuggestedPromptsProvider @Inject constructor(
    private val context: Context,
    private val dispatcherProvider: DispatcherProvider,
) : ContextualSuggestedPromptsProvider {

    internal var catalogAssetPath: String = CATALOG_ASSET_PATH

    private val moshi by lazy {
        Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    }

    private val bundledCatalog: SuggestionCatalog? by lazy { loadBundledCatalog() }

    override suspend fun resolveSuggestions(
        input: ResolvePageSuggestionsInput,
    ): List<ContextualSuggestedPrompt> = withContext(dispatcherProvider.io()) {
        val catalog = bundledCatalog ?: return@withContext DECODE_FAILURE_FALLBACK
        ContextualSuggestionsMatcher.resolve(input, catalog)
            .filterNot { it.id == SUGGESTION_ID_SUMMARIZE_PAGE }
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
        private val DECODE_FAILURE_FALLBACK = listOf(
            ContextualSuggestedPrompt(
                id = SUGGESTION_ID_SUMMARIZE_PAGE,
                label = "Summarize this page",
                prompt = "Summarize this page.",
                icon = "summary",
            ),
        )
    }
}
