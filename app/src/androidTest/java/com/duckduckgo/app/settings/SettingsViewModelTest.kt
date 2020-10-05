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

import android.content.Context
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import androidx.test.platform.app.InstrumentationRegistry
import com.duckduckgo.app.blockingObserve
import com.duckduckgo.app.browser.BuildConfig
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.DuckDuckGoTheme
import com.duckduckgo.app.icon.api.AppIcon
import com.duckduckgo.app.settings.SettingsViewModel.Command
import com.duckduckgo.app.settings.clear.ClearWhatOption.CLEAR_NONE
import com.duckduckgo.app.settings.clear.ClearWhenOption.APP_EXIT_ONLY
import com.duckduckgo.app.settings.db.SettingsDataStore
import com.duckduckgo.app.statistics.Variant
import com.duckduckgo.app.statistics.VariantManager
import com.duckduckgo.app.statistics.pixels.Pixel
import com.nhaarman.mockitokotlin2.KArgumentCaptor
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
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

    @Mock
    private lateinit var mockPixel: Pixel

    private lateinit var commandCaptor: KArgumentCaptor<Command>

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)

        context = InstrumentationRegistry.getInstrumentation().targetContext
        commandCaptor = argumentCaptor()

        testee = SettingsViewModel(mockAppSettingsDataStore, mockDefaultBrowserDetector, mockVariantManager, mockPixel)
        testee.command.observeForever(commandObserver)

        whenever(mockAppSettingsDataStore.automaticallyClearWhenOption).thenReturn(APP_EXIT_ONLY)
        whenever(mockAppSettingsDataStore.automaticallyClearWhatOption).thenReturn(CLEAR_NONE)
        whenever(mockAppSettingsDataStore.appIcon).thenReturn(AppIcon.DEFAULT)

        whenever(mockVariantManager.getVariant()).thenReturn(VariantManager.DEFAULT_VARIANT)
    }

    @Test
    fun whenViewModelInitialisedThenPixelIsFired() {
        testee // init
        verify(mockPixel).fire(Pixel.PixelName.SETTINGS_OPENED)
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
        verify(mockAppSettingsDataStore).theme = DuckDuckGoTheme.LIGHT

        testee.command.blockingObserve()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.UpdateTheme, commandCaptor.firstValue)
    }

    @Test
    fun whenLightThemeToggledOnThenLighThemePixelIsSent() {
        testee.onLightThemeToggled(true)
        verify(mockPixel).fire(Pixel.PixelName.SETTINGS_THEME_TOGGLED_LIGHT)
    }

    @Test
    fun whenLightThemeTogglesOffThenDataStoreIsUpdatedAndUpdateThemeCommandIsSent() {
        testee.onLightThemeToggled(false)
        verify(mockAppSettingsDataStore).theme = DuckDuckGoTheme.DARK

        testee.command.blockingObserve()
        verify(commandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        assertEquals(Command.UpdateTheme, commandCaptor.firstValue)
    }

    @Test
    fun whenLightThemeToggledOffThenDarkThemePixelIsSent() {
        testee.onLightThemeToggled(false)
        verify(mockPixel).fire(Pixel.PixelName.SETTINGS_THEME_TOGGLED_DARK)
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
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.start()
        val viewState = latestViewState()
        assertTrue(viewState.isAppDefaultBrowser)
    }

    @Test
    fun whenDefaultBrowserAppNotSetToOursThenIsDefaultBrowserFlagIsFalse() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
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
    fun whenWhitelistSelectedThenPixelIsSentAndWhitelistLaunched() {
        testee.onManageWhitelistSelected()
        verify(mockPixel).fire(Pixel.PixelName.SETTINGS_MANAGE_WHITELIST)
        verify(commandObserver).onChanged(Command.LaunchWhitelist)
    }

    @Test
    fun whenVariantIsEmptyThenEmptyVariantIncludedInSettings() {
        testee.start()
        val expectedStartString = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        assertEquals(expectedStartString, latestViewState().version)
    }

    @Test
    fun whenVariantIsSetThenVariantKeyIncludedInSettings() {
        whenever(mockVariantManager.getVariant()).thenReturn(Variant("ab", filterBy = { true }))
        testee.start()
        val expectedStartString = "${BuildConfig.VERSION_NAME} ab (${BuildConfig.VERSION_CODE})"
        assertEquals(expectedStartString, latestViewState().version)
    }

    @Test
    fun whenChangeIconRequestedThenCommandIsChangeIcon() {
        testee.userRequestedToChangeIcon()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchAppIcon, commandCaptor.firstValue)
    }

    @Test
    fun whenOnGlobalPrivacyControlClickedThenCommandIsLaunchGlobalPrivacyControl() {
        testee.onGlobalPrivacyControlClicked()
        testee.command.blockingObserve()
        verify(commandObserver).onChanged(commandCaptor.capture())
        assertEquals(Command.LaunchGlobalPrivacyControl, commandCaptor.firstValue)
    }

    private fun latestViewState() = testee.viewState.value!!
}
