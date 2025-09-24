/*
 * Copyright (c) 2020 DuckDuckGo
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

package com.duckduckgo.app.systemsearch

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duckduckgo.anvil.annotations.ContributesViewModel
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter
import com.duckduckgo.browser.ui.omnibar.OmnibarPosition
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import com.duckduckgo.voice.api.VoiceSearchAvailability
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import logcat.LogPriority.WARN
import logcat.asLog
import logcat.logcat

data class SystemSearchResult(
    val autocomplete: AutoCompleteResult,
    val deviceApps: List<DeviceApp>,
)

@ContributesViewModel(ActivityScope::class)
class SystemSearchViewModel @Inject constructor(
    private val duckAiFeatureState: DuckAiFeatureState,
    private val voiceSearchAvailability: VoiceSearchAvailability,
    private val duckChat: DuckChat,
    private var userStageStore: UserStageStore,
    private val autoComplete: AutoComplete,
    private val deviceAppLookup: DeviceAppLookup,
    private val pixel: Pixel,
    private val savedSitesRepository: SavedSitesRepository,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val autoCompleteSettings: AutoCompleteSettings,
    private val history: NavigationHistory,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ViewModel(), EditSavedSiteDialogFragment.EditSavedSiteListener {

    data class OnboardingViewState(
        val visible: Boolean,
        val expanded: Boolean = false,
    )

    data class OmnibarViewState(
        val isVoiceSearchButtonVisible: Boolean = false,
        val isDuckAiButtonVisible: Boolean = false,
        val isClearButtonVisible: Boolean = false,
    ) {
        val isButtonDividerVisible: Boolean
            get() = (isClearButtonVisible || isVoiceSearchButtonVisible) && isDuckAiButtonVisible
    }

    sealed class Suggestions {
        data class SystemSearchResultsViewState(
            val autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
            val appResults: List<DeviceApp> = emptyList(),
        ) : Suggestions()

        data class QuickAccessItems(val favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite> = emptyList()) : Suggestions()
    }

    data class HiddenBookmarksIds(val favorites: List<String> = emptyList())

    sealed class Command {
        data object ClearInputText : Command()
        data object LaunchDuckDuckGo : Command()
        data class LaunchBrowser(val query: String) : Command()
        data class LaunchBrowserAndSwitchToTab(
            val query: String,
            val tabId: String,
        ) : Command()

        data class LaunchEditDialog(val savedSite: SavedSite) : Command()
        data class DeleteFavoriteConfirmation(val savedSite: SavedSite) : Command()
        data class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
        data class LaunchDeviceApplication(val deviceApp: DeviceApp) : Command()
        data class ShowAppNotFoundMessage(val appName: String) : Command()
        data object DismissKeyboard : Command()
        data class EditQuery(val query: String) : Command()
        data class ShowRemoveSearchSuggestionDialog(val suggestion: AutoCompleteSuggestion) : Command()
        data object AutocompleteItemRemoved : Command()
        data object ExitSearch : Command()
    }

    val onboardingViewState: MutableLiveData<OnboardingViewState> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    @VisibleForTesting
    internal val queryFlow = MutableStateFlow("")
    private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1)
    private val voiceSearchState = MutableSharedFlow<Unit>(replay = 1)
    private val hiddenIds = MutableStateFlow(HiddenBookmarksIds())

    private var hasUserSeenHistory = false
    private var omnibarPosition: OmnibarPosition = appSettingsPreferencesStore.omnibarPosition

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val suggestionsViewState = combine(queryFlow, refreshTrigger) { query, _ -> query.trim() }
        .debounce(DEBOUNCE_TIME_MS)
        .distinctUntilChanged()
        .flatMapLatest { query ->
            buildResultsFlow(query)
        }
        .flowOn(dispatchers.io())
        .catch { t: Throwable? -> logcat(WARN) { "Failed to get search results: ${t?.asLog()}" } }
        .map { results ->
            updateResults(results)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, Suggestions.SystemSearchResultsViewState())

    val favoritesViewState = combine(
        flow = savedSitesRepository.getFavorites(),
        flow2 = hiddenIds,
        queryFlow,
    ) { favorites, hiddenIds, query ->
        if (query.isEmpty()) {
            favorites.filter { it.id !in hiddenIds.favorites }
        } else {
            emptyList()
        }
    }.map { favorites ->
        Suggestions.QuickAccessItems(favorites = favorites.map { favorite -> FavoritesQuickAccessAdapter.QuickAccessFavorite(favorite) })
    }.stateIn(viewModelScope, SharingStarted.Lazily, Suggestions.QuickAccessItems())

    val omnibarViewState = combine(
        flow = voiceSearchState.map { voiceSearchAvailability.isVoiceSearchAvailable },
        flow2 = queryFlow,
        flow3 = duckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus,
    ) { isVoiceSearchEnabled, query, isDuckAiEnabled ->
        OmnibarViewState(
            isVoiceSearchButtonVisible = isVoiceSearchEnabled,
            isDuckAiButtonVisible = isDuckAiEnabled,
            isClearButtonVisible = query.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, OmnibarViewState())

    init {
        resetViewState()
        refreshAppList()
    }

    private fun currentOnboardingState(): OnboardingViewState = onboardingViewState.value!!

    fun resetViewState() {
        command.value = Command.ClearInputText
        viewModelScope.launch {
            resetOnboardingState()
        }

        queryFlow.update { "" }

        voiceSearchState.tryEmit(Unit)
        refreshTrigger.tryEmit(Unit)
    }

    private suspend fun resetOnboardingState() {
        val showOnboarding = userStageStore.isNewUser()
        onboardingViewState.value = OnboardingViewState(visible = showOnboarding)
        if (showOnboarding) {
            pixel.fire(INTERSTITIAL_ONBOARDING_SHOWN)
        }
    }

    private fun buildResultsFlow(query: String): Flow<SystemSearchResult> {
        return combine(
            autoComplete.autoComplete(query),
            flow { emit(deviceAppLookup.query(query)) },
        ) { autocompleteResult, appsResult ->
            if (autocompleteResult.suggestions.contains(AutoCompleteInAppMessageSuggestion)) {
                hasUserSeenHistory = true
            }
            SystemSearchResult(autocompleteResult, appsResult)
        }
    }

    fun onOmnibarConfigured(position: OmnibarPosition) {
        omnibarPosition = position
    }

    val hasOmnibarPositionChanged: Boolean
        get() = omnibarPosition != appSettingsPreferencesStore.omnibarPosition

    fun userTappedOnboardingToggle() {
        onboardingViewState.value = currentOnboardingState().copy(expanded = !currentOnboardingState().expanded)
        if (currentOnboardingState().expanded) {
            pixel.fire(INTERSTITIAL_ONBOARDING_MORE_PRESSED)
            command.value = Command.DismissKeyboard
        } else {
            pixel.fire(INTERSTITIAL_ONBOARDING_LESS_PRESSED)
        }
    }

    fun userDismissedOnboarding() {
        viewModelScope.launch {
            onboardingViewState.value = currentOnboardingState().copy(visible = false)
            userStageStore.stageCompleted(AppStage.NEW)
            pixel.fire(INTERSTITIAL_ONBOARDING_DISMISSED)
        }
    }

    fun onUserSelectedToEditQuery(query: String) {
        command.value = Command.EditQuery(query)
    }

    fun onVoiceSearchStateChanged() {
        voiceSearchState.tryEmit(Unit)
    }

    fun onDuckAiRequested(query: String) {
        duckChat.openDuckChatWithAutoPrompt(query)
        command.value = Command.ExitSearch
    }

    fun userUpdatedQuery(query: String) {
        if (autoCompleteSettings.autoCompleteSuggestionsEnabled) {
            queryFlow.update { query }
        }
    }

    private fun updateResults(results: SystemSearchResult): Suggestions.SystemSearchResultsViewState {
        val suggestions = results.autocomplete.suggestions
        val appResults = results.deviceApps
        val hasMultiResults = suggestions.isNotEmpty() && appResults.isNotEmpty()

        val updatedSuggestions = if (hasMultiResults) suggestions.take(RESULTS_MAX_RESULTS_PER_GROUP) else suggestions
        val updatedApps = if (hasMultiResults) appResults.take(RESULTS_MAX_RESULTS_PER_GROUP) else appResults
        return Suggestions.SystemSearchResultsViewState(
            autocompleteResults = AutoCompleteResult(results.autocomplete.query, updatedSuggestions),
            appResults = updatedApps,
        )
    }

    private fun inputCleared() {
        queryFlow.update { "" }
    }

    fun userTappedDax() {
        viewModelScope.launch {
            userStageStore.stageCompleted(AppStage.NEW)
            pixel.fire(INTERSTITIAL_LAUNCH_DAX)
            command.value = Command.LaunchDuckDuckGo
        }
    }

    fun userRequestedClear() {
        command.value = Command.ClearInputText
        inputCleared()
    }

    fun userSubmittedQuery(query: String) {
        if (query.isBlank()) {
            return
        }

        viewModelScope.launch {
            userStageStore.stageCompleted(AppStage.NEW)
            command.value = Command.LaunchBrowser(query.trim())
            pixel.fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
        }
    }

    fun userSubmittedAutocompleteResult(suggestion: AutoCompleteSuggestion) {
        when (suggestion) {
            is AutoCompleteSwitchToTabSuggestion -> {
                command.value = Command.LaunchBrowserAndSwitchToTab(suggestion.phrase, suggestion.tabId)
            }
            is AutoCompleteSuggestion.AutoCompleteDuckAIPrompt -> {
                onDuckAiRequested(suggestion.phrase)
            }
            else -> {
                command.value = Command.LaunchBrowser(suggestion.phrase)
            }
        }
        pixel.fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    fun userLongPressedAutocomplete(suggestion: AutoCompleteSuggestion) {
        when (suggestion) {
            is AutoCompleteHistorySuggestion, is AutoCompleteHistorySearchSuggestion -> showRemoveSearchSuggestionDialog(suggestion)
            else -> return
        }
    }

    private fun showRemoveSearchSuggestionDialog(suggestion: AutoCompleteSuggestion) {
        appCoroutineScope.launch(dispatchers.main()) {
            command.value = Command.ShowRemoveSearchSuggestionDialog(suggestion)
        }
    }

    fun onRemoveSearchSuggestionConfirmed(suggestion: AutoCompleteSuggestion, omnibarText: String) {
        appCoroutineScope.launch(dispatchers.io()) {
            pixel.fire(AUTOCOMPLETE_RESULT_DELETED)
            pixel.fire(AUTOCOMPLETE_RESULT_DELETED_DAILY, type = Daily())

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
                queryFlow.value = omnibarText
                refreshTrigger.tryEmit(Unit)
                command.value = Command.AutocompleteItemRemoved
            }
        }
    }

    fun userSelectedApp(app: DeviceApp) {
        command.value = Command.LaunchDeviceApplication(app)
        pixel.fire(INTERSTITIAL_LAUNCH_DEVICE_APP)
    }

    fun appNotFound(app: DeviceApp) {
        command.value = Command.ShowAppNotFoundMessage(app.shortName)

        refreshAppList()
    }

    private fun refreshAppList() {
        viewModelScope.launch(dispatchers.io()) {
            deviceAppLookup.refreshAppList()
        }
    }

    fun onQuickAccessListChanged(newList: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateWithPosition(newList.map { it.favorite })
        }
    }

    fun onQuickAccessItemClicked(it: FavoritesQuickAccessAdapter.QuickAccessFavorite) {
        pixel.fire(FAVORITE_SYSTEM_SEARCH_ITEM_PRESSED)
        command.value = Command.LaunchBrowser(it.favorite.url)
    }

    fun onEditQuickAccessItemRequested(it: FavoritesQuickAccessAdapter.QuickAccessFavorite) {
        command.value = Command.LaunchEditDialog(it.favorite)
    }

    fun onDeleteQuickAccessItemRequested(it: FavoritesQuickAccessAdapter.QuickAccessFavorite) {
        hideQuickAccessItem(it)
        command.value = Command.DeleteFavoriteConfirmation(it.favorite)
    }

    fun onDeleteSavedSiteRequested(it: FavoritesQuickAccessAdapter.QuickAccessFavorite) {
        hideQuickAccessItem(it)
        command.value = Command.DeleteSavedSiteConfirmation(it.favorite)
    }

    companion object {
        private const val DEBOUNCE_TIME_MS = 200L
        private const val RESULTS_MAX_RESULTS_PER_GROUP = 4
    }

    override fun onFavouriteEdited(favorite: Favorite) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateFavourite(favorite)
        }
    }

    override fun onBookmarkEdited(
        bookmark: Bookmark,
        oldFolderId: String,
        updateFavorite: Boolean,
    ) {
        viewModelScope.launch(dispatchers.io()) {
            savedSitesRepository.updateBookmark(bookmark, oldFolderId, updateFavorite)
        }
    }

    override fun onFavoriteAdded() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED_DAILY, type = Daily())
    }

    override fun onFavoriteRemoved() {
        pixel.fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    fun deleteFavoriteSnackbarDismissed(savedSite: SavedSite) {
        when (savedSite) {
            is SavedSite.Favorite -> {
                appCoroutineScope.launch(dispatchers.io()) {
                    savedSitesRepository.delete(savedSite)
                }
            }

            else -> throw IllegalArgumentException("Illegal SavedSite to delete received")
        }
    }

    fun deleteSavedSiteSnackbarDismissed(savedSite: SavedSite) {
        appCoroutineScope.launch(dispatchers.io()) {
            savedSitesRepository.delete(savedSite, true)
        }
    }

    private fun hideQuickAccessItem(quickAccessFavourite: FavoritesQuickAccessAdapter.QuickAccessFavorite) {
        viewModelScope.launch(dispatchers.io()) {
            hiddenIds.emit(hiddenIds.value.copy(favorites = hiddenIds.value.favorites + quickAccessFavourite.favorite.id))
        }
    }

    fun undoDelete(savedSite: SavedSite) {
        viewModelScope.launch(dispatchers.io()) {
            hiddenIds.emit(
                hiddenIds.value.copy(
                    favorites = hiddenIds.value.favorites - savedSite.id,
                ),
            )
        }
    }

    fun onUserDismissedAutoCompleteInAppMessage() {
        viewModelScope.launch(dispatchers.io()) {
            autoComplete.userDismissedHistoryInAutoCompleteIAM()
        }
    }

    fun autoCompleteSuggestionsGone() {
        viewModelScope.launch(dispatchers.io()) {
            if (hasUserSeenHistory) {
                autoComplete.submitUserSeenHistoryIAM()
            }
            hasUserSeenHistory = false
        }
    }
}
