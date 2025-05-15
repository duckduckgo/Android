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
import com.duckduckgo.app.generalsettings.GeneralSettingsViewModel.Command.OpenMaliciousLearnMore
import com.duckduckgo.app.generalsettings.showonapplaunch.ShowOnAppLaunchFeature
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.LastOpenedTab
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.NewTabPage
import com.duckduckgo.app.generalsettings.showonapplaunch.model.ShowOnAppLaunchOption.SpecificPage
import com.duckduckgo.app.generalsettings.showonapplaunch.store.FakeShowOnAppLaunchOptionDataStore
import com.duckduckgo.app.pixels.AppPixelName
import com.duckduckgo.app.pixels.AppPixelName.SETTINGS_GENERAL_APP_LAUNCH_PRESSED
import com.duckduckgo.app.pixels.remoteconfig.AndroidBrowserConfigFeature
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelType.Count
import com.duckduckgo.common.test.CoroutineTestRule
import com.duckduckgo.feature.toggles.api.FakeFeatureToggleFactory
import com.duckduckgo.feature.toggles.api.Toggle
import com.duckduckgo.history.api.NavigationHistory
import com.duckduckgo.malicioussiteprotection.api.MaliciousSiteProtection
import com.duckduckgo.voice.api.VoiceSearchAvailability
import com.duckduckgo.voice.impl.VoiceSearchPixelNames
import com.duckduckgo.voice.store.VoiceSearchRepository
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
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

    private val fakeShowOnAppLaunchFeatureToggle = FakeFeatureToggleFactory.create(ShowOnAppLaunchFeature::class.java)

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

    private val dispatcherProvider = coroutineTestRule.testDispatcherProvider

    private val fakeBrowserConfigFeature = FakeFeatureToggleFactory.create(AndroidBrowserConfigFeature::class.java)

    private val mockMaliciousSiteProtection: MaliciousSiteProtection = mock()

    @Before
    fun before() {
        MockitoAnnotations.openMocks(this)

        runBlocking {
            whenever(mockHistory.isHistoryUserEnabled()).thenReturn(true)
            whenever(mockHistory.isHistoryFeatureAvailable()).thenReturn(false)

            fakeAppSettingsDataStore = FakeSettingsDataStore()

            fakeShowOnAppLaunchOptionDataStore = FakeShowOnAppLaunchOptionDataStore()
            fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)
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
        initTestee()

        testee.onAutocompleteSettingChanged(true)

        assertTrue(fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled)
    }

    @Test
    fun whenAutocompleteSwitchedOffThenDataStoreIsUpdated() {
        initTestee()

        testee.onAutocompleteSettingChanged(false)

        assertFalse(fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled)
    }

    @Test
    fun whenAutocompleteSwitchedOffThenRecentlyVisitedSitesIsUpdated() = runTest {
        initTestee()

        testee.onAutocompleteSettingChanged(false)

        verify(mockHistory).setHistoryUserEnabled(false)
    }

    @Test
    fun whenAutocompleteRecentlyVisitedSitesSwitchedOnThenHistoryUpdated() = runTest {
        initTestee()

        testee.onAutocompleteRecentlyVisitedSitesSettingChanged(true)

        verify(mockHistory).setHistoryUserEnabled(true)
    }

    @Test
    fun whenAutocompleteRecentlyVisitedSitesSwitchedOffThenHistoryUpdated() = runTest {
        initTestee()

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

        initTestee()

        testee.onVoiceSearchChanged(true)

        testee.viewState.test {
            assertEquals(viewState.copy(voiceSearchEnabled = true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }
    }

    @Test
    fun whenVoiceSearchEnabledThenSettingsUpdated() = runTest {
        initTestee()

        testee.onVoiceSearchChanged(true)

        verify(mockVoiceSearchRepository).setVoiceSearchUserEnabled(true)
    }

    @Test
    fun whenVoiceSearchDisabledThenSettingsUpdated() = runTest {
        initTestee()

        testee.onVoiceSearchChanged(false)
        verify(mockVoiceSearchRepository).setVoiceSearchUserEnabled(false)
    }

    @Test
    fun whenVoiceSearchEnabledThenFirePixel() = runTest {
        initTestee()

        testee.onVoiceSearchChanged(true)
        verify(mockPixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_ON)
    }

    @Test
    fun whenVoiceSearchDisabledThenFirePixel() = runTest {
        initTestee()

        testee.onVoiceSearchChanged(false)
        verify(mockPixel).fire(VoiceSearchPixelNames.VOICE_SEARCH_GENERAL_SETTINGS_OFF)
    }

    @Test
    fun whenShowOnAppLaunchClickedThenLaunchShowOnAppLaunchScreenCommandEmitted() = runTest {
        initTestee()

        testee.onShowOnAppLaunchButtonClick()

        testee.commands.test {
            assertEquals(LaunchShowOnAppLaunchScreen, awaitItem())
        }
    }

    @Test
    fun whenShowOnAppLaunchSetToLastOpenedTabThenShowOnAppLaunchOptionIsLastOpenedTab() = runTest {
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        initTestee()

        testee.viewState.test {
            assertEquals(LastOpenedTab, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchSetToNewTabPageThenShowOnAppLaunchOptionIsNewTabPage() = runTest {
        initTestee()

        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(NewTabPage)

        testee.viewState.test {
            assertEquals(NewTabPage, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchSetToSpecificPageThenShowOnAppLaunchOptionIsSpecificPage() = runTest {
        val specificPage = SpecificPage("example.com")

        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(specificPage)

        initTestee()

        testee.viewState.test {
            assertEquals(specificPage, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchUpdatedThenViewStateIsUpdated() = runTest {
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        initTestee()

        testee.viewState.test {
            awaitItem()

            fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(NewTabPage)

            assertEquals(NewTabPage, awaitItem()?.showOnAppLaunchSelectedOption)
        }
    }

    @Test
    fun whenShowOnAppLaunchClickedThenPixelFiredEmitted() = runTest {
        initTestee()

        testee.onShowOnAppLaunchButtonClick()

        verify(mockPixel).fire(SETTINGS_GENERAL_APP_LAUNCH_PRESSED)
    }

    @Test
    fun whenLaunchedThenShowOnAppLaunchIsNotVisibleByDefault() = runTest {
        initTestee()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(!state!!.isShowOnAppLaunchOptionVisible)
        }
    }

    @Test
    fun whenShowOnAppLaunchFeatureIsDisabledThenIsShowOnAppLaunchOptionIsVisible() = runTest {
        fakeShowOnAppLaunchFeatureToggle.self().setRawStoredState(Toggle.State(enable = true))

        initTestee()

        testee.viewState.test {
            val state = awaitItem()

            assertTrue(state!!.isShowOnAppLaunchOptionVisible)
        }
    }

    @Test
    fun whenMaliciousSiteProtectionEnabledThenViewStateEmittedSettingOnAndPixelFired() = runTest {
        fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled = true
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        val viewState = defaultViewState()
        val paramsCaptor = argumentCaptor<Map<String, String>>()

        initTestee()

        testee.onMaliciousSiteProtectionSettingChanged(true)
        testee.viewState.test {
            assertEquals(viewState.copy(maliciousSiteProtectionEnabled = true), awaitItem())
            cancelAndConsumeRemainingEvents()
        }

        assertTrue(fakeAppSettingsDataStore.maliciousSiteProtectionEnabled)
        verify(mockPixel).fire(eq(AppPixelName.MALICIOUS_SITE_PROTECTION_SETTING_TOGGLED), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue
        assertEquals("true", params["newState"])
    }

    @Test
    fun whenMaliciousSiteProtectionDisabledThenViewStateEmittedSettingOffAndPixelFired() = runTest {
        fakeAppSettingsDataStore.autoCompleteSuggestionsEnabled = true
        fakeShowOnAppLaunchOptionDataStore.setShowOnAppLaunchOption(LastOpenedTab)

        val viewState = defaultViewState()
        val paramsCaptor = argumentCaptor<Map<String, String>>()

        initTestee()

        testee.onMaliciousSiteProtectionSettingChanged(false)
        testee.viewState.test {
            assertEquals(viewState.copy(maliciousSiteProtectionEnabled = false), awaitItem())
            cancelAndConsumeRemainingEvents()
        }

        assertFalse(fakeAppSettingsDataStore.maliciousSiteProtectionEnabled)
        verify(mockPixel).fire(eq(AppPixelName.MALICIOUS_SITE_PROTECTION_SETTING_TOGGLED), paramsCaptor.capture(), any(), eq(Count))
        val params = paramsCaptor.firstValue
        assertEquals("false", params["newState"])
    }

    @Test
    fun whenMaliciousSiteLearnMoreClickedThenOpenMaliciousLearnMoreCommandEmitted() = runTest {
        initTestee()

        testee.maliciousSiteLearnMoreClicked()

        testee.commands.test {
            assertEquals(OpenMaliciousLearnMore, awaitItem())
        }
    }

    @Test
    fun whenScamBlockerAvailableThenScamBlockerSettingIsShown() = runTest {
        fakeBrowserConfigFeature.newThreatProtectionSettings().setRawStoredState(Toggle.State(false))
        fakeBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(Toggle.State(true))
        whenever(mockMaliciousSiteProtection.isFeatureEnabled()).thenReturn(true)

        initTestee()

        testee.viewState.test {
            assertTrue(awaitItem()?.maliciousSiteProtectionFeatureAvailable!!)
        }
    }

    @Test
    fun whenScamBlockerAvailableButNewThreatProtectionSettingsThenScamBlockerSettingIsNotShown() = runTest {
        fakeBrowserConfigFeature.newThreatProtectionSettings().setRawStoredState(Toggle.State(true))
        fakeBrowserConfigFeature.enableMaliciousSiteProtection().setRawStoredState(Toggle.State(true))
        whenever(mockMaliciousSiteProtection.isFeatureEnabled()).thenReturn(true)

        initTestee()

        testee.viewState.test {
            assertFalse(awaitItem()?.maliciousSiteProtectionFeatureAvailable!!)
        }
    }

    private fun defaultViewState() = GeneralSettingsViewModel.ViewState(
        autoCompleteSuggestionsEnabled = true,
        autoCompleteRecentlyVisitedSitesSuggestionsUserEnabled = true,
        storeHistoryEnabled = false,
        showVoiceSearch = false,
        voiceSearchEnabled = false,
        isShowOnAppLaunchOptionVisible = fakeShowOnAppLaunchFeatureToggle.self().isEnabled(),
        showOnAppLaunchSelectedOption = LastOpenedTab,
        maliciousSiteProtectionEnabled = true,
        maliciousSiteProtectionFeatureAvailable = false,
    )

    private fun initTestee() {
        testee = GeneralSettingsViewModel(
            fakeAppSettingsDataStore,
            mockPixel,
            mockHistory,
            mockVoiceSearchAvailability,
            mockVoiceSearchRepository,
            dispatcherProvider,
            fakeShowOnAppLaunchFeatureToggle,
            fakeShowOnAppLaunchOptionDataStore,
            fakeBrowserConfigFeature,
            mockMaliciousSiteProtection,
        )
    }
}
