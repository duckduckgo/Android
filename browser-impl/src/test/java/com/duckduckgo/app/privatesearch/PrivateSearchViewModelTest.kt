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
import com.duckduckgo.app.FakeSettingsDataStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.privatesearch.PrivateSearchViewModel.Command
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.history.api.NavigationHistory
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
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

    private lateinit var fakeAppSettingsDataStore: FakeSettingsDataStore

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockHistory: NavigationHistory

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    val dispatcherProvider = coroutineTestRule.testDispatcherProvider

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        runTest {
            whenever(mockHistory.isHistoryUserEnabled()).thenReturn(true)

            fakeAppSettingsDataStore = FakeSettingsDataStore()

            testee = PrivateSearchViewModel(
                fakeAppSettingsDataStore,
                mockPixel,
                mockHistory,
                dispatcherProvider,
            )
        }
    }

    @After
    fun after() {
        // Clean up the state after each test if necessary
        fakeAppSettingsDataStore = FakeSettingsDataStore()
        reset(mockPixel, mockHistory)
    }

    @Test
    fun whenAutocompleteSwitchedOnThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(true)

        assertTrue(fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled)
    }

    @Test
    fun whenAutocompleteSwitchedOffThenDataStoreIsUpdated() {
        testee.onAutocompleteSettingChanged(false)

        assertFalse(fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled)
    }

    @Test
    fun whenAutocompleteSwitchedOffThenRecentlyVisitedSitesIsUpdated() = runTest {
        testee.onAutocompleteSettingChanged(false)

        verify(mockHistory).setHistoryUserEnabled(false)
    }

    @Test
    fun whenAutocompleteRecentlyVisitedSitesSwitchedOnThenHistoryUpdated() = runTest {
        testee.onAutocompleteRecentlyVisitedSitesSettingChanged(true)

        verify(mockHistory).setHistoryUserEnabled(true)
    }

    @Test
    fun whenAutocompleteRecentlyVisitedSitesSwitchedOffThenHistoryUpdated() = runTest {
        whenever(mockHistory.isHistoryUserEnabled()).thenReturn(false)
        testee.onAutocompleteRecentlyVisitedSitesSettingChanged(false)

        verify(mockHistory).setHistoryUserEnabled(false)
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
