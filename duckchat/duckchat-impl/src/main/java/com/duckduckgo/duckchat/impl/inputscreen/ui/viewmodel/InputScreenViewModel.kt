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

import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.duckduckgo.app.browser.UriString.Companion.isWebUrl
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
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
import com.duckduckgo.common.utils.extensions.toBinaryString
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.EditWithSelectedQuery
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.Command.SwitchToTab
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.InputFieldCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.SearchCommand
import com.duckduckgo.duckchat.impl.inputscreen.ui.command.SearchCommand.ShowRemoveSearchSuggestionDialog
import com.duckduckgo.duckchat.impl.inputscreen.ui.session.InputScreenSessionStore
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.AutoCompleteScrollState
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.InputFieldState
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.InputScreenVisibilityState
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIcon
import com.duckduckgo.duckchat.impl.inputscreen.ui.state.SubmitButtonIconState
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.UserSelectedMode.CHAT
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.UserSelectedMode.NONE
import com.duckduckgo.duckchat.impl.inputscreen.ui.viewmodel.UserSelectedMode.SEARCH
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelName.DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN
import com.duckduckgo.duckchat.impl.pixel.DuckChatPixelParameters
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.voice.api.VoiceSearchAvailability
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

enum class UserSelectedMode {
    SEARCH, CHAT, NONE
}

class InputScreenViewModel @AssistedInject constructor(
    @Assisted currentOmnibarText: String,
    private val autoComplete: AutoComplete,
    private val dispatchers: DispatcherProvider,
    private val history: NavigationHistory,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val autoCompleteSettings: AutoCompleteSettings,
    private val duckChat: DuckChat,
    private val pixel: Pixel,
    private val sessionStore: InputScreenSessionStore,
) : ViewModel() {

    private var hasUserSeenHistoryIAM = false

    private val newTabPageHasContent = MutableStateFlow(false)
    private val voiceServiceAvailable = MutableStateFlow(voiceSearchAvailability.isVoiceSearchAvailable)
    private val voiceInputAllowed = MutableStateFlow(true)
    private var userSelectedMode: UserSelectedMode = NONE
    private val _visibilityState = MutableStateFlow(
        InputScreenVisibilityState(
            voiceInputButtonVisible = voiceServiceAvailable.value && voiceInputAllowed.value,
            autoCompleteSuggestionsVisible = false,
            showChatLogo = true,
            showSearchLogo = true,
            newLineButtonVisible = false,
        ),
    )
    val visibilityState: StateFlow<InputScreenVisibilityState> = _visibilityState.asStateFlow()

    private val initialSearchInputText = currentOmnibarText.trim()
    private val searchInputTextState = MutableStateFlow(initialSearchInputText)

    private val _submitButtonIconState = MutableStateFlow(SubmitButtonIconState(SubmitButtonIcon.SEARCH))
    val submitButtonIconState: StateFlow<SubmitButtonIconState> = _submitButtonIconState.asStateFlow()

    private val refreshSuggestions = MutableSharedFlow<Unit>()

    /**
     * This becomes true when either:
     * 1. The user has modified the input text from its initial state, OR
     * 2. The initial text was not a URL (e.g., search query from SERP)
     */
    private val hasMovedBeyondInitialUrl = MutableStateFlow(checkMovedBeyondInitialUrl(searchInputTextState.value))

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
        hasMovedBeyondInitialUrl,
    ) { autoCompleteEnabled, searchInput, hasMovedBeyondInitialUrl ->
        autoCompleteEnabled &&
            searchInput.isNotEmpty() &&
            hasMovedBeyondInitialUrl
    }.stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    @OptIn(ExperimentalCoroutinesApi::class)
    val autoCompleteSuggestionResults: StateFlow<AutoCompleteResult> = shouldShowAutoComplete
        .flatMapLatest { shouldShow ->
            if (shouldShow) {
                merge(
                    searchInputTextState.debounceExceptFirst(timeoutMillis = 100),
                    refreshSuggestions.map { searchInputTextState.value },
                ).flatMapLatest { autoComplete.autoComplete(it) }
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

    private val _inputFieldState = MutableStateFlow(InputFieldState(canExpand = false))
    val inputFieldState: StateFlow<InputFieldState> = _inputFieldState.asStateFlow()

    private var autoCompleteScrollState = AutoCompleteScrollState()

    val command: SingleLiveEvent<Command> = SingleLiveEvent()
    val searchTabCommand: SingleLiveEvent<SearchCommand> = SingleLiveEvent()

    private val _inputFieldCommand = Channel<InputFieldCommand>(capacity = Channel.CONFLATED)
    val inputFieldCommand: Flow<InputFieldCommand> = _inputFieldCommand.receiveAsFlow()

    init {
        combine(voiceServiceAvailable, voiceInputAllowed) { serviceAvailable, inputAllowed ->
            serviceAvailable && inputAllowed
        }.onEach { voiceInputPossible ->
            _visibilityState.update {
                it.copy(
                    voiceInputButtonVisible = voiceInputPossible,
                )
            }
        }.launchIn(viewModelScope)

        searchInputTextState.onEach { searchInput ->
            if (!hasMovedBeyondInitialUrl.value) {
                hasMovedBeyondInitialUrl.value = checkMovedBeyondInitialUrl(searchInput)
            }
        }.launchIn(viewModelScope)

        hasMovedBeyondInitialUrl.onEach { hasMovedBeyondInitialUrl ->
            _inputFieldState.update {
                it.copy(canExpand = hasMovedBeyondInitialUrl)
            }
        }.launchIn(viewModelScope)

        if (!hasMovedBeyondInitialUrl.value) {
            // If the initial text is a URL, we select all text in the input box
            _inputFieldCommand.trySend(InputFieldCommand.SelectAll)
        }

        shouldShowAutoComplete.onEach { showAutoComplete ->
            _visibilityState.update {
                it.copy(autoCompleteSuggestionsVisible = showAutoComplete)
            }
        }.launchIn(viewModelScope)

        combine(newTabPageHasContent, shouldShowAutoComplete) { newTabPageHasContent, shouldShowAutoComplete ->
            !newTabPageHasContent && !shouldShowAutoComplete
        }.onEach { shouldShowSearchLogo ->
            _visibilityState.update {
                it.copy(showSearchLogo = shouldShowSearchLogo)
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
            autoComplete.fireAutocompletePixel(autoCompleteSuggestionResults.value.suggestions, suggestion, true)
            withContext(dispatchers.main()) {
                when (suggestion) {
                    is AutoCompleteDefaultSuggestion -> onUserSubmittedQuery(suggestion.phrase)
                    is AutoCompleteBookmarkSuggestion -> onUserSubmittedQuery(suggestion.url)
                    is AutoCompleteSearchSuggestion -> onUserSubmittedQuery(suggestion.phrase)
                    is AutoCompleteHistorySuggestion -> onUserSubmittedQuery(suggestion.url)
                    is AutoCompleteHistorySearchSuggestion -> onUserSubmittedQuery(suggestion.phrase)
                    is AutoCompleteSwitchToTabSuggestion -> onUserSwitchedToTab(suggestion.tabId)
                    is AutoCompleteInAppMessageSuggestion -> return@withContext
                    is AutoCompleteSuggestion.AutoCompleteDuckAIPrompt -> onUserTappedDuckAiPromptAutocomplete(suggestion.phrase)
                }
            }
        }
    }

    private fun onUserSwitchedToTab(tabId: String) {
        command.value = SwitchToTab(tabId)
    }

    private fun onUserTappedDuckAiPromptAutocomplete(prompt: String) {
        command.value = Command.SubmitChat(prompt)
        appCoroutineScope.launch(dispatchers.io()) {
            val params = mapOf(DuckChatPixelParameters.WAS_USED_BEFORE to duckChat.wasOpenedBefore().toBinaryString())
            pixel.fire(DuckChatPixelName.DUCK_CHAT_OPEN_AUTOCOMPLETE_EXPERIMENTAL, parameters = params)
        }
        duckChat.openDuckChatWithAutoPrompt(prompt)
    }

    fun userLongPressedAutocomplete(suggestion: AutoCompleteSuggestion) {
        when (suggestion) {
            is AutoCompleteHistorySuggestion, is AutoCompleteHistorySearchSuggestion -> showRemoveSearchSuggestionDialog(suggestion)
            else -> return
        }
    }

    private fun showRemoveSearchSuggestionDialog(suggestion: AutoCompleteSuggestion) {
        appCoroutineScope.launch(dispatchers.main()) {
            searchTabCommand.value = ShowRemoveSearchSuggestionDialog(suggestion)
            hideKeyboard()
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = EditWithSelectedQuery(query)
    }

    fun onRemoveSearchSuggestionConfirmed(
        suggestion: AutoCompleteSuggestion,
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
                refreshSuggestions.emit(Unit)
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
        _submitButtonIconState.update {
            it.copy(icon = if (isWebUrl(query)) SubmitButtonIcon.SEND else SubmitButtonIcon.SEARCH)
        }
    }

    fun onChatInputTextChanged(query: String) {
        _visibilityState.update {
            it.copy(showChatLogo = (query == initialSearchInputText && !it.autoCompleteSuggestionsVisible) || query.isEmpty())
        }
    }

    fun onSearchSubmitted(query: String) {
        val sanitizedQuery = query.replace(oldValue = "\n", newValue = " ")
        command.value = Command.SubmitSearch(sanitizedQuery)
        pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED)
        pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_QUERY_SUBMITTED_DAILY, type = Daily())

        viewModelScope.launch {
            sessionStore.setHasUsedSearchMode(true)
            checkAndFireBothModesPixel()
        }
    }

    fun onChatSubmitted(query: String) {
        viewModelScope.launch {
            val wasDuckAiOpenedBefore = duckChat.wasOpenedBefore()
            if (isWebUrl(query)) {
                command.value = Command.SubmitSearch(query)
            } else {
                command.value = Command.SubmitChat(query)
                duckChat.openDuckChatWithAutoPrompt(query)
            }
            sessionStore.setHasUsedChatMode(true)
            checkAndFireBothModesPixel()

            val params = mapOf(DuckChatPixelParameters.WAS_USED_BEFORE to wasDuckAiOpenedBefore.toBinaryString())
            pixel.fire(pixel = DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED, parameters = params)
            pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_PROMPT_SUBMITTED_DAILY, type = Daily())
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

    fun onChatSelected() {
        viewModelScope.launch {
            _submitButtonIconState.update {
                it.copy(icon = SubmitButtonIcon.SEND)
            }
            _visibilityState.update {
                it.copy(newLineButtonVisible = true)
            }
        }
        if (userSelectedMode == SEARCH) {
            fireModeSwitchedPixel()
        }
        userSelectedMode = CHAT
    }

    fun onSearchSelected() {
        viewModelScope.launch {
            _visibilityState.update {
                it.copy(newLineButtonVisible = false)
            }
        }
        if (userSelectedMode == CHAT) {
            fireModeSwitchedPixel()
        }
        userSelectedMode = SEARCH
    }

    fun onVoiceSearchDisabled() {
        voiceServiceAvailable.value = false
    }

    fun onVoiceInputAllowedChange(allowed: Boolean) {
        voiceInputAllowed.value = allowed
    }

    fun showKeyboard() {
        command.value = Command.ShowKeyboard
    }

    fun hideKeyboard() {
        command.value = Command.HideKeyboard
    }

    fun onInputFieldTouched() {
        _inputFieldState.update {
            it.copy(canExpand = true)
        }
    }

    fun storeAutoCompleteScrollPosition(firstVisibleItemPosition: Int, itemOffsetTop: Int) {
        autoCompleteScrollState = autoCompleteScrollState.copy(
            firstVisibleItemPosition = firstVisibleItemPosition,
            itemOffsetTop = itemOffsetTop,
        )
    }

    fun restoreAutoCompleteScrollPosition() {
        searchTabCommand.value = SearchCommand.RestoreAutoCompleteScrollPosition(
            firstVisibleItemPosition = autoCompleteScrollState.firstVisibleItemPosition,
            itemOffsetTop = autoCompleteScrollState.itemOffsetTop,
        )
        showKeyboard()
    }

    fun onNewTabPageContentChanged(hasContent: Boolean) {
        newTabPageHasContent.value = hasContent
    }

    private fun checkMovedBeyondInitialUrl(searchInput: String): Boolean {
        // check if user modified input or initial text wasn't a webpage URL
        val userHasModifiedInput = initialSearchInputText != searchInput
        val initialTextWasNotWebUrl = !isWebUrl(searchInput) && searchInput.toUri().scheme != DUCK_SCHEME

        return userHasModifiedInput || initialTextWasNotWebUrl
    }

    fun fireShownPixel() {
        pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SHOWN, type = Daily())
    }

    private fun fireModeSwitchedPixel() {
        pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_MODE_SWITCHED)
    }

    private suspend fun checkAndFireBothModesPixel() {
        if (sessionStore.hasUsedSearchMode() && sessionStore.hasUsedChatMode()) {
            pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES)
            pixel.fire(DUCK_CHAT_EXPERIMENTAL_OMNIBAR_SESSION_BOTH_MODES_DAILY, type = Daily())
        }
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

    companion object {
        const val DUCK_SCHEME = "duck"
    }
}

@OptIn(FlowPreview::class)
private fun <T> Flow<T>.debounceExceptFirst(timeoutMillis: Long): Flow<T> {
    return merge(
        take(1),
        drop(1).debounce(timeoutMillis),
    )
}
