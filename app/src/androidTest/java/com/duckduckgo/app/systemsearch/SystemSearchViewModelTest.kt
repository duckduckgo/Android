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
import com.duckduckgo.app.onboarding.store.OnboardingStore
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command
import com.duckduckgo.app.systemsearch.SystemSearchViewModel.Command.LaunchDuckDuckGo
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.reactivex.Observable
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class SystemSearchViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val schedulers = InstantSchedulersRule()

    @get:Rule
    var coroutineRule = CoroutineTestRule()

    private val mockOnboardingStore: OnboardingStore = mock()
    private val mockDeviceAppLookup: DeviceAppLookup = mock()
    private val mockAutoComplete: AutoComplete = mock()

    private val commandObserver: Observer<Command> = mock()
    private val commandCaptor = argumentCaptor<Command>()

    private lateinit var testee: SystemSearchViewModel

    @Before
    fun setup() {
        whenever(mockAutoComplete.autoComplete(QUERY)).thenReturn(Observable.just(autocompleteQueryResult))
        whenever(mockAutoComplete.autoComplete(BLANK_QUERY)).thenReturn(Observable.just(autocompleteBlankResult))
        whenever(mockDeviceAppLookup.query(QUERY)).thenReturn(appQueryResult)
        whenever(mockDeviceAppLookup.query(BLANK_QUERY)).thenReturn(appBlankResult)
        testee = SystemSearchViewModel(mockOnboardingStore, mockAutoComplete, mockDeviceAppLookup, coroutineRule.testDispatcherProvider)
        testee.command.observeForever(commandObserver)
    }

    @After
    fun tearDown() {
        testee.command.removeObserver(commandObserver)
    }

    @Test
    fun whenUserUpdatesQueryThenViewStateUpdated() = ruleRunBlockingTest {
        testee.userUpdatedQuery(QUERY)

        val newViewState = testee.resutlsViewState.value
        assertNotNull(newViewState)
        assertEquals(QUERY, newViewState?.queryText)
        assertEquals(appQueryResult, newViewState?.appResults)
        assertEquals(autocompleteQueryResult, newViewState?.autocompleteResults)
    }

    @Test
    fun whenUserClearsQueryThenViewStateReset() = ruleRunBlockingTest {
        testee.userUpdatedQuery(QUERY)
        testee.userClearedQuery()

        val newViewState = testee.resutlsViewState.value
        assertNotNull(newViewState)
        assertTrue(newViewState!!.queryText.isEmpty())
        assertTrue(newViewState.appResults.isEmpty())
        assertEquals(AutoCompleteResult("", emptyList()), newViewState.autocompleteResults)
    }

    @Test
    fun whenUsersUpdatesWithBlankQueryThenViewStateReset() = ruleRunBlockingTest {
        testee.userUpdatedQuery(QUERY)
        testee.userUpdatedQuery(BLANK_QUERY)

        val newViewState = testee.resutlsViewState.value
        assertNotNull(newViewState)
        assertTrue(newViewState!!.queryText.isEmpty())
        assertTrue(newViewState.appResults.isEmpty())
        assertEquals(AutoCompleteResult("", emptyList()), newViewState.autocompleteResults)
    }

    @Test
    fun whenUserSubmitsQueryThenBrowserLaunched() {
        testee.userSubmittedQuery(QUERY)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(QUERY), commandCaptor.lastValue)
    }

    @Test
    fun whenUserSubmitsAutocompleteResultThenBrowserLaunched() {
        testee.userSubmittedAutocompleteResult(AUTOCOMPLETE_RESULT)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchBrowser(AUTOCOMPLETE_RESULT), commandCaptor.lastValue)
    }

    @Test
    fun whenUserSelectsAppResultThenAppLaunched() {
        testee.userSelectedApp(deviceApp)
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchDeviceApplication(deviceApp), commandCaptor.lastValue)
    }

    @Test
    fun whenUserTapsDaxThenAppLaunched() {
        testee.userTappedDax()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertTrue(commandCaptor.lastValue is LaunchDuckDuckGo)
    }

    @Test
    fun whenUserSelectsAppThatCannotBeFoundThenAppsRefreshedAndUserMessageShown() {
        testee.appNotFound(deviceApp)
        verify(mockDeviceAppLookup).refreshAppList()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.ShowAppNotFoundMessage(deviceApp.shortName), commandCaptor.lastValue)
    }

    private fun ruleRunBlockingTest(block: suspend TestCoroutineScope.() -> Unit) =
        coroutineRule.testDispatcher.runBlockingTest(block)

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