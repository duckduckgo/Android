/*
 * Copyright (c) 2023 DuckDuckGo
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

package com.duckduckgo.app.privatesearch

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privatesearch.PrivateSearchViewModel.Command
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.history.api.NavigationHistory
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.reset
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

internal class PrivateSearchViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: PrivateSearchViewModel

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockHistory: NavigationHistory

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        testee = PrivateSearchViewModel(
            mockAppSettingsDataStore,
            mockPixel,
            mockHistory,
        )
    }

    @After
    fun after() {
        // Clean up the state after each test if necessary
        reset(mockAppSettingsDataStore, mockPixel, mockHistory)
    }

    @Test
    fun whenAutocompleteSwitchedOnThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(true)

        verify(mockAppSettingsDataStore).autoCompleteSuggestionsEnabled = true
    }

    @Test
    fun whenAutocompleteSwitchedOffThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(false)

        verify(mockAppSettingsDataStore).autoCompleteSuggestionsEnabled = false
    }

    @Test
    fun whenAutocompleteSwitchedOffThenRecentlyVisitedSitesIsAlsoOff() {
        testee.onAutocompleteSettingChanged(false)

        TestScope().launch {
            testee.viewState().test {
                assertEquals(false, awaitItem().autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled)
            }
        }
    }

    @Test
    fun whenAutocompleteSwitchedOffAndHistoryUserEnabledThenRecentlyVisitedSitesIsOn() {
        runBlocking {
            whenever(mockHistory.isHistoryUserEnabled()).thenReturn(true)

            testee.onAutocompleteSettingChanged(true)

            TestScope().launch {
                testee.viewState().test {
                    assertEquals(true, awaitItem().autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled)
                }
            }
        }
    }

    @Test
    fun whenAutocompleteSwitchedOffAndHistoryUserEnabledThenRecentlyVisitedSitesIsOff() {
        runBlocking {
            whenever(mockHistory.isHistoryUserEnabled()).thenReturn(true)

            testee.onAutocompleteSettingChanged(false)

            TestScope().launch {
                testee.viewState().test {
                    assertEquals(false, awaitItem().autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled)
                }
            }
        }
    }

    @Test
    fun whenAutocompleteRecentlyVisitedSitesSwitchedOnThenHistoryUpdated() {
        runBlocking {
            testee.onAutocompleteRecentlyVisitedSitesSettingChanged(true)

            verify(mockHistory).setHistoryUserEnabled(true)
        }
    }

    @Test
    fun whenAutocompleteRecentlyVisitedSitesSwitchedOffThenHistoryUpdated() {
        runBlocking {
            whenever(mockHistory.isHistoryUserEnabled()).thenReturn(false)
            testee.onAutocompleteRecentlyVisitedSitesSettingChanged(false)

            verify(mockHistory).setHistoryUserEnabled(false)
        }
    }

    @Test
    fun whenMoreSearchSettingsClickedThenCommandLaunchCustomizeSearchWebPageAndPixelIsSent() = runTest {
        testee.commands().test {
            testee.onPrivateSearchMoreSearchSettingsClicked()

            assertEquals(Command.LaunchCustomizeSearchWebPage, awaitItem())
            verify(mockPixel).fire(AppPixelName.SETTINGS_PRIVATE_SEARCH_MORE_SEARCH_SETTINGS_PRESSED)

            cancelAndConsumeRemainingEvents()
        }
    }
}
