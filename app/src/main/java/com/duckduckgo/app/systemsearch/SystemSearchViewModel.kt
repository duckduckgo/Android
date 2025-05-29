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
import com.duckduckgo.app.autocomplete.api.AutoComplete
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteInAppMessageSuggestion
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter
import com.duckduckgo.app.di.AppCoroutineScope
import com.duckduckgo.app.onboarding.store.AppStage
import com.duckduckgo.app.onboarding.store.UserStageStore
import com.duckduckgo.app.onboarding.store.isNewUser
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.UpdateVoiceSearch
import com.duckduckgo.common.utils.ConflatedJob
import com.duckduckgo.common.utils.DispatcherProvider
import com.duckduckgo.common.utils.SingleLiveEvent
import com.duckduckgo.di.scopes.ActivityScope
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite
import com.duckduckgo.savedsites.api.models.SavedSite.Bookmark
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.savedsites.impl.dialogs.EditSavedSiteDialogFragment
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
    private var userStageStore: UserStageStore,
    private val autoComplete: AutoComplete,
    private val deviceAppLookup: DeviceAppLookup,
    private val pixel: Pixel,
    private val savedSitesRepository: SavedSitesRepository,
    private val appSettingsPreferencesStore: SettingsDataStore,
    private val history: NavigationHistory,
    private val dispatchers: DispatcherProvider,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : ViewModel(), EditSavedSiteDialogFragment.EditSavedSiteListener {

    data class OnboardingViewState(
        val visible: Boolean,
        val expanded: Boolean = false,
    )

    sealed class Suggestions {
        data class SystemSearchResultsViewState(
            val autocompleteResults: AutoCompleteResult = AutoCompleteResult("", emptyList()),
            val appResults: List<DeviceApp> = emptyList(),
        ) : Suggestions()

        data class QuickAccessItems(val favorites: List<FavoritesQuickAccessAdapter.QuickAccessFavorite>) : Suggestions()
    }

    sealed class Command {
        object ClearInputText : Command()
        object LaunchDuckDuckGo : Command()
        data class LaunchBrowser(val query: String) : Command()
        data class LaunchBrowserAndSwitchToTab(val query: String, val tabId: String) : Command()
        data class LaunchEditDialog(val savedSite: SavedSite) : Command()
        data class DeleteFavoriteConfirmation(val savedSite: SavedSite) : Command()
        data class DeleteSavedSiteConfirmation(val savedSite: SavedSite) : Command()
        data class LaunchDeviceApplication(val deviceApp: DeviceApp) : Command()
        data class ShowAppNotFoundMessage(val appName: String) : Command()
        object DismissKeyboard : Command()
        data class EditQuery(val query: String) : Command()
        object UpdateVoiceSearch : Command()
        data class ShowRemoveSearchSuggestionDialog(val suggestion: AutoCompleteSuggestion) : Command()
        data object AutocompleteItemRemoved : Command()
    }

    val onboardingViewState: MutableLiveData<OnboardingViewState> = MutableLiveData()
    val resultsViewState: MutableLiveData<Suggestions> = MutableLiveData()
    val command: SingleLiveEvent<Command> = SingleLiveEvent()

    @VisibleForTesting
    internal val resultsStateFlow = MutableStateFlow("")
    private var results = SystemSearchResult(AutoCompleteResult("", emptyList()), emptyList())
    private var resultsJob = ConflatedJob()
    private var latestQuickAccessItems: Suggestions.QuickAccessItems = Suggestions.QuickAccessItems(emptyList())
    private var hasUserSeenHistory = false

    val hiddenIds = MutableStateFlow(HiddenBookmarksIds())

    data class HiddenBookmarksIds(val favorites: List<String> = emptyList())

    private var appsJob: Job? = null

    init {
        resetViewState()
        configureResults()
        refreshAppList()

        savedSitesRepository.getFavorites()
            .combine(hiddenIds) { favorites, hiddenIds ->
                favorites.filter { it.id !in hiddenIds.favorites }
            }
            .flowOn(dispatchers.io())
            .onEach { filteredFavourites ->
                withContext(dispatchers.main()) {
                    latestQuickAccessItems =
                        Suggestions.QuickAccessItems(filteredFavourites.map { FavoritesQuickAccessAdapter.QuickAccessFavorite(it) })
                    resultsViewState.postValue(latestQuickAccessItems)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun currentOnboardingState(): OnboardingViewState = onboardingViewState.value!!
    private fun currentResultsState(): Suggestions = resultsViewState.value!!

    fun resetViewState() {
        command.value = Command.ClearInputText
        viewModelScope.launch {
            resetOnboardingState()
        }
        resetResultsState()
    }

    private suspend fun resetOnboardingState() {
        val showOnboarding = userStageStore.isNewUser()
        onboardingViewState.value = OnboardingViewState(visible = showOnboarding)
        if (showOnboarding) {
            pixel.fire(INTERSTITIAL_ONBOARDING_SHOWN)
        }
    }

    private fun resetResultsState() {
        results = SystemSearchResult(AutoCompleteResult("", emptyList()), emptyList())
        appsJob?.cancel()
        resultsViewState.value = latestQuickAccessItems
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun configureResults() {
        resultsJob += resultsStateFlow
            .debounce(DEBOUNCE_TIME_MS)
            .distinctUntilChanged()
            .flatMapLatest { buildResultsFlow(query = it) }
            .flowOn(dispatchers.io())
            .onEach { result ->
                updateResults(result)
            }
            .flowOn(dispatchers.main())
            .catch { t: Throwable? -> logcat(WARN) { "Failed to get search results: ${t?.asLog()}" } }
            .launchIn(viewModelScope)
    }

    private fun buildResultsFlow(query: String): Flow<SystemSearchResult> {
        return combine(
            autoComplete.autoComplete(query),
            flow { emit(deviceAppLookup.query(query)) },
        ) { autocompleteResult: AutoCompleteResult, appsResult: List<DeviceApp> ->
            if (autocompleteResult.suggestions.contains(AutoCompleteInAppMessageSuggestion)) {
                hasUserSeenHistory = true
            }
            SystemSearchResult(autocompleteResult, appsResult)
        }
    }

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

    fun userUpdatedQuery(query: String) {
        appsJob?.cancel()

        if (query.isBlank()) {
            inputCleared()
            return
        }

        if (appSettingsPreferencesStore.autoCompleteSuggestionsEnabled) {
            val trimmedQuery = query.trim()
            resultsStateFlow.value = trimmedQuery
        }
    }

    private fun updateResults(results: SystemSearchResult) {
        this.results = results

        val suggestions = results.autocomplete.suggestions
        val appResults = results.deviceApps
        val hasMultiResults = suggestions.isNotEmpty() && appResults.isNotEmpty()

        val updatedSuggestions = if (hasMultiResults) suggestions.take(RESULTS_MAX_RESULTS_PER_GROUP) else suggestions
        val updatedApps = if (hasMultiResults) appResults.take(RESULTS_MAX_RESULTS_PER_GROUP) else appResults
        resultsViewState.postValue(
            when (val currentResultsState = currentResultsState()) {
                is Suggestions.SystemSearchResultsViewState -> {
                    currentResultsState.copy(
                        autocompleteResults = AutoCompleteResult(results.autocomplete.query, updatedSuggestions),
                        appResults = updatedApps,
                    )
                }

                is Suggestions.QuickAccessItems -> {
                    Suggestions.SystemSearchResultsViewState(
                        autocompleteResults = AutoCompleteResult(results.autocomplete.query, updatedSuggestions),
                        appResults = updatedApps,
                    )
                }
            },
        )
    }

    private fun inputCleared() {
        if (appSettingsPreferencesStore.autoCompleteSuggestionsEnabled) {
            resultsStateFlow.value = ""
        }
        resetResultsState()
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
                resultsStateFlow.value = omnibarText
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

    override fun onCleared() {
        resultsJob.cancel()
        super.onCleared()
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

    fun voiceSearchDisabled() {
        command.value = UpdateVoiceSearch
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
