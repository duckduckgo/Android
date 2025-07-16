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

package com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel

import android.util.Patterns
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteBookmarkSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.duckchat.impl.inputscreen.store.InputScreenDataStore
import com.duckduckgo.duckchat.impl.inputscreen.store.InputScreenMode
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.AutocompleteItemRemoved
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.EditWithSelectedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.ShowRemoveSearchSuggestionDialog
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchToTab
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.InputScreenVisibilityState
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.voice.api.VoiceSearchAvailability
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

class InputScreenViewModel @AssistedInject constructor(
    @Assisted currentOmnibarText: String,
    private val autoComplete: AutoComplete,
    private val dispatchers: DispatcherProvider,
    private val history: NavigationHistory,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val inputScreenDataStore: InputScreenDataStore,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val autoCompleteSettings: AutoCompleteSettings,
) : ViewModel() {

    private var hasUserSeenHistoryIAM = false

    private val voiceServiceAvailable = MutableStateFlow(voiceSearchAvailability.isVoiceSearchAvailable)
    private val voiceInputAllowed = MutableStateFlow(true)
    private val _visibilityState = MutableStateFlow(
        InputScreenVisibilityState(
            voiceInputButtonVisible = voiceServiceAvailable.value && voiceInputAllowed.value,
            forceWebSearchButtonVisible = false,
            autoCompleteSuggestionsVisible = false,
        ),
    )
    val visibilityState: StateFlow<InputScreenVisibilityState> = _visibilityState.asStateFlow()

    private val initialSearchInputText = currentOmnibarText.trim()
    private val searchInputTextState = MutableStateFlow(initialSearchInputText)

    /**
     * Tracks whether we should show autocomplete suggestions based on the initial input state.
     *
     * This becomes true when either:
     * 1. The user has modified the input text from its initial state, OR
     * 2. The initial text was not a URL (e.g., search query from SERP)
     *
     * We suppress autocomplete when the user is on a webpage and the input still shows
     * that page's URL unchanged, since autocomplete suggestions would then obfuscate favorites.
     */
    private var hasMovedBeyondInitialUrl = false

    /**
     * Caches the feature flag and user preference state.
     */
    private val autoCompleteSuggestionsEnabled = MutableStateFlow(autoCompleteSettings.autoCompleteSuggestionsEnabled)

    /**
     * Monitors conditions to determine if auto-complete suggestions should be shown.
     *
     * We only want to show auto-complete suggestions if:
     * - The feature is enabled
     * - The search input text is not empty
     * - Either the user has modified the input OR the initial text wasn't a URL
     *
     * The initial text comes from the address bar. If it's a URL that hasn't been modified,
     * it represents the current webpage, so we suppress autocomplete. If the user is on SERP,
     * the initial text will be the search query (not URL), so we show autocomplete immediately.
     */
    private val shouldShowAutoComplete = combine(
        autoCompleteSuggestionsEnabled,
        searchInputTextState,
    ) { autoCompleteEnabled, searchInput ->
        val shouldShowBasedOnInput = if (hasMovedBeyondInitialUrl) {
            // once user has interacted or initial text wasn't a URL, allow autocomplete (if the rest of the conditions are met as well)
            true
        } else {
            // check if user modified input or initial text wasn't a webpage URL
            val userHasModifiedInput = initialSearchInputText != searchInput
            val initialTextWasNotWebUrl = !searchInput.isWebUrl() && searchInput.toUri().scheme != "duck"

            val shouldShow = userHasModifiedInput || initialTextWasNotWebUrl
            if (shouldShow) {
                hasMovedBeyondInitialUrl = true
            }
            shouldShow
        }

        autoCompleteEnabled &&
            searchInput.isNotEmpty() &&
            shouldShowBasedOnInput
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val autoCompleteSuggestionResults: StateFlow<AutoCompleteResult> = shouldShowAutoComplete
        .flatMapLatest { shouldShow ->
            if (shouldShow) {
                searchInputTextState
                    .debounceExceptFirst(300)
                    .flatMapLatest { autoComplete.autoComplete(it) }
            } else {
                flowOf(AutoCompleteResult("", emptyList()))
            }
        }
        .flowOn(dispatchers.io())
        .onEach { result ->
            if (result.suggestions.contains(AutoCompleteInAppMessageSuggestion)) {
                hasUserSeenHistoryIAM = true
            }
        }
        .flowOn(dispatchers.main())
        .catch { t: Throwable? -> logcat(WARN) { "Failed to get search results: ${t?.asLog()}" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, AutoCompleteResult("", emptyList()))

    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    init {
        viewModelScope.launch {
            inputScreenDataStore.getLastUsedMode().let { mode ->
                command.value = when (mode) {
                    null,
                    InputScreenMode.SEARCH,
                    -> Command.SwitchModeToSearch
                    InputScreenMode.CHAT -> Command.SwitchModeToChat
                }
            }
        }

        combine(voiceServiceAvailable, voiceInputAllowed) { serviceAvailable, inputAllowed ->
            serviceAvailable && inputAllowed
        }.onEach { voiceInputPossible ->
            _visibilityState.update {
                it.copy(
                    voiceInputButtonVisible = voiceInputPossible,
                )
            }
        }.launchIn(viewModelScope)

        shouldShowAutoComplete.onEach { showAutoComplete ->
            _visibilityState.update {
                it.copy(autoCompleteSuggestionsVisible = showAutoComplete)
            }
        }.launchIn(viewModelScope)
    }

    fun onActivityResume() {
        autoCompleteSuggestionsEnabled.value = autoCompleteSettings.autoCompleteSuggestionsEnabled
        _visibilityState.update {
            it.copy(
                voiceInputButtonVisible = voiceSearchAvailability.isVoiceSearchAvailable,
            )
        }
    }

    fun userSelectedAutocomplete(suggestion: AutoCompleteSuggestion) {
        appCoroutineScope.launch(dispatchers.io()) {
            withContext(dispatchers.main()) {
                when (suggestion) {
                    is AutoCompleteDefaultSuggestion -> onUserSubmittedQuery(suggestion.phrase)
                    is AutoCompleteBookmarkSuggestion -> onUserSubmittedQuery(suggestion.url)
                    is AutoCompleteSearchSuggestion -> onUserSubmittedQuery(suggestion.phrase)
                    is AutoCompleteHistorySuggestion -> onUserSubmittedQuery(suggestion.url)
                    is AutoCompleteHistorySearchSuggestion -> onUserSubmittedQuery(suggestion.phrase)
                    is AutoCompleteSwitchToTabSuggestion -> onUserSwitchedToTab(suggestion.tabId)
                    is AutoCompleteInAppMessageSuggestion -> return@withContext
                }
            }
        }
    }

    private fun onUserSwitchedToTab(tabId: String) {
        command.value = SwitchToTab(tabId)
    }

    fun userLongPressedAutocomplete(suggestion: AutoCompleteSuggestion) {
        when (suggestion) {
            is AutoCompleteHistorySuggestion, is AutoCompleteHistorySearchSuggestion -> showRemoveSearchSuggestionDialog(suggestion)
            else -> return
        }
    }

    private fun showRemoveSearchSuggestionDialog(suggestion: AutoCompleteSuggestion) {
        appCoroutineScope.launch(dispatchers.main()) {
            // TODO: handle remove search suggestion
            command.value = ShowRemoveSearchSuggestionDialog(suggestion)
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = EditWithSelectedQuery(query)
    }

    fun onRemoveSearchSuggestionConfirmed(
        suggestion: AutoCompleteSuggestion,
        omnibarText: String,
    ) {
        appCoroutineScope.launch(dispatchers.io()) {
            when (suggestion) {
                is AutoCompleteHistorySuggestion -> {
                    history.removeHistoryEntryByUrl(suggestion.url)
                }

                is AutoCompleteHistorySearchSuggestion -> {
                    history.removeHistoryEntryByQuery(suggestion.phrase)
                }

                else -> {}
            }
            withContext(dispatchers.main()) {
                searchInputTextState.value = omnibarText
                command.value = AutocompleteItemRemoved
            }
        }
    }

    fun onUserSubmittedQuery(query: String) {
        command.value = Command.UserSubmittedQuery(query)
        _visibilityState.update {
            it.copy(
                autoCompleteSuggestionsVisible = false,
            )
        }
    }

    fun onSearchInputTextChanged(query: String) {
        searchInputTextState.value = query.trim()
    }

    fun onUserDismissedAutoCompleteInAppMessage() {
        viewModelScope.launch(dispatchers.io()) {
            autoComplete.userDismissedHistoryInAutoCompleteIAM()
        }
    }

    fun autoCompleteSuggestionsGone() {
        viewModelScope.launch(dispatchers.io()) {
            if (hasUserSeenHistoryIAM) {
                autoComplete.submitUserSeenHistoryIAM()
            }
            hasUserSeenHistoryIAM = false
        }
    }

    fun onSearchSelected() {
        viewModelScope.launch {
            inputScreenDataStore.setLastUsedMode(InputScreenMode.SEARCH)
            _visibilityState.update {
                it.copy(
                    forceWebSearchButtonVisible = false,
                )
            }
        }
    }

    fun onChatSelected() {
        viewModelScope.launch {
            inputScreenDataStore.setLastUsedMode(InputScreenMode.CHAT)
            _visibilityState.update {
                it.copy(
                    forceWebSearchButtonVisible = true,
                )
            }
        }
    }

    fun onVoiceSearchDisabled() {
        voiceServiceAvailable.value = false
    }

    fun onVoiceInputAllowedChange(allowed: Boolean) {
        voiceInputAllowed.value = allowed
    }

    class InputScreenViewModelProviderFactory(
        private val assistedFactory: InputScreenViewModelFactory,
        private val currentOmnibarText: String,
    ) : ViewModelProvider.Factory {

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return assistedFactory.create(currentOmnibarText) as T
        }
    }

    @AssistedFactory
    interface InputScreenViewModelFactory {
        fun create(
            currentOmnibarText: String,
        ): InputScreenViewModel
    }
}

@OptIn(FlowPreview::class)
private fun <T> Flow<T>.debounceExceptFirst(timeoutMillis: Long): Flow<T> {
    return merge(
        take(1),
        drop(1).debounce(timeoutMillis),
    )
}

private fun String.isWebUrl(): Boolean {
    return Patterns.WEB_URL.matcher(this).matches()
}
