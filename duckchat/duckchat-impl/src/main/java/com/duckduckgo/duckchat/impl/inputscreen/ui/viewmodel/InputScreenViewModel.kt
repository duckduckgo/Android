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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
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
import com.duckduckgo.di.scopes.FragmentScope
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
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

@ContributesViewModel(FragmentScope::class)
class InputScreenViewModel @Inject constructor(
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

    private val searchInputTextState = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val autoCompleteSuggestionResults: StateFlow<AutoCompleteResult> = searchInputTextState
        .debounce(300)
        .flatMapLatest { autoComplete.autoComplete(it) }
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
    }

    fun onActivityResume() {
        val showAutoCompleteSuggestions = shouldShowAutoCompleteSuggestions()
        _visibilityState.update {
            it.copy(
                voiceInputButtonVisible = voiceSearchAvailability.isVoiceSearchAvailable,
                autoCompleteSuggestionsVisible = showAutoCompleteSuggestions,
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
        if (autoCompleteSettings.autoCompleteSuggestionsEnabled) {
            searchInputTextState.value = query.trim()
        }
        val showAutoCompleteSuggestions = shouldShowAutoCompleteSuggestions()
        _visibilityState.update {
            it.copy(autoCompleteSuggestionsVisible = showAutoCompleteSuggestions)
        }
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

    private fun shouldShowAutoCompleteSuggestions(): Boolean {
        return autoCompleteSettings.autoCompleteSuggestionsEnabled && searchInputTextState.value.isNotBlank()
    }
}
