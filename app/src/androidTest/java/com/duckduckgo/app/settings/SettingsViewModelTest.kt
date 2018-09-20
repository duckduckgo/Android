/*
 * Copyright (c) 2017 DuckDuckGo
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

package com.duckduckgo.app.settings

import android.arch.core.executor.testing.InstantTaskExecutorRule
import android.arch.lifecycle.Observer
import android.content.Context
import android.support.test.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultBrowsing.DefaultBrowserDetector
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.nhaarman.mockito_kotlin.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class SettingsViewModelTest {

    @get:Rule
    @Suppress("unused")
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var testee: SettingsViewModel

    private lateinit var context: Context

    @Mock
    private lateinit var commandObserver: Observer<Command>

    @Mock
    private lateinit var mockAppSettingsDataStore: SettingsDataStore

    @Mock
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock
    private lateinit var mockVariantManager: VariantManager

    private lateinit var commandCaptor: KArgumentCaptor<Command>

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        context = InstrumentationRegistry.getTargetContext()
        commandCaptor = argumentCaptor()

        testee = SettingsViewModel(mockAppSettingsDataStore, mockDefaultBrowserDetector, mockVariantManager)
        testee.command.observeForever(commandObserver)

        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
    }

    @Test
    fun whenStartNotCalledYetThenViewStateInitialisedDefaultValues() {
        assertNotNull(testee.viewState)

        val value = latestViewState()
        assertTrue(value.loading)
        assertEquals("", value.version)
        assertTrue(value.autoCompleteSuggestionsEnabled)
        assertFalse(value.showDefaultBrowserSetting)
        assertFalse(value.isAppDefaultBrowser)
    }

    @Test
    fun whenStartCalledThenLoadingSetToFalse() {
        testee.start()
        val value = latestViewState()
        assertEquals(false, value.loading)
    }

    @Test
    fun whenStartCalledThenVersionSetCorrectly() {
        testee.start()
        val value = latestViewState()
        val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        assertTrue(value.version.startsWith(expectedStartString))
    }

    @Test
    fun whenLightThemeToggledOnThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() {
        testee.onLightThemeToggled(true)
        verify(mockAppSettingsDataStore).lightThemeEnabled = true

        testee.command.blockingObserve()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.UpdateTheme, commandCaptor.firstValue)
    }

    @Test
    fun whenLightThemeTogglesOffThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() {
        testee.onLightThemeToggled(false)
        verify(mockAppSettingsDataStore).lightThemeEnabled = false

        testee.command.blockingObserve()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.UpdateTheme, commandCaptor.firstValue)
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
    fun whenLeaveFeedBackRequestedThenCommandIsLaunchFeedback() {
        testee.userRequestedToSendFeedback()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchFeedback, commandCaptor.firstValue)
    }

    @Test
    fun whenDefaultBrowserAppAlreadySetToOursThenIsDefaultBrowserFlagIsTrue() {
        whenever(mockDefaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()).thenReturn(true)
        testee.start()
        val viewState = latestViewState()
        assertTrue(viewState.isAppDefaultBrowser)
    }

    @Test
    fun whenDefaultBrowserAppNotSetToOursThenIsDefaultBrowserFlagIsFalse() {
        whenever(mockDefaultBrowserDetector.isCurrentlyConfiguredAsDefaultBrowser()).thenReturn(false)
        testee.start()
        val viewState = latestViewState()
        assertFalse(viewState.isAppDefaultBrowser)
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCannotBeSetThenFlagToShowSettingIsFalse() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(false)
        testee.start()
        assertFalse(latestViewState().showDefaultBrowserSetting)
    }

    @Test
    fun whenBrowserDetectorIndicatesDefaultCanBeSetThenFlagToShowSettingIsTrue() {
        whenever(mockDefaultBrowserDetector.deviceSupportsDefaultBrowserConfiguration()).thenReturn(true)
        testee.start()
        assertTrue(latestViewState().showDefaultBrowserSetting)
    }

    @Test
    fun whenVariantIsEmptyThenEmptyVariantIncludedInSettings() {
        testee.start()
        val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        assertEquals(expectedStartString, latestViewState().version)
    }

    @Test
    fun whenVariantIsSetThenVariantKeyIncludedInSettings() {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ab"))
        testee.start()
        val expectedStartString = "${BuildConfig.VERSION_NAME} ab (${BuildConfig.VERSION_CODE})"
        assertEquals(expectedStartString, latestViewState().version)
    }


    private fun latestViewState() = testee.viewState.value!!
}