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

import android.content.Intent
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.CoroutineTestRule
import com.duckduckgo.app.InstantSchedulersRule
import com.duckduckgo.app.autocomplete.api.AutoComplete
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteResult
import com.duckduckgo.app.autocomplete.api.AutoComplete.AutoCompleteSuggestion.AutoCompleteSearchSuggestion
import com.duckduckgo.app.bookmarks.model.FavoritesRepository
import com.duckduckgo.app.bookmarks.model.SavedSite.Favorite
import com.duckduckgo.app.browser.favicon.FaviconManager
import com.duckduckgo.app.browser.favorites.FavoritesQuickAccessAdapter.QuickAccessFavorite
import com.duckduckgo.app.onboarding.store.*
import com.duckduckgo.app.runBlocking
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.pixels.AppPixelName.*
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.LaunchDuckDuckGo
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Suggestions.SystemSearchResultsViewState
import com.nhaarman.mockitokotlin2.*
import io.reactivex.Observable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

@Suppress("EXPERIMENTAL_API_USAGE")
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
    private val mockFavoritesRepository: FavoritesRepository = mock()
    private val mockFaviconManager: FaviconManager = mock()
    private val mockPixel: Pixel = mock()

    private val commandObserver: Observer<Command> = mock()
    private val commandCaptor = argumentCaptor<Command>()

    private lateinit var testee: SystemSearchViewModel

    @Before
    fun setup() {
        whenever(mockAutoComplete.autoComplete(QUERY)).thenReturn(Observable.just(autocompleteQueryResult))
        whenever(mockAutoComplete.autoComplete(BLANK_QUERY)).thenReturn(Observable.just(autocompleteBlankResult))
        whenever(mockDeviceAppLookup.query(QUERY)).thenReturn(appQueryResult)
        whenever(mockDeviceAppLookup.query(BLANK_QUERY)).thenReturn(appBlankResult)
        whenever(mockFavoritesRepository.favorites()).thenReturn(flowOf())
        testee = SystemSearchViewModel(mockUserStageStore, mockAutoComplete, mockDeviceAppLookup, mockPixel, mockFavoritesRepository, mockFaviconManager, coroutineRule.testDispatcherProvider)
        testee.command.observeForever(commandObserver)
    }

    @After
    fun tearDown() {
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenOnboardingShouldNotShowThenViewIsNotVisibleAndUnexpanded() = runBlockingTest {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.DAX_ONBOARDING)
        testee.resetViewState()

        val viewState = testee.onboardingViewState.value
        assertFalse(viewState!!.visible)
        assertFalse(viewState.expanded)
    }

    @Test
    fun whenOnboardingShouldShowThenViewIsVisibleAndUnexpanded() = runBlockingTest {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.resetViewState()

        val viewState = testee.onboardingViewState.value
        assertTrue(viewState!!.visible)
        assertFalse(viewState.expanded)
    }

    @Test
    fun whenDatabaseIsSlowThenIntroducingTextDoesNotCrashTheApp() = coroutineRule.runBlocking {
        (coroutineRule.testDispatcherProvider.io() as TestCoroutineDispatcher).pauseDispatcher()
        testee =
            SystemSearchViewModel(givenEmptyUserStageStore(), mockAutoComplete, mockDeviceAppLookup, mockPixel, mockFavoritesRepository, mockFaviconManager, coroutineRule.testDispatcherProvider)
        testee.resetViewState()
        testee.userUpdatedQuery("test")

        // no crash
    }

    @Test
    fun whenOnboardingShownThenPixelSent() = runBlockingTest {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.resetViewState()
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_SHOWN)
    }

    @Test
    fun whenOnboardingIsUnexpandedAndUserPressesToggleThenItIsExpandedAndPixelSent() = runBlockingTest {
        whenOnboardingShowing()
        testee.userTappedOnboardingToggle()

        val viewState = testee.onboardingViewState.value
        assertTrue(viewState!!.expanded)
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_MORE_PRESSED)
    }

    @Test
    fun whenOnboardingIsExpandedAndUserPressesToggleThenItIsUnexpandedAndPixelSent() = runBlockingTest {
        whenOnboardingShowing()
        testee.userTappedOnboardingToggle() // first press to expand
        testee.userTappedOnboardingToggle() // second press to minimize

        val viewState = testee.onboardingViewState.value
        assertFalse(viewState!!.expanded)
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_LESS_PRESSED)
    }

    @Test
    fun whenOnboardingIsDismissedThenViewHiddenPixelSentAndOnboardingStoreNotified() = runBlockingTest {
        whenOnboardingShowing()
        testee.userDismissedOnboarding()

        val viewState = testee.onboardingViewState.value
        assertFalse(viewState!!.visible)
        verify(mockPixel).fire(INTERSTITIAL_ONBOARDING_DISMISSED)
        verify(mockUserStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenUserUpdatesQueryThenViewStateUpdated() = coroutineRule.runBlocking {
        testee.userUpdatedQuery(QUERY)

        val newViewState = testee.resultsViewState.value as SystemSearchResultsViewState
        assertNotNull(newViewState)
        assertEquals(appQueryResult, newViewState.appResults)
        assertEquals(autocompleteQueryResult, newViewState.autocompleteResults)
    }

    @Test
    fun whenUserAddsSpaceToQueryThenViewStateMatchesAndSpaceTrimmedFromAutocomplete() = coroutineRule.runBlocking {
        testee.userUpdatedQuery(QUERY)
        testee.userUpdatedQuery("$QUERY ")

        val newViewState = testee.resultsViewState.value as SystemSearchResultsViewState
        assertNotNull(newViewState)
        assertEquals(appQueryResult, newViewState.appResults)
        assertEquals(autocompleteQueryResult, newViewState.autocompleteResults)
    }

    @Test
    fun whenUserClearsQueryThenViewStateReset() = coroutineRule.runBlocking {
        testee.userUpdatedQuery(QUERY)
        testee.userRequestedClear()

        assertTrue(testee.resultsViewState.value is SystemSearchViewModel.Suggestions.QuickAccessItems)
    }

    @Test
    fun whenUsersUpdatesWithBlankQueryThenViewStateReset() = coroutineRule.runBlocking {
        testee.userUpdatedQuery(QUERY)
        testee.userUpdatedQuery(BLANK_QUERY)

        assertTrue(testee.resultsViewState.value is SystemSearchViewModel.Suggestions.QuickAccessItems)
    }

    @Test
    fun whenUserSubmitsQueryThenBrowserLaunchedWithQueryAndPixelSent() {
        testee.userSubmittedQuery(QUERY)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(QUERY), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsQueryWithSpaceThenBrowserLaunchedWithTrimmedQueryAndPixelSent() {
        testee.userSubmittedQuery("$QUERY ")
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(QUERY), commandCaptor.lastValue)
        verify(mockPixel).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsBlankQueryThenIgnored() {
        testee.userSubmittedQuery(BLANK_QUERY)
        assertFalse(commandCaptor.allValues.any { it is Command.LaunchBrowser })
        verify(mockPixel, never()).fire(INTERSTITIAL_LAUNCH_BROWSER_QUERY)
    }

    @Test
    fun whenUserSubmitsQueryThenOnboardingCompleted() = coroutineRule.runBlocking {
        testee.userSubmittedQuery(QUERY)
        verify(mockUserStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenUserSubmitsAutocompleteResultThenBrowserLaunchedAndPixelSent() {
        testee.userSubmittedAutocompleteResult(AUTOCOMPLETE_RESULT)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(AUTOCOMPLETE_RESULT), commandCaptor.lastValue)
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
    fun whenUserTapsDaxThenOnboardingCompleted() = coroutineRule.runBlocking {
        testee.userTappedDax()
        verify(mockUserStageStore).stageCompleted(AppStage.NEW)
    }

    @Test
    fun whenViewModelCreatedThenAppsRefreshed() = coroutineRule.runBlocking {
        verify(mockDeviceAppLookup).refreshAppList()
    }

    @Test
    fun whenUserSelectsAppThatCannotBeFoundThenAppsRefreshedAndUserMessageShown() = coroutineRule.runBlocking {
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
        val quickAccessItem = QuickAccessFavorite(Favorite(1, "title", "http://example.com", 0))

        testee.onQuickAccesItemClicked(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(quickAccessItem.favorite.url), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessItemClickedThenPixelSent() {
        val quickAccessItem = QuickAccessFavorite(Favorite(1, "title", "http://example.com", 0))

        testee.onQuickAccesItemClicked(quickAccessItem)

        verify(mockPixel).fire(FAVORITE_SYSTEM_SEARCH_ITEM_PRESSED)
    }

    @Test
    fun whenQuickAccessItemEditRequestedThenLaunchEditDialog() {
        val quickAccessItem = QuickAccessFavorite(Favorite(1, "title", "http://example.com", 0))

        testee.onEditQuickAccessItemRequested(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchEditDialog(quickAccessItem.favorite), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessItemDeleteRequestedThenShowDeleteConfirmation() {
        val quickAccessItem = QuickAccessFavorite(Favorite(1, "title", "http://example.com", 0))

        testee.onDeleteQuickAccessItemRequested(quickAccessItem)

        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.DeleteSavedSiteConfirmation(quickAccessItem.favorite), commandCaptor.lastValue)
    }

    @Test
    fun whenQuickAccessEditedThenRepositoryUpdated() {
        val savedSite = Favorite(1, "title", "http://example.com", 0)

        testee.onSavedSiteEdited(savedSite)

        verify(mockFavoritesRepository).update(savedSite)
    }

    @Test
    fun whenQuickAccessDeleteRequestedThenRepositoryUpdated() = coroutineRule.runBlocking {
        val savedSite = Favorite(1, "title", "http://example.com", 0)

        testee.onDeleteQuickAccessItemRequested(QuickAccessFavorite(savedSite))

        verify(mockFavoritesRepository).delete(savedSite)
    }

    @Test
    fun whenQuickAccessInsertedThenRepositoryUpdated() {
        val savedSite = Favorite(1, "title", "http://example.com", 0)

        testee.insertQuickAccessItem(savedSite)

        verify(mockFavoritesRepository).insert(savedSite)
    }

    @Test
    fun whenQuickAccessListChangedThenRepositoryUpdated() {
        val savedSite = Favorite(1, "title", "http://example.com", 0)
        val savedSites = listOf(QuickAccessFavorite(savedSite))

        testee.onQuickAccessListChanged(savedSites)

        verify(mockFavoritesRepository).updateWithPosition(listOf(savedSite))
    }

    @Test
    fun whenUserHasFavoritesThenInitialStateShowsFavorites() {
        val savedSite = Favorite(1, "title", "http://example.com", 0)
        whenever(mockFavoritesRepository.favorites()).thenReturn(flowOf(listOf(savedSite)))
        testee = SystemSearchViewModel(mockUserStageStore, mockAutoComplete, mockDeviceAppLookup, mockPixel, mockFavoritesRepository, mockFaviconManager, coroutineRule.testDispatcherProvider)

        val viewState = testee.resultsViewState.value as SystemSearchViewModel.Suggestions.QuickAccessItems
        assertEquals(1, viewState.favorites.size)
        assertEquals(savedSite, viewState.favorites.first().favorite)
    }

    private suspend fun whenOnboardingShowing() {
        whenever(mockUserStageStore.getUserAppStage()).thenReturn(AppStage.NEW)
        testee.resetViewState()
    }

    private fun givenEmptyUserStageStore(): UserStageStore {
        val emptyUserStageDao = object : UserStageDao {
            override suspend fun currentUserAppStage() = UserStage(appStage = AppStage.NEW)
            override fun insert(userStage: UserStage) {}
        }
        return AppUserStageStore(emptyUserStageDao, coroutineRule.testDispatcherProvider)
    }

    companion object {
        const val QUERY = "abc"
        const val BLANK_QUERY = ""
        const val AUTOCOMPLETE_RESULT = "autocomplete result"
        val deviceApp = DeviceApp("", "", Intent())
        val autocompleteQueryResult = AutoCompleteResult(QUERY, listOf(AutoCompleteSearchSuggestion(QUERY, false)))
        val autocompleteBlankResult = AutoCompleteResult(BLANK_QUERY, emptyList())
        val appQueryResult = listOf(deviceApp)
        val appBlankResult = emptyList<DeviceApp>()
    }
}
