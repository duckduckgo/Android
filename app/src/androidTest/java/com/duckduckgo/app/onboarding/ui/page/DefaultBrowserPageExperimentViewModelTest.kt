/*
 * Copyright (c) 2019 DuckDuckGo
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

package com.duckduckgo.app.onboarding.ui.page

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.duckduckgo.app.browser.defaultbrowsing.DefaultBrowserDetector
import com.duckduckgo.app.global.install.AppInstallStore
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPageExperimentViewModel.*
import com.duckduckgo.app.onboarding.ui.page.DefaultBrowserPageExperimentViewModel.Companion.MAX_DIALOG_ATTEMPTS
import com.duckduckgo.app.statistics.pixels.Pixel
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DEFAULT_BROWSER_DIALOG
import com.duckduckgo.app.statistics.pixels.Pixel.PixelValues.DEFAULT_BROWSER_SETTINGS
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.atLeastOnce
import com.nhaarman.mockitokotlin2.lastValue
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Captor
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class DefaultBrowserPageExperimentViewModelTest {

    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    @Mock
    private lateinit var mockDefaultBrowserDetector: DefaultBrowserDetector

    @Mock
    private lateinit var mockPixel: Pixel

    @Mock
    private lateinit var mockInstallStore: AppInstallStore

    private lateinit var testee: DefaultBrowserPageExperimentViewModel

    @Captor
    private lateinit var commandCaptor: ArgumentCaptor<Command>

    @Mock
    private lateinit var mockCommandObserver: Observer<Command>

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
        testee = DefaultBrowserPageExperimentViewModel(mockDefaultBrowserDetector, mockPixel, mockInstallStore)
        testee.command.observeForever(mockCommandObserver)
    }

    @After
    fun after() {
        testee.command.removeObserver(mockCommandObserver)
    }

    @Test
    fun whenInitializingIfThereIsADefaultBrowserThenShowSettingsUI() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        testee = DefaultBrowserPageExperimentViewModel(mockDefaultBrowserDetector, mockPixel, mockInstallStore)

        assertTrue(viewState().showSettingsUI)
    }

    @Test
    fun whenInitializingIfThereIsNotADefaultBrowserThenShowDialogUI() {
        assertFalse(viewState().showSettingsUI)
    }

    @Test
    fun whenReloadUiThenShowSettingsDependingOnDefaultBrowserValue() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true).thenReturn(false)

        testee.reloadUI()
        assertTrue(viewState().showSettingsUI)

        testee.reloadUI()
        assertFalse(viewState().showSettingsUI)
    }

    @Test
    fun whenContinueButtonClickedWithoutTryingToSetDDGAsDefaultThenSendPixelAndExecuteContinueToBrowserCommand() {
        testee.onContinueToBrowser(false)

        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)

        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
    }

    @Test
    fun whenContinueButtonClickedAfterTryingToSetDDGAsDefaultThenDoNotSendPixelAndExecuteContinueToBrowserCommand() {
        testee.onContinueToBrowser(true)

        verify(mockPixel, never()).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)

        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
    }

    @Test
    fun whenSetDDGAsDefaultButtonClickedWithDefaultBrowserThenExecuteOpenSettingsCommandAndFireDefaultBrowserLaunchedPixel() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED to DEFAULT_BROWSER_SETTINGS
        )

        testee.onDefaultBrowserClicked()

        assertTrue(captureCommands().lastValue is Command.OpenSettings)
        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_LAUNCHED, params)
    }

    @Test
    fun whenSetDDGAsDefaultButtonClickedWithoutDefaultBrowserThenExecuteOpenDialogCommandAndFireDefaultBrowserLaunchedPixel() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED to DEFAULT_BROWSER_DIALOG
        )

        testee.onDefaultBrowserClicked()

        assertTrue(captureCommands().lastValue is Command.OpenDialog)
        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_LAUNCHED, params)
    }

    @Test
    fun whenUserSetDDGAsDefaultThenShowContinueButtonWithNoInstructionsAndFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()
        )

        testee.handleResult(Origin.InternalBrowser())

        assertTrue(viewState().showOnlyContinue)
        assertFalse(viewState().showInstructionsCard)
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_SET, params)
    }

    @Test
    fun whenUserSetDDGAsJustOnceForFirstTimeThenShowInstructionsAgainAndOpenDialog() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.handleResult(Origin.InternalBrowser(1))

        assertFalse(viewState().showOnlyContinue)
        assertTrue(viewState().showInstructionsCard)
        assertTrue(captureCommands().lastValue is Command.OpenDialog)
        assertEquals(1, (captureCommands().lastValue as Command.OpenDialog).timesOpened)
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any())
    }

    @Test
    fun whenUserSetDDGAsJustOnceTheMaxAllowedTimesThenTakeUserToBrowser() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.handleResult(Origin.InternalBrowser(MAX_DIALOG_ATTEMPTS))

        assertFalse(viewState().showInstructionsCard)
        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any())
    }

    @Test
    fun whenUserSetAnotherBrowserAsDefaultThenShowSettingsUI() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        testee.handleResult(Origin.ExternalBrowser)

        assertTrue(viewState().showSettingsUI)
        assertFalse(viewState().showInstructionsCard)
        verify(mockPixel, never()).fire(any<Pixel.PixelName>(), any())
    }

    @Test
    fun whenUserWasTakenToSettingsAndSelectedDDGAsDefaultThenShowContinueToBrowserUI() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)

        testee.handleResult(Origin.Settings)

        assertTrue(viewState().showOnlyContinue)
        assertFalse(viewState().showInstructionsCard)
    }

    @Test
    fun whenUserWasTakenToSettingsAndDidNotSelectDDGAsDefaultThenShowSettingsUI() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)

        testee.handleResult(Origin.Settings)

        assertTrue(viewState().showSettingsUI)
        assertFalse(viewState().showInstructionsCard)
    }

    @Test
    fun whenUserSelectedDDGAsDefaultThenFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString()
        )

        testee.handleResult(Origin.Settings)

        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_SET, params)
    }

    private fun captureCommands(): ArgumentCaptor<Command> {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }

    private fun viewState() = testee.viewState.value!!
}