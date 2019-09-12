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
    fun whenInitializeThenSendPixel() {
        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_VISUALIZED)
    }

    @Test
    fun whenInitializingIfThereIsADefaultBrowserThenShowSettingsUI() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        testee = DefaultBrowserPageExperimentViewModel(mockDefaultBrowserDetector, mockPixel, mockInstallStore)

        assertTrue(viewState().showSettingsUi)
    }

    @Test
    fun whenInitializingIfThereIsNotADefaultBrowserThenShowDialogUI() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)

        assertFalse(viewState().showSettingsUi)
    }

    @Test
    fun whenLoadUiThenShowSettingsUiIfDefaultBrowserIsTrue() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        testee.loadUI()

        assertTrue(viewState().showSettingsUi)
    }

    @Test
    fun whenLoadUiThenShowDialogUiIfDefaultBrowserIsFalse() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)

        testee.loadUI()

        assertFalse(viewState().showSettingsUi)
    }


    @Test
    fun whenContinueButtonClickedWithoutTryingToSetDDGAsDefaultThenSendPixelAndExecuteContinueToBrowserCommand() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        testee.onContinueToBrowser(false)

        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)

        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
    }

    @Test
    fun whenContinueButtonClickedWithoutTryingToSetDDGAsDefaultButDDGWasDefaultThenDoNotSendPixelAndExecuteContinueToBrowserCommand() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.onContinueToBrowser(false)

        verify(mockPixel, never()).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)

        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
    }

    @Test
    fun whenContinueButtonClickedAfterTryingToSetDDGAsDefaultThenDoNotSendPixelAndExecuteContinueToBrowserCommand() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        testee.onContinueToBrowser(true)

        verify(mockPixel, never()).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SKIPPED)

        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
    }

    @Test
    fun whenDefaultButtonClickedWithDefaultBrowserThenExecuteOpenSettingsCommandAndFireDefaultBrowserLaunchedPixel() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED to DEFAULT_BROWSER_SETTINGS
        )

        testee.onDefaultBrowserClicked()

        assertTrue(captureCommands().lastValue is Command.OpenSettings)
        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_LAUNCHED, params)
    }

    @Test
    fun whenDefaultButtonClickedWithoutDefaultBrowserThenExecuteOpenDialogCommandAndFireDefaultBrowserLaunchedPixel() {
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_BEHAVIOUR_TRIGGERED to DEFAULT_BROWSER_DIALOG
        )

        testee.onDefaultBrowserClicked()

        assertTrue(captureCommands().lastValue is Command.OpenDialog)
        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_LAUNCHED, params)
    }

    @Test
    fun whenUserSetDDGAsDefaultFromDialogThenShowContinueButtonWithNoInstructionsAndFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to DEFAULT_BROWSER_DIALOG
        )

        testee.handleResult(Origin.InternalBrowser)

        assertTrue(viewState().showOnlyContinue)
        assertFalse(viewState().showInstructionsCard)
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_SET, params)
    }

    @Test
    fun whenUserSetDDGAsJustOnceForFirstTimeThenShowInstructionsAgainOpenDialogAndFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)

        testee.handleResult(Origin.InternalBrowser)

        assertFalse(viewState().showOnlyContinue)
        assertTrue(viewState().showInstructionsCard)
        assertTrue(captureCommands().lastValue is Command.OpenDialog)
        assertEquals(1, testee.timesPressedJustOnce)
        verify(mockPixel).fire(Pixel.PixelName.ONBOARDING_DEFAULT_BROWSER_SELECTED_JUST_ONCE)
    }

    @Test
    fun whenUserSetDDGAsJustOnceTheMaxAllowedTimesThenTakeUserToBrowser() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to Pixel.PixelValues.DEFAULT_BROWSER_JUST_ONCE_MAX
        )

        testee.timesPressedJustOnce = MAX_DIALOG_ATTEMPTS
        testee.handleResult(Origin.InternalBrowser)

        assertFalse(viewState().showInstructionsCard)
        assertTrue(captureCommands().lastValue is Command.ContinueToBrowser)
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_NOT_SET, params)
    }

    @Test
    fun whenUserDismissedDialogThenShowDialogUIAndFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(false)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to Pixel.PixelValues.DEFAULT_BROWSER_DIALOG_DISMISSED
        )

        testee.handleResult(Origin.DialogDismissed)

        assertFalse(viewState().showSettingsUi)
        assertFalse(viewState().showInstructionsCard)
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_NOT_SET, params)
    }

    @Test
    fun whenUserSetAnotherBrowserAsDefaultThenShowSettingsUI() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to Pixel.PixelValues.DEFAULT_BROWSER_EXTERNAL
        )

        testee.handleResult(Origin.ExternalBrowser)

        assertTrue(viewState().showSettingsUi)
        assertFalse(viewState().showInstructionsCard)
        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_NOT_SET, params)
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
        whenever(mockDefaultBrowserDetector.hasDefaultBrowser()).thenReturn(true)

        testee.handleResult(Origin.Settings)

        assertTrue(viewState().showSettingsUi)
        assertFalse(viewState().showInstructionsCard)
    }

    @Test
    fun whenUserSelectedDDGAsDefaultInSettingsScreenThenFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_FROM_ONBOARDING to true.toString(),
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to DEFAULT_BROWSER_SETTINGS
        )

        testee.handleResult(Origin.Settings)

        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_SET, params)
    }

    @Test
    fun whenUserDoesNotSelectedDDGAsDefaultInSettingsThenFirePixel() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        val params = mapOf(
            Pixel.PixelParameter.DEFAULT_BROWSER_SET_ORIGIN to DEFAULT_BROWSER_SETTINGS
        )

        testee.handleResult(Origin.Settings)

        verify(mockPixel).fire(Pixel.PixelName.DEFAULT_BROWSER_NOT_SET, params)
    }

    @Test
    fun whenOriginReceivedIsSettingsThenResetTimesPressedJustOnce() {
        testee.timesPressedJustOnce = 1

        testee.handleResult(Origin.Settings)

        assertEquals(0, testee.timesPressedJustOnce)
    }

    @Test
    fun whenOriginReceivedIsDialogDismissedThenResetTimesPressedJustOnce() {
        testee.timesPressedJustOnce = 1

        testee.handleResult(Origin.DialogDismissed)

        assertEquals(0, testee.timesPressedJustOnce)
    }

    @Test
    fun whenOriginReceivedIsExternalBrowserThenResetTimesPressedJustOnce() {
        testee.timesPressedJustOnce = 1

        testee.handleResult(Origin.ExternalBrowser)

        assertEquals(0, testee.timesPressedJustOnce)
    }

    @Test
    fun whenOriginReceivedIsInternalBrowserAndDDGIsTheDefaultBrowserThenResetTimesPressedJustOnce() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(true)
        testee.timesPressedJustOnce = 1

        testee.handleResult(Origin.InternalBrowser)

        assertEquals(0, testee.timesPressedJustOnce)
    }

    @Test
    fun whenOriginReceivedIsInternalBrowserAndDDGIsNotTheDefaultBrowserThenIncreaseTimesPressedJustOnceIfIsLessThanTheMaxNumberOfAttempts() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        testee.timesPressedJustOnce = 1

        testee.handleResult(Origin.InternalBrowser)

        assertEquals(2, testee.timesPressedJustOnce)
    }

    @Test
    fun whenOriginReceivedIsInternalBrowserAndDDGIsNotTheDefaultBrowserThenResetTimesPressedJustOnceIfIsGreaterOrEqualThanTheMaxNumberOfAttempts() {
        whenever(mockDefaultBrowserDetector.isDefaultBrowser()).thenReturn(false)
        testee.timesPressedJustOnce = MAX_DIALOG_ATTEMPTS

        testee.handleResult(Origin.InternalBrowser)

        assertEquals(0, testee.timesPressedJustOnce)
    }

    private fun captureCommands(): ArgumentCaptor<Command> {
        verify(mockCommandObserver, atLeastOnce()).onChanged(commandCaptor.capture())
        return commandCaptor
    }

    private fun viewState() = testee.viewState.value!!
}