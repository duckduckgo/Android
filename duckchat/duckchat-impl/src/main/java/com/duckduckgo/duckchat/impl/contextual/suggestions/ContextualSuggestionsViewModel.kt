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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.di.scopes.ViewScope
import com.duckduckgo.duckchat.impl.feature.DuckChatFeature
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixels
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject

@ContributesViewModel(ViewScope::class)
class ContextualSuggestionsViewModel @Inject constructor(
    private val suggestedPromptsProvider: ContextualSuggestedPromptsProvider,
    private val duckChatFeature: DuckChatFeature,
    private val dispatchers: DispatcherProvider,
    private val duckChatPixels: DuckChatPixels,
) : ViewModel() {

    data class ViewState(
        val suggestions: List<ContextualSuggestedPrompt> = emptyList(),
        val loading: Boolean = false,
    )

    private val _viewState = MutableStateFlow(ViewState())
    val viewState: StateFlow<ViewState> = _viewState.asStateFlow()

    private var loadJob: Job? = null
    private var resolvedSuggestions: List<ContextualSuggestedPrompt> = emptyList()
    private var maxSuggestedPrompts: Int = 1
    private var prioritySuggestionIds: Set<String> = emptySet()
    private var reservedQuickActionSlots: Int = 0
    private var pageType: SuggestionsPageType = SuggestionsPageType.NONE
    private var isSmart: Boolean = false
    private var suggestionsVisible = false

    fun load() {
        loadJob?.cancel()
        suggestionsVisible = false
        loadJob = viewModelScope.launch {
            if (!suggestionsEnabled()) {
                hideSuggestions()
                return@launch
            }
            _viewState.value = ViewState(suggestions = emptyList(), loading = true)
            delay(TIMEOUT_MS)
            if (_viewState.value.loading) {
                duckChatPixels.reportContextualSuggestionsContextCollectionTimedOut()
                resolve(url = null, pageTypeSignals = null)
            }
        }
    }

    fun onSuggestionSelected(suggestionId: String) {
        duckChatPixels.reportContextualSuggestionSelected(suggestionId, pageType.pixelValue)
    }

    fun pageTypePixelValue(): String = pageType.pixelValue

    fun onPageContextUpdated(serializedPageContext: String) {
        val json = runCatching { JSONObject(serializedPageContext) }.getOrNull() ?: return
        if (json.optString("title").isBlank() || json.optString("content").isBlank()) return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            resolve(
                url = json.optString("url").takeIf { it.isNotBlank() },
                pageTypeSignals = parsePageTypeSignals(json),
            )
        }
    }

    fun clear() {
        loadJob?.cancel()
        resolvedSuggestions = emptyList()
        hideSuggestions()
    }

    fun onReservedQuickActionSlotsChanged(count: Int) {
        if (reservedQuickActionSlots == count) return
        reservedQuickActionSlots = count
        _viewState.update { it.copy(suggestions = visibleSuggestions()) }
    }

    private fun visibleSuggestions(): List<ContextualSuggestedPrompt> {
        val capacity = (maxSuggestedPrompts - reservedQuickActionSlots).coerceAtLeast(0)
        if (resolvedSuggestions.size <= capacity) return resolvedSuggestions
        val prioritySuggestions = resolvedSuggestions.filter { it.id in prioritySuggestionIds }
        val regularSuggestions = resolvedSuggestions.filterNot { it.id in prioritySuggestionIds }
        val priorityCount = minOf(prioritySuggestions.size, capacity)
        return regularSuggestions.take(capacity - priorityCount) + prioritySuggestions.take(priorityCount)
    }

    private suspend fun resolve(
        url: String?,
        pageTypeSignals: PageTypeSignals?,
    ) {
        if (!suggestionsEnabled()) {
            hideSuggestions()
            return
        }
        fetchSuggestions(url, pageTypeSignals)
        showSuggestions()
    }

    private suspend fun suggestionsEnabled(): Boolean =
        withContext(dispatchers.io()) { duckChatFeature.contextualSuggestedPrompts().isEnabled() }

    private suspend fun fetchSuggestions(
        url: String?,
        pageTypeSignals: PageTypeSignals?,
    ) {
        val input = ResolvePageSuggestionsInput(
            pageTypeSignals = pageTypeSignals,
            url = url,
            uiLocale = Locale.getDefault().toLanguageTag(),
        )
        val resolved = suggestedPromptsProvider.resolveSuggestions(input)
        maxSuggestedPrompts = suggestedPromptsProvider.maxSuggestedPrompts()
        prioritySuggestionIds = suggestedPromptsProvider.prioritySuggestionIds()
        resolvedSuggestions = resolved.suggestions
        pageType = resolved.pageType
        isSmart = resolved.isSmart
    }

    private fun showSuggestions() {
        val suggestions = visibleSuggestions()
        _viewState.value = ViewState(suggestions = suggestions, loading = false)
        if (suggestions.isEmpty()) {
            suggestionsVisible = false
        } else if (!suggestionsVisible) {
            suggestionsVisible = true
            duckChatPixels.reportContextualSuggestionsViewed(isSmart, pageType.pixelValue)
        }
    }

    private fun hideSuggestions() {
        suggestionsVisible = false
        _viewState.value = ViewState(suggestions = emptyList(), loading = false)
    }

    private fun parsePageTypeSignals(pageContextJson: JSONObject): PageTypeSignals? {
        val signals = pageContextJson.optJSONObject("pageTypeSignals") ?: return null
        val jsonLdArray = signals.optJSONArray("jsonLdType")
        val jsonLdType = buildList {
            if (jsonLdArray != null) {
                for (i in 0 until jsonLdArray.length()) {
                    add(jsonLdArray.optString(i))
                }
            }
        }
        return PageTypeSignals(
            jsonLdType = jsonLdType,
            ogType = signals.optString("ogType").takeIf { it.isNotBlank() },
            lang = signals.optString("lang"),
        )
    }

    companion object {
        private const val TIMEOUT_MS = 5_000L
    }
}
