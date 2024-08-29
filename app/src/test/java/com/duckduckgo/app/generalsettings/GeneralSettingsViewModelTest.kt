/*
 * Copyright (c) 2024 DuckDuckGo
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

package com.duckduckgo.app.generalsettings

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import app.cash.turbine.test
import com.duckduckgo.app.FakeSettingsDataStore
import com.duckduckgo.app.generalsettings.GeneralSettingsViewModel.Command.LaunchShowOnAppLaunchScreen
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.VoiceSearchPixelNames
import com.duckduckgo.voice.store.VoiceSearchRepository
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

internal class GeneralSettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: GeneralSettingsViewModel

    private lateinit var fakeAppSettingsDataStore: FakeSettingsDataStore

    private lateinit var fakeShowOnAppLaunchOptionDataStore: FakeShowOnAppLaunchOptionDataStore

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockHistory: NavigationHistory

    @Mock
    private lateinit var mockVoiceSearchAvailability: VoiceSearchAvailability

    @Mock
    private lateinit var mockVoiceSearchRepository: VoiceSearchRepository

    @get:Rule
    val coroutineTestRule: CoroutineTestRule = CoroutineTestRule()

    val dispatcherProvider = coroutineTestRule.testDispatcherProvider

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        runTest {
            whenever(mockHistory.isHistoryUserEnabled()).thenReturn(true)

            fakeAppSettingsDataStore = FakeSettingsDataStore()

            fakeShowOnAppLaunchOptionDataStore = FakeShowOnAppLaunchOptionDataStore()

            testee = GeneralSettingsViewModel(
                fakeAppSettingsDataStore,
                mockPixel,
                mockHistory,
                mockVoiceSearchAvailability,
                mockVoiceSearchRepository,
                dispatcherProvider,
                fakeShowOnAppLaunchOptionDataStore,
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
    fun whenVoiceSearchEnabledThenViewStateEmitted() = runTest {
        fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled = true
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)
        whenever(mockVoiceSearchAvailability.isVoiceSearchAvailable).thenReturn(true)

        val viewState = defaultViewState()

        testee.onVoiceSearchChanged(true)

        testee.viewState.test {
            assertEquals(viewState.copy(voiceSearchEnabled = true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVoiceSearchEnabledThenSettingsUpdated() = runTest {
        testee.onVoiceSearchChanged(true)
        verify(mockVoiceSearchRepository).setVoiceSearchUserEnabled(true)
    }

    @Test
    fun whenVoiceSearchDisabledThenSettingsUpdated() = runTest {
        testee.onVoiceSearchChanged(false)
        verify(mockVoiceSearchRepository).setVoiceSearchUserEnabled(false)
    }

    @Test
    fun whenVoiceSearchEnabledThenFirePixel() = runTest {
        testee.onVoiceSearchChanged(true)
        verify(mockPixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_ON)
    }

    @Test
    fun whenVoiceSearchDisabledThenFirePixel() = runTest {
        testee.onVoiceSearchChanged(false)
        verify(mockPixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_OFF)
    }

    @Test
    fun whenShowOnAppLaunchClickedThenLaunchShowOnAppLaunchScreenCommandEmitted() = runTest {
        testee.onShowOnAppLaunchButtonClick()

        testee.commands.test {
            assertEquals(LaunchShowOnAppLaunchScreen, awaitItem())
        }
    }

    @Test
    fun whenShowOnAppLaunchSetToLastOpenedTabThenShowOnAppLaunchOptionIsLastOpenedTab() = runTest {
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        testee.viewState.test {
            assertEquals(LastOpenedTab, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchSetToNewTabPageThenShowOnAppLaunchOptionIsNewTabPage() = runTest {
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(NewTabPage)

        testee.viewState.test {
            assertEquals(NewTabPage, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchSetToSpecificPageThenShowOnAppLaunchOptionIsSpecificPage() = runTest {
        val specificPage = SpecificPage("example.com")

        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(specificPage)

        testee.viewState.test {
            assertEquals(specificPage, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchUpdatedThenViewStateIsUpdated() = runTest {
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        testee.viewState.test {
            awaitItem()

            fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(NewTabPage)

            assertEquals(NewTabPage, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    private fun defaultViewState() = GeneralSettingsViewModel.ViewState(
        autoCompleteSuggestionsEnabled = true,
        autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = true,
        storeHistoryEnabled = false,
        showVoiceSearch = false,
        voiceSearchEnabled = false,
        showOnAppLaunchSelectedOption = LastOpenedTab,
    )
}
