/*
 * Copyright (c) 2022 DuckDuckGo
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

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import app.cash.turbine.test
import com.duckduckgo.app.browser.newtab.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Daily
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.AutocompleteItemRemoved
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.LaunchDuckDuckGo
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.ShowRemoveSearchSuggestionDialog
import com.duckduckgo.browser.api.autocomplete.AutoComplete
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteResult
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteDefaultSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteHistoryRelatedSuggestion.AutoCompleteHistorySuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoComplete.AutoCompleteSuggestion.AutoCompleteUrlSuggestion.AutoCompleteSwitchToTabSuggestion
import com.duckduckgo.browser.api.autocomplete.AutoCompleteSettings
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.common.test.InstantSchedulersRule
import com.duckduckgo.duckchat.api.DuckAiFeatureState
import com.duckduckgo.duckchat.api.DuckChat
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.savedsites.api.SavedSitesRepository
import com.duckduckgo.savedsites.api.models.SavedSite.Favorite
import com.duckduckgo.savedsites.impl.SavedSitesPixelName
import com.duckduckgo.voice.api.VoiceSearchAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import kotlinx.coroutines.test.runTest
import org.junit.*
import org.junit.Assert.*
import org.mockito.Mockito.verify
import org.mockito.internal.util.DefaultMockingDetails
import org.mockito.kotlin.*

class SystemSearchViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockUserStageStore: UserStageStore = mock()
    private val mockDeviceAppLookup: DeviceAppLookup = mock()
    private val mockAutoComplete: AutoComplete = mock()
    private val mocksavedSitesRepository: SavedSitesRepository = mock()
    private val mockPixel: Pixel = mock()
    private val mockSettingsStore: SettingsDataStore = mock()
    private val mockAutoCompleteSettings: AutoCompleteSettings = mock()
    private val mockHistory: NavigationHistory = mock()
    private val mockDuckChat: DuckChat = mock()
    private val mockDuckAiFeatureState: DuckAiFeatureState = mock()
    private val mockVoiceSearchAvailability: VoiceSearchAvailability = mock()

    private val commandObserver: Observer<Command> = mock()
    private val commandCaptor = argumentCaptor<Command>()

    private lateinit var testee: SystemSearchViewModel

    @Before
    fun setup() = runTest {
        whenever(mockAutoComplete.autoComplete(QUERY)).thenReturn(flowOf(autocompleteQueryResult))
        whenever(mockAutoComplete.autoComplete(BLANK_QUERY)).thenReturn(flowOf(autocompleteBlankResult))
        whenever(mockDeviceAppLookup.query(QUERY)).thenReturn(appQueryResult)
        whenever(mockDeviceAppLookup.query(BLANK_QUERY)).thenReturn(appBlankResult)
        whenever(mocksavedSitesRepository.getFavorites()).thenReturn(flowOf(emptyList())) // Ensure initial favorites is empty for most tests
        doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(false)
        whenever(mockDuckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus).thenReturn(MutableStateFlow(false))

        testee = SystemSearchViewModel(
            mockDuckAiFeatureState,
            mockVoiceSearchAvailability,
            mockDuckChat,
            mockUserStageStore,
            mockAutoComplete,
            mockDeviceAppLookup,
            mockPixel,
            mocksavedSitesRepository,
            mockSettingsStore,
            mockAutoCompleteSettings,
            mockHistory,
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )
        testee.command.observeForever(commandObserver)
    }

    @After
    fun tearDown() {
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenOnboardingShouldNotShowThenViewIsNotVisibleAndUnexpanded() = runTest {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.resetViewState()

        val viewState = testee.onboardingViewState.value
        assertFalse(viewState!!.visible)
        assertFalse(viewState.expanded)
    }

    @Test
    fun whenOnboardingShouldShowThenViewIsVisibleAndUnexpanded() = runTest {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.resetViewState()

        val viewState = testee.onboardingViewState.value
        assertTrue(viewState!!.visible)
        assertFalse(viewState.expanded)
    }

    @Test
    fun whenOnboardingShownThenPixelSent() = runTest {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.resetViewState()
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_SHOWN)
    }

    @Test
    fun whenOnboardingIsUnexpandedAndUserPressesToggleThenItIsExpandedAndPixelSent() = runTest {
        whenOnboardingShowing()
        testee.userTappedOnboardingToggle()

        val viewState = testee.onboardingViewState.value
        assertTrue(viewState!!.expanded)
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_MORE_PRESSED)
    }

    @Test
    fun whenOnboardingIsExpandedAndUserPressesToggleThenItIsUnexpandedAndPixelSent() = runTest {
        whenOnboardingShowing()
        testee.userTappedOnboardingToggle() // first press to expand
        testee.userTappedOnboardingToggle() // second press to minimize

        val viewState = testee.onboardingViewState.value
        assertFalse(viewState!!.expanded)
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_LESS_PRESSED)
    }

    @Test
    fun whenOnboardingIsDismissedThenViewHiddenPixelSentAndOnboardingStoreNotified() = runTest {
        whenOnboardingShowing()
        testee.userDismissedOnboarding()

        val viewState = testee.onboardingViewState.value
        assertFalse(viewState!!.visible)
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_DISMISSED)
        verify(mockUserStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenUserUpdatesQueryThenViewStateUpdated() = runTest {
        testee.suggestionsViewState.test {
            testee.userUpdatedQuery(QUERY)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            val newViewState = expectMostRecentItem()
            assertNotNull(newViewState)
            assertEquals(appQueryResult, newViewState.appResults)
            assertEquals(autocompleteQueryResult, newViewState.autocompleteResults)
        }
    }

    @Test
    fun whenUserAddsSpaceToQueryThenViewStateMatchesAndSpaceTrimmedFromAutocomplete() = runTest {
        testee.suggestionsViewState.test {
            // initial default emission
            awaitItem()
            testee.userUpdatedQuery("$QUERY ")
            // account for debounce
            coroutineRule.testDispatcher.scheduler.advanceTimeBy(300)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            val newViewState = awaitItem()
            assertNotNull(newViewState)
            assertEquals(appQueryResult, newViewState.appResults)
            assertEquals(autocompleteQueryResult, newViewState.autocompleteResults)
        }
    }

    @Test
    fun whenUsersUpdatesWithAutoCompleteEnabledThenAutoCompleteSuggestionsIsNotEmpty() = runTest {
        testee.suggestionsViewState.test {
            doReturn(true).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
            testee.userUpdatedQuery(QUERY)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            val newViewState = expectMostRecentItem()
            assertNotNull(newViewState)
            assertEquals(appQueryResult, newViewState.appResults)
            assertEquals(autocompleteQueryResult, newViewState.autocompleteResults)
        }
    }

    @Test
    fun whenUsersUpdatesWithAutoCompleteDisabledThenViewStateReset() = runTest {
        testee.suggestionsViewState.test {
            doReturn(false).whenever(mockAutoCompleteSettings).autoCompleteSuggestionsEnabled
            testee.userUpdatedQuery(QUERY)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().autocompleteResults.suggestions.isEmpty())
        }
    }

    @Test
    fun whenUserClearsQueryThenViewStateReset() = runTest {
        testee.suggestionsViewState.test {
            testee.userUpdatedQuery(QUERY)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            expectMostRecentItem() // Initial state after query

            testee.userRequestedClear()
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().autocompleteResults.suggestions.isEmpty())
        }
    }

    @Test
    fun whenUsersUpdatesWithBlankQueryThenViewStateReset() = runTest {
        testee.suggestionsViewState.test {
            testee.userUpdatedQuery(QUERY)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            expectMostRecentItem() // Initial state after query

            testee.userUpdatedQuery(BLANK_QUERY)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().autocompleteResults.suggestions.isEmpty())
        }
    }

    @Test
    fun whenUserSubmitsQueryThenBrowserLaunchedWithQueryAndPixelSent() = runTest {
        testee.userSubmittedQuery(QUERY)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(QUERY), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsQueryWithSpaceThenBrowserLaunchedWithTrimmedQueryAndPixelSent() = runTest {
        testee.userSubmittedQuery("$QUERY ")
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(QUERY), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsBlankQueryThenIgnored() = runTest {
        testee.userSubmittedQuery(BLANK_QUERY)
        assertFalse(commandCaptor.allValues.any { it is Command.LaunchBrowser })
        verify(mockPixel, never()).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsQueryThenOnboardingCompleted() = runTest {
        testee.userSubmittedQuery(QUERY)
        verify(mockUserStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenUserSubmitsAutocompleteResultThenBrowserLaunchedAndPixelSent() = runTest {
        testee.userSubmittedAutocompleteResult(AutoCompleteSearchSuggestion(phrase = AUTOCOMPLETE_RESULT, isUrl = false, isAllowedInTopHits = false))
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(AUTOCOMPLETE_RESULT), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsAutocompleteResultToOpenInTabThenBrowserLaunchedAndPixelSent() = runTest {
        val phrase = "phrase"
        val tabId = "tabId"

        testee.userSubmittedAutocompleteResult(AutoCompleteSwitchToTabSuggestion(phrase, "title", "https://example.com", tabId))

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowserAndSwitchToTab(phrase, tabId), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSelectsAppResultThenAppLaunchedAndPixelSent() {
        testee.userSelectedApp(deviceApp)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchDeviceApplication(deviceApp), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_DEVICE_APP)
    }

    @Test
    fun whenUserTapsDaxThenAppLaunchedAndPixelSent() {
        testee.userTappedDax()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is LaunchDuckDuckGo)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_DAX)
    }

    @Test
    fun whenUserTapsDaxThenOnboardingCompleted() = runTest {
        testee.userTappedDax()
        verify(mockUserStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenViewModelCreatedThenAppsRefreshed() = runTest {
        verify(mockDeviceAppLookup).refreshAppList()
    }

    @Test
    fun whenUserSelectsAppThatCannotBeFoundThenAppsRefreshedAndUserMessageShown() = runTest {
        testee.appNotFound(deviceApp)
        verify(mockDeviceAppLookup, times(2)).refreshAppList()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowAppNotFoundMessage(deviceApp.shortName), commandCaptor.lastValue)
    }

    @Test
    fun whenUserSelectedToUpdateQueryThenEditQueryCommandSent() {
        val query = "test"
        testee.onUserSelectedToEditQuery(query)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.EditQuery(query), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessItemClickedThenLaunchBrowser() {
        val quickAccessItem = QuickAccessFavorite(Favorite("favorite1", "title", "http://example.com", "timestamp", 0))

        testee.onQuickAccessItemClicked(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(quickAccessItem.favorite.url), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessItemClickedThenPixelSent() {
        val quickAccessItem = QuickAccessFavorite(Favorite("favorite1", "title", "http://example.com", "timestamp", 0))

        testee.onQuickAccessItemClicked(quickAccessItem)

        verify(mockPixel).fire(FAVORITE_SYSTEM_SEARCH_ITEM_PRESSED)
    }

    @Test
    fun whenQuickAccessItemEditRequestedThenLaunchEditDialog() {
        val quickAccessItem = QuickAccessFavorite(Favorite("favorite1", "title", "http://example.com", "timestamp", 0))

        testee.onEditQuickAccessItemRequested(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchEditDialog(quickAccessItem.favorite), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessItemDeleteRequestedThenShowDeleteFavoriteConfirmation() {
        val quickAccessItem = QuickAccessFavorite(Favorite("favorite1", "title", "http://example.com", "timestamp", 0))

        testee.onDeleteQuickAccessItemRequested(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.DeleteFavoriteConfirmation(quickAccessItem.favorite), commandCaptor.lastValue)
    }

    @Test
    fun whenSavedSiteDeleteRequestedThenShowDeleteSavedSiteConfirmation() {
        val quickAccessItem = QuickAccessFavorite(Favorite("favorite1", "title", "http://example.com", "timestamp", 0))

        testee.onDeleteSavedSiteRequested(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.DeleteSavedSiteConfirmation(quickAccessItem.favorite), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessEditedThenRepositoryUpdated() {
        val savedSite = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)

        testee.onFavouriteEdited(savedSite)

        verify(mocksavedSitesRepository).updateFavourite(savedSite)
    }

    @Test
    fun whenQuickAccessDeleteRequestedThenFavouriteDeletedFromViewState() = runTest {
        val savedSite = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)
        whenever(mocksavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(savedSite)))
        // Re-initialize testee to pick up the new mock value for getFavorites()
        testee = SystemSearchViewModel(
            mockDuckAiFeatureState,
            mockVoiceSearchAvailability,
            mockDuckChat,
            mockUserStageStore,
            mockAutoComplete,
            mockDeviceAppLookup,
            mockPixel,
            mocksavedSitesRepository,
            mockSettingsStore,
            mockAutoCompleteSettings,
            mockHistory,
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )
        testee.command.observeForever(commandObserver) // Re-observe commands after re-initialization

        testee.favoritesViewState.test {
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle() // Ensure initial state is processed
            var viewState = expectMostRecentItem()
            assertFalse(viewState.favorites.isEmpty())

            testee.onDeleteQuickAccessItemRequested(QuickAccessFavorite(savedSite))
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            viewState = expectMostRecentItem()
            assertTrue(viewState.favorites.isEmpty())
        }
    }

    @Test
    fun whenQuickAccessDeleteUndoThenViewStateUpdated() = runTest {
        val savedSite = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)
        whenever(mocksavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(savedSite)))
        // Re-initialize testee to pick up the new mock value for getFavorites()
        testee = SystemSearchViewModel(
            mockDuckAiFeatureState,
            mockVoiceSearchAvailability,
            mockDuckChat,
            mockUserStageStore,
            mockAutoComplete,
            mockDeviceAppLookup,
            mockPixel,
            mocksavedSitesRepository,
            mockSettingsStore,
            mockAutoCompleteSettings,
            mockHistory,
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )
        testee.command.observeForever(commandObserver) // Re-observe commands after re-initialization

        testee.favoritesViewState.test {
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle() // Ensure initial state is processed
            var viewState = expectMostRecentItem()
            assertFalse(viewState.favorites.isEmpty()) // Initial state check

            // Simulate deletion
            testee.onDeleteQuickAccessItemRequested(QuickAccessFavorite(savedSite))
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            viewState = expectMostRecentItem()
            assertTrue(viewState.favorites.isEmpty()) // State after deletion

            testee.undoDelete(savedSite)
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            viewState = expectMostRecentItem()
            assertFalse(viewState.favorites.isEmpty()) // State after undo
        }
    }

    @Test
    fun whenQuickAccessDeletedThenRepositoryDeletesFavorite() = runTest {
        val savedSite = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)

        testee.deleteFavoriteSnackbarDismissed(savedSite)

        verify(mocksavedSitesRepository).delete(savedSite)
    }

    @Test
    fun whenAssociatedBookmarkDeletedThenRepositoryDeletesBookmark() = runTest {
        val savedSite = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)

        testee.deleteSavedSiteSnackbarDismissed(savedSite)

        verify(mocksavedSitesRepository).delete(savedSite, true)
    }

    @Test
    fun whenQuickAccessListChangedThenRepositoryUpdated() {
        val savedSite = Favorite("favorute1", "title", "http://example.com", "timestamp", 0)
        val savedSites = listOf(QuickAccessFavorite(savedSite))

        testee.onQuickAccessListChanged(savedSites)

        verify(mocksavedSitesRepository).updateWithPosition(listOf(savedSite))
    }

    @Test
    fun whenUserHasFavoritesThenInitialStateShowsFavorites() = runTest {
        val savedSite = Favorite("favorite1", "title", "http://example.com", "timestamp", 0)
        whenever(mocksavedSitesRepository.getFavorites()).thenReturn(flowOf(listOf(savedSite)))
        // Re-initialize testee to pick up the new mock value for getFavorites()
        testee = SystemSearchViewModel(
            mockDuckAiFeatureState,
            mockVoiceSearchAvailability,
            mockDuckChat,
            mockUserStageStore,
            mockAutoComplete,
            mockDeviceAppLookup,
            mockPixel,
            mocksavedSitesRepository,
            mockSettingsStore,
            mockAutoCompleteSettings,
            mockHistory,
            coroutineRule.testDispatcherProvider,
            coroutineRule.testScope,
        )
        testee.command.observeForever(commandObserver) // Re-observe commands after re-initialization

        testee.favoritesViewState.test {
            coroutineRule.testDispatcher.scheduler.advanceUntilIdle()
            val viewState = expectMostRecentItem()
            assertEquals(1, viewState.favorites.size)
            assertEquals(savedSite, viewState.favorites.first().favorite)
        }
    }

    @Test
    fun whenOnFavoriteAddedThenPixelFired() {
        testee.onFavoriteAdded()

        verify(mockPixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_ADD_FAVORITE_TOGGLED)
    }

    @Test
    fun whenOnFavoriteRemovedThenPixelFired() {
        testee.onFavoriteRemoved()

        verify(mockPixel).fire(SavedSitesPixelName.EDIT_BOOKMARK_REMOVE_FAVORITE_TOGGLED)
    }

    @Test
    fun whenUserLongPressedOnHistorySuggestionThenShowRemoveSearchSuggestionDialogCommandIssued() {
        val suggestion = AutoCompleteHistorySuggestion(phrase = "phrase", title = "title", url = "url", isAllowedInTopHits = false)

        testee.userLongPressedAutocomplete(suggestion)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is ShowRemoveSearchSuggestionDialog }
        assertEquals(suggestion, (issuedCommand as ShowRemoveSearchSuggestionDialog).suggestion)
    }

    @Test
    fun whenUserLongPressedOnHistorySearchSuggestionThenShowRemoveSearchSuggestionDialogCommandIssued() {
        val suggestion = AutoCompleteHistorySearchSuggestion(phrase = "phrase", isAllowedInTopHits = false)

        testee.userLongPressedAutocomplete(suggestion)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is ShowRemoveSearchSuggestionDialog }
        assertEquals(suggestion, (issuedCommand as ShowRemoveSearchSuggestionDialog).suggestion)
    }

    @Test
    fun whenUserLongPressedOnOtherSuggestionThenDoNothing() {
        val suggestion = AutoCompleteDefaultSuggestion(phrase = "phrase")

        testee.userLongPressedAutocomplete(suggestion)

        assertCommandNotIssued<ShowRemoveSearchSuggestionDialog>()
    }

    @Test
    fun whenOnRemoveSearchSuggestionConfirmedForHistorySuggestionThenPixelsFiredAndHistoryEntryRemoved() = runBlocking {
        val suggestion = AutoCompleteHistorySuggestion(phrase = "phrase", title = "title", url = "url", isAllowedInTopHits = false)
        val omnibarText = "foo"

        testee.onRemoveSearchSuggestionConfirmed(suggestion, omnibarText)

        verify(mockPixel).fire(AUTOCOMPLETE_RESULT_DELETED)
        verify(mockPixel).fire(AUTOCOMPLETE_RESULT_DELETED_DAILY, type = Daily())
        verify(mockHistory).removeHistoryEntryByUrl(suggestion.url)
        assertCommandIssued<AutocompleteItemRemoved>()
    }

    @Test
    fun whenOnRemoveSearchSuggestionConfirmedForHistorySearchSuggestionThenPixelsFiredAndHistoryEntryRemoved() = runBlocking {
        val suggestion = AutoCompleteHistorySearchSuggestion(phrase = "phrase", isAllowedInTopHits = false)
        val omnibarText = "foo"

        testee.onRemoveSearchSuggestionConfirmed(suggestion, omnibarText)

        verify(mockPixel).fire(AUTOCOMPLETE_RESULT_DELETED)
        verify(mockPixel).fire(AUTOCOMPLETE_RESULT_DELETED_DAILY, type = Daily())
        verify(mockHistory).removeHistoryEntryByQuery(suggestion.phrase)
        assertCommandIssued<AutocompleteItemRemoved>()
    }

    @Test
    fun onDuckAiTappedThenDuckChatOpenedWithQuery() {
        val query = "What is DuckDuckGo?"
        testee.onDuckAiRequested(query)
        verify(mockDuckChat).openDuckChatWithAutoPrompt(query)
    }

    @Test
    fun whenQueryIsEmptyAndVoiceSearchDisabledAndDuckAiDisabledThenOmnibarViewStateIsCorrect() = runTest {
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(false)
        (mockDuckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus as MutableStateFlow).value = false
        testee.queryFlow.value = ""
        testee.omnibarViewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.isVoiceSearchButtonVisible)
            assertFalse(viewState.isDuckAiButtonVisible)
            assertFalse(viewState.isClearButtonVisible)
            assertFalse(viewState.isButtonDividerVisible)
        }
    }

    @Test
    fun whenQueryIsNotEmptyAndVoiceSearchEnabledAndDuckAiEnabledThenOmnibarViewStateIsCorrect() = runTest {
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)
        (mockDuckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus as MutableStateFlow).value = true
        testee.queryFlow.value = "query"
        testee.omnibarViewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.isVoiceSearchButtonVisible)
            assertTrue(viewState.isDuckAiButtonVisible)
            assertTrue(viewState.isClearButtonVisible)
            assertTrue(viewState.isButtonDividerVisible)
        }
    }

    @Test
    fun whenQueryIsNotEmptyAndVoiceSearchDisabledAndDuckAiEnabledThenOmnibarViewStateIsCorrect() = runTest {
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(false)
        (mockDuckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus as MutableStateFlow).value = true
        testee.queryFlow.value = "query"
        testee.omnibarViewState.test {
            val viewState = awaitItem()
            assertFalse(viewState.isVoiceSearchButtonVisible)
            assertTrue(viewState.isDuckAiButtonVisible)
            assertTrue(viewState.isClearButtonVisible)
            assertTrue(viewState.isButtonDividerVisible)
        }
    }

    @Test
    fun whenQueryIsEmptyAndVoiceSearchEnabledAndDuckAiEnabledThenOmnibarViewStateIsCorrect() = runTest {
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)
        (mockDuckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus as MutableStateFlow).value = true
        testee.queryFlow.value = ""
        testee.omnibarViewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.isVoiceSearchButtonVisible)
            assertTrue(viewState.isDuckAiButtonVisible)
            assertFalse(viewState.isClearButtonVisible)
            assertTrue(viewState.isButtonDividerVisible)
        }
    }

    @Test
    fun whenQueryIsNotEmptyAndVoiceSearchEnabledAndDuckAiDisabledThenOmnibarViewStateIsCorrect() = runTest {
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)
        (mockDuckAiFeatureState.showOmnibarShortcutOnNtpAndOnFocus as MutableStateFlow).value = false
        testee.queryFlow.value = "query"
        testee.omnibarViewState.test {
            val viewState = awaitItem()
            assertTrue(viewState.isVoiceSearchButtonVisible)
            assertFalse(viewState.isDuckAiButtonVisible)
            assertTrue(viewState.isClearButtonVisible)
            assertFalse(viewState.isButtonDividerVisible)
        }
    }

    private suspend fun whenOnboardingShowing() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.resetViewState()
    }

    private inline fun <reified T : Command> assertCommandIssued(instanceAssertions: T.() -> Unit = {}) {
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        val issuedCommand = commandCaptor.allValues.find { it is T }
        assertNotNull("Command of type ${'$'}{T::class.java.simpleName} not issued. All commands: ${'$'}{commandCaptor.allValues}", issuedCommand)
        (issuedCommand as T).apply { instanceAssertions() }
    }

    private inline fun <reified T : Command> assertCommandNotIssued() {
        val defaultMockingDetails = DefaultMockingDetails(commandObserver)
        if (defaultMockingDetails.invocations.isNotEmpty()) {
            verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
            val issuedCommand = commandCaptor.allValues.find { it is T }
            assertNull(
                "Command of type ${'$'}{T::class.java.simpleName} was issued but should not have been. " +
                    "Command: ${'$'}issuedCommand. All commands: ${'$'}{commandCaptor.allValues}",
                issuedCommand,
            )
        }
    }

    companion object {
        const val QUERY = "abc"
        const val BLANK_QUERY = ""
        const val AUTOCOMPLETE_RESULT = "autocomplete result"
        val deviceApp = DeviceApp("", "", Intent())
        val autocompleteQueryResult = AutoCompleteResult(
            QUERY,
            listOf(AutoCompleteSearchSuggestion(QUERY, isUrl = false, isAllowedInTopHits = false)),
        )
        val autocompleteBlankResult = AutoCompleteResult(BLANK_QUERY, emptyList())
        val appQueryResult = listOf(deviceApp)
        val appBlankResult = emptyList<DeviceApp>()
    }
}
